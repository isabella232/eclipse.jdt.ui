/*******************************************************************************
 * Copyright (c) 2000, 2013 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.preferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.ibm.icu.text.Collator;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.core.expressions.Expression;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.commands.ActionHandler;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.StatusDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.BidiUtils;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.text.templates.TemplateException;

import org.eclipse.ui.ActiveShellExpression;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerActivation;
import org.eclipse.ui.handlers.IHandlerService;

import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.IUpdate;

import org.eclipse.jdt.ui.IContextMenuConstants;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IJavaPartitions;
import org.eclipse.jdt.ui.text.JavaTextTools;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.javaeditor.JavaSourceViewer;
import org.eclipse.jdt.internal.ui.text.template.preferences.TemplateVariableProcessor;
import org.eclipse.jdt.internal.ui.util.SWTUtil;


/**
 * Dialog to edit a template.
 * <p>
 * <strong>Note:</strong> This is a copy of org.eclipse.ui.texteditor.templates.TemplatePreferencePage.EditTemplateDialog
 * which we should try to eliminate (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=208865).
 * </p>
 */
public class EditTemplateDialog extends StatusDialog {

	private static class TextViewerAction extends Action implements IUpdate {

		private int fOperationCode= -1;
		private ITextOperationTarget fOperationTarget;

		/**
		 * Creates a new action.
		 *
		 * @param viewer the viewer
		 * @param operationCode the opcode
		 */
		public TextViewerAction(ITextViewer viewer, int operationCode) {
			fOperationCode= operationCode;
			fOperationTarget= viewer.getTextOperationTarget();
			update();
		}

		/**
		 * Updates the enabled state of the action.
		 * Fires a property change if the enabled state changes.
		 *
		 * @see Action#firePropertyChange(String, Object, Object)
		 */
		@Override
		public void update() {
			// XXX: workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=206111
			if (fOperationCode == ITextOperationTarget.UNDO || fOperationCode == ITextOperationTarget.REDO)
				return;

			boolean wasEnabled= isEnabled();
			boolean isEnabled= (fOperationTarget != null && fOperationTarget.canDoOperation(fOperationCode));
			setEnabled(isEnabled);

			if (wasEnabled != isEnabled) {
				firePropertyChange(ENABLED, wasEnabled ? Boolean.TRUE : Boolean.FALSE, isEnabled ? Boolean.TRUE : Boolean.FALSE);
			}
		}

		/**
		 * @see Action#run()
		 */
		@Override
		public void run() {
			if (fOperationCode != -1 && fOperationTarget != null) {
				fOperationTarget.doOperation(fOperationCode);
			}
		}
	}

	private Template fTemplate;

	private Text fNameText;
	private Text fDescriptionText;
	private Combo fContextCombo;
	private SourceViewer fPatternEditor;
	private Button fInsertVariableButton;
	private Button fAutoInsertCheckbox;
	private boolean fIsNameModifiable;

	private StatusInfo fValidationStatus;
	private boolean fSuppressError= true; // https://bugs.eclipse.org/bugs/show_bug.cgi?id=4354
	private Map<String, TextViewerAction> fGlobalActions= new HashMap<>(10);
	private List<String> fSelectionActions = new ArrayList<>(3);
	private String[][] fContextTypes;

	private ContextTypeRegistry fContextTypeRegistry;

	private final TemplateVariableProcessor fTemplateProcessor= new TemplateVariableProcessor();

	/**
	 * Creates a new dialog.
	 *
	 * @param parent the shell parent of the dialog
	 * @param template the template to edit
	 * @param edit whether this is a new template or an existing being edited
	 * @param isNameModifiable whether the name of the template may be modified
	 * @param registry the context type registry to use
	 */
	public EditTemplateDialog(Shell parent, Template template, boolean edit, boolean isNameModifiable, ContextTypeRegistry registry) {
		super(parent);

		String title= edit
			? PreferencesMessages.EditTemplateDialog_title_edit
			: PreferencesMessages.EditTemplateDialog_title_new;
		setTitle(title);

		fTemplate= template;
		fIsNameModifiable= isNameModifiable;

		String delim= new Document().getLegalLineDelimiters()[0];

		List<String[]> contexts= new ArrayList<>();
		for (Iterator<TemplateContextType> it= registry.contextTypes(); it.hasNext();) {
			TemplateContextType type= it.next();
			if ("javadoc".equals(type.getId())) //$NON-NLS-1$
				contexts.add(new String[] { type.getId(), type.getName(), "/**" + delim }); //$NON-NLS-1$
			else
				contexts.add(0, new String[] { type.getId(), type.getName(), "" }); //$NON-NLS-1$
		}
		Collections.sort(contexts, new Comparator<String[]>() {
			Collator fCollator= Collator.getInstance();
			@Override
			public int compare(String[] o1, String[] o2) {
				return fCollator.compare(o1[1], o2[1]);
			}
		});
		fContextTypes= contexts.toArray(new String[contexts.size()][]);

		fValidationStatus= new StatusInfo();

		fContextTypeRegistry= registry;

		TemplateContextType type= fContextTypeRegistry.getContextType(template.getContextTypeId());
		fTemplateProcessor.setContextType(type);
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#isResizable()
	 * @since 3.4
	 */
	@Override
	protected boolean isResizable() {
		return true;
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.dialogs.StatusDialog#create()
	 */
	@Override
	public void create() {
		super.create();
		updateStatusAndButtons();
		getButton(IDialogConstants.OK_ID).setEnabled(getStatus().isOK());
	}

	/*
	 * @see Dialog#createDialogArea(Composite)
	 */
	@Override
	protected Control createDialogArea(Composite ancestor) {
		Composite parent= new Composite(ancestor, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.numColumns= 2;
		layout.marginHeight= convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth= convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing= convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing= convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);

		parent.setLayout(layout);
		parent.setLayoutData(new GridData(GridData.FILL_BOTH));

		ModifyListener listener= e -> doTextWidgetChanged(e.widget);

		if (fIsNameModifiable) {
			createLabel(parent, PreferencesMessages.EditTemplateDialog_name);

			Composite composite= new Composite(parent, SWT.NONE);
			composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			layout= new GridLayout();
			layout.numColumns= 4;
			layout.marginWidth= 0;
			layout.marginHeight= 0;
			composite.setLayout(layout);

			fNameText= createText(composite);
			fNameText.addFocusListener(new FocusListener() {

				@Override
				public void focusGained(FocusEvent e) {
				}

				@Override
				public void focusLost(FocusEvent e) {
					if (fSuppressError) {
						fSuppressError= false;
						updateStatusAndButtons();
					}
				}
			});
			BidiUtils.applyBidiProcessing(fNameText, BidiUtils.BTD_DEFAULT);

			createLabel(composite, PreferencesMessages.EditTemplateDialog_context);
			fContextCombo= new Combo(composite, SWT.READ_ONLY);
			SWTUtil.setDefaultVisibleItemCount(fContextCombo);

			for (String[] fContextType : fContextTypes) {
				fContextCombo.add(fContextType[1]);
			}

			fContextCombo.addModifyListener(listener);

			fAutoInsertCheckbox= createCheckbox(composite, PreferencesMessages.EditTemplateDialog_autoinsert);
			fAutoInsertCheckbox.setSelection(fTemplate.isAutoInsertable());
		}

		createLabel(parent, PreferencesMessages.EditTemplateDialog_description);

		int descFlags= fIsNameModifiable ? SWT.BORDER : SWT.BORDER | SWT.READ_ONLY;
		fDescriptionText= new Text(parent, descFlags );
		fDescriptionText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		fDescriptionText.addModifyListener(listener);
		BidiUtils.applyBidiProcessing(fDescriptionText, BidiUtils.BTD_DEFAULT);

		Label patternLabel= createLabel(parent, PreferencesMessages.EditTemplateDialog_pattern);
		patternLabel.setLayoutData(new GridData(GridData.VERTICAL_ALIGN_BEGINNING));
		fPatternEditor= createEditor(parent);

		Label filler= new Label(parent, SWT.NONE);
		filler.setLayoutData(new GridData());

		Composite composite= new Composite(parent, SWT.NONE);
		layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData());

		fInsertVariableButton= new Button(composite, SWT.NONE);
		fInsertVariableButton.setLayoutData(getButtonGridData());
		fInsertVariableButton.setText(PreferencesMessages.EditTemplateDialog_insert_variable);
		fInsertVariableButton.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				fPatternEditor.getTextWidget().setFocus();
				fPatternEditor.doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {}
		});

		fDescriptionText.setText(fTemplate.getDescription());
		if (fIsNameModifiable) {
			fNameText.setText(fTemplate.getName());
			fNameText.addModifyListener(listener);
			fContextCombo.select(getIndex(fTemplate.getContextTypeId()));
		} else {
			fPatternEditor.getControl().setFocus();
		}
		initializeActions();

		applyDialogFont(parent);
		return composite;
	}

	protected void doTextWidgetChanged(Widget w) {
		if (w == fNameText) {
			fSuppressError= false;
			updateStatusAndButtons();
		} else if (w == fContextCombo) {
			String contextId= getContextId();
			fTemplateProcessor.setContextType(fContextTypeRegistry.getContextType(contextId));
			IDocument document= fPatternEditor.getDocument();
			String prefix= getPrefix();
			document.set(prefix + getPattern());
			fPatternEditor.setVisibleRegion(prefix.length(), document.getLength() - prefix.length());
			updateStatusAndButtons();
		} else if (w == fDescriptionText) {
			// nothing
		}
	}

	private String getContextId() {
		if (fContextCombo != null && !fContextCombo.isDisposed()) {
			String name= fContextCombo.getText();
			for (String[] fContextType : fContextTypes) {
				if (name.equals(fContextType[1])) {
					return fContextType[0];
				}
			}
		}

		return fTemplate.getContextTypeId();
	}

	protected void doSourceChanged(IDocument document) {
		String text= document.get();
		fValidationStatus.setOK();
		TemplateContextType contextType= fContextTypeRegistry.getContextType(getContextId());
		if (contextType != null) {
			try {
				contextType.validate(text);
			} catch (TemplateException e) {
				fValidationStatus.setError(e.getLocalizedMessage());
			}
		}

		updateAction(ITextEditorActionConstants.UNDO);
		updateStatusAndButtons();
	}

	private static GridData getButtonGridData() {
		GridData data= new GridData(GridData.FILL_HORIZONTAL);
		return data;
	}

	private static Label createLabel(Composite parent, String name) {
		Label label= new Label(parent, SWT.NULL);
		label.setText(name);
		label.setLayoutData(new GridData());

		return label;
	}

	private static Button createCheckbox(Composite parent, String name) {
		Button button= new Button(parent, SWT.CHECK);
		button.setText(name);
		button.setLayoutData(new GridData());

		return button;
	}

	private static Text createText(Composite parent) {
		Text text= new Text(parent, SWT.BORDER);
		text.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		return text;
	}

	private SourceViewer createEditor(Composite parent) {
		String prefix= getPrefix();
		IDocument document= new Document(prefix + fTemplate.getPattern());
		JavaTextTools tools= JavaPlugin.getDefault().getJavaTextTools();
		tools.setupJavaDocumentPartitioner(document, IJavaPartitions.JAVA_PARTITIONING);
		IPreferenceStore store= JavaPlugin.getDefault().getCombinedPreferenceStore();
		SourceViewer viewer= new JavaSourceViewer(parent, null, null, false, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL, store);
		CodeTemplateSourceViewerConfiguration configuration= new CodeTemplateSourceViewerConfiguration(tools.getColorManager(), store, null, fTemplateProcessor);
		viewer.configure(configuration);
		viewer.setEditable(true);
		viewer.setDocument(document, prefix.length(), document.getLength() - prefix.length());

		Font font= JFaceResources.getFont(PreferenceConstants.EDITOR_TEXT_FONT);
		viewer.getTextWidget().setFont(font);
		new JavaSourcePreviewerUpdater(viewer, configuration, store);

		int nLines= document.getNumberOfLines();
		if (nLines < 5) {
			nLines= 5;
		} else if (nLines > 12) {
			nLines= 12;
		}

		Control control= viewer.getControl();
		GridData data= new GridData(GridData.FILL_BOTH);
		data.widthHint= convertWidthInCharsToPixels(80);
		data.heightHint= convertHeightInCharsToPixels(nLines);
		control.setLayoutData(data);

		viewer.addTextListener(event -> {
			if (event .getDocumentEvent() != null)
				doSourceChanged(event.getDocumentEvent().getDocument());
		});

		viewer.addSelectionChangedListener(event -> updateSelectionDependentActions());

		return viewer;
	}

	private String getPrefix() {
		String id= getContextId();
		int idx= getIndex(id);
		if (idx != -1)
			return fContextTypes[idx][2];
		else
			return ""; //$NON-NLS-1$
	}

	private void initializeActions() {
		final ArrayList<IHandlerActivation> handlerActivations= new ArrayList<>(3);
		final IHandlerService handlerService= PlatformUI.getWorkbench().getAdapter(IHandlerService.class);
		final Expression expression= new ActiveShellExpression(fPatternEditor.getControl().getShell());

		getShell().addDisposeListener(e -> handlerService.deactivateHandlers(handlerActivations));

		fPatternEditor.getTextWidget().addFocusListener(new FocusListener() {
			@Override
			public void focusLost(FocusEvent e) {
				handlerService.deactivateHandlers(handlerActivations);
			}
			@Override
			public void focusGained(FocusEvent e) {
				IAction action= fGlobalActions.get(ITextEditorActionConstants.REDO);
				handlerActivations.add(handlerService.activateHandler(IWorkbenchCommandConstants.EDIT_REDO, new ActionHandler(action), expression));
				action= fGlobalActions.get(ITextEditorActionConstants.UNDO);
				handlerActivations.add(handlerService.activateHandler(IWorkbenchCommandConstants.EDIT_UNDO, new ActionHandler(action), expression));
				action= fGlobalActions.get(ITextEditorActionConstants.CONTENT_ASSIST);
				handlerActivations.add(handlerService.activateHandler(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS, new ActionHandler(action), expression));
			}
		});


		TextViewerAction action= new TextViewerAction(fPatternEditor, ITextOperationTarget.UNDO);
		action.setText(PreferencesMessages.EditTemplateDialog_undo);
		fGlobalActions.put(ITextEditorActionConstants.UNDO, action);

		action= new TextViewerAction(fPatternEditor, ITextOperationTarget.REDO);
		action.setText(PreferencesMessages.EditTemplateDialog_redo);
		fGlobalActions.put(ITextEditorActionConstants.REDO, action);

		action= new TextViewerAction(fPatternEditor, ITextOperationTarget.CUT);
		action.setText(PreferencesMessages.EditTemplateDialog_cut);
		fGlobalActions.put(ITextEditorActionConstants.CUT, action);

		action= new TextViewerAction(fPatternEditor, ITextOperationTarget.COPY);
		action.setText(PreferencesMessages.EditTemplateDialog_copy);
		fGlobalActions.put(ITextEditorActionConstants.COPY, action);

		action= new TextViewerAction(fPatternEditor, ITextOperationTarget.PASTE);
		action.setText(PreferencesMessages.EditTemplateDialog_paste);
		fGlobalActions.put(ITextEditorActionConstants.PASTE, action);

		action= new TextViewerAction(fPatternEditor, ITextOperationTarget.SELECT_ALL);
		action.setText(PreferencesMessages.EditTemplateDialog_select_all);
		fGlobalActions.put(ITextEditorActionConstants.SELECT_ALL, action);

		action= new TextViewerAction(fPatternEditor, ISourceViewer.CONTENTASSIST_PROPOSALS);
		action.setText(PreferencesMessages.EditTemplateDialog_content_assist);
		fGlobalActions.put(ITextEditorActionConstants.CONTENT_ASSIST, action);

		fSelectionActions.add(ITextEditorActionConstants.CUT);
		fSelectionActions.add(ITextEditorActionConstants.COPY);
		fSelectionActions.add(ITextEditorActionConstants.PASTE);

		// create context menu
		MenuManager manager= new MenuManager(null, null);
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(this::fillContextMenu);

		StyledText text= fPatternEditor.getTextWidget();
		Menu menu= manager.createContextMenu(text);
		text.setMenu(menu);
	}

	private void fillContextMenu(IMenuManager menu) {
		menu.add(new GroupMarker(ITextEditorActionConstants.GROUP_UNDO));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_UNDO, fGlobalActions.get(ITextEditorActionConstants.UNDO));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_UNDO, fGlobalActions.get(ITextEditorActionConstants.REDO));

		menu.add(new Separator(ITextEditorActionConstants.GROUP_EDIT));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_EDIT, fGlobalActions.get(ITextEditorActionConstants.CUT));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_EDIT, fGlobalActions.get(ITextEditorActionConstants.COPY));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_EDIT, fGlobalActions.get(ITextEditorActionConstants.PASTE));
		menu.appendToGroup(ITextEditorActionConstants.GROUP_EDIT, fGlobalActions.get(ITextEditorActionConstants.SELECT_ALL));

		menu.add(new Separator(IContextMenuConstants.GROUP_GENERATE));
		menu.appendToGroup(IContextMenuConstants.GROUP_GENERATE, fGlobalActions.get("ContentAssistProposal")); //$NON-NLS-1$
	}


	protected void updateSelectionDependentActions() {
		Iterator<String> iterator= fSelectionActions.iterator();
		while (iterator.hasNext())
			updateAction(iterator.next());
	}

	protected void updateAction(String actionId) {
		IAction action= fGlobalActions.get(actionId);
		if (action instanceof IUpdate)
			((IUpdate) action).update();
	}

	private int getIndex(String contextid) {

		if (contextid == null)
			return -1;

		for (int i= 0; i < fContextTypes.length; i++) {
			if (contextid.equals(fContextTypes[i][0])) {
				return i;
			}
		}
		return -1;
	}

	@Override
	protected void okPressed() {
		String name= fNameText == null ? fTemplate.getName() : fNameText.getText();
		boolean isAutoInsertable= fAutoInsertCheckbox != null && fAutoInsertCheckbox.getSelection();
		fTemplate= new Template(name, fDescriptionText.getText(), getContextId(), getPattern(), isAutoInsertable);
		super.okPressed();
	}

	private void updateStatusAndButtons() {
		StatusInfo status= fValidationStatus;
		boolean isEmpty= fNameText != null && fNameText.getText().length() == 0;
		if (!fSuppressError && isEmpty) {
			status= new StatusInfo();
			status.setError(PreferencesMessages.EditTemplateDialog_error_noname);
		} else if (fNameText != null && !isValidTemplateName(fNameText.getText())) {
			status= new StatusInfo();
			status.setError(PreferencesMessages.EditTemplateDialog_error_invalidName);
		} else if (!isValidPattern(fPatternEditor.getDocument().get())) {
			status= new StatusInfo();
			status.setError(PreferencesMessages.EditTemplateDialog_error_invalidPattern);
		}
		updateStatus(status);
	}

	/**
	 * Validates the pattern.
	 * <p>
	 * This implementation rejects invalid XML characters.
	 * </p>
	 *
	 * @param pattern the pattern to verify
	 * @return <code>true</code> if the pattern is valid
	 * @since 3.6
	 */
	private boolean isValidPattern(String pattern) {
		for (int i= 0; i < pattern.length(); i++) {
			char ch= pattern.charAt(i);
			if ((ch != 9) && (ch != 10) && (ch != 13) && (ch < 32))
				return false;
		}
		return true;
	}

	/**
	 * Checks whether the given string is a valid template name.
	 *
	 * @param name the string to test
	 * @return <code>true</code> if the name is valid
	 * @since 3.3.1
	 */
	private boolean isValidTemplateName(String name) {
		return name.length() == 0 || name.trim().length() != 0;
	}

	/*
	 * @see org.eclipse.jface.window.Window#configureShell(Shell)
	 */
	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaHelpContextIds.EDIT_TEMPLATE_DIALOG);
	}

	/**
	 * Returns the created template.
	 *
	 * @return the created template
	 * @since 3.1
	 */
	public Template getTemplate() {
		return fTemplate;
	}

	private String getPattern() {
		IDocument doc= fPatternEditor.getDocument();
		IRegion visible= fPatternEditor.getVisibleRegion();
		try {
			return doc.get(visible.getOffset(), doc.getLength() - visible.getOffset());
		} catch (BadLocationException e) {
			return ""; //$NON-NLS-1$
		}
	}

	/*
	 * @see org.eclipse.jface.dialogs.Dialog#getDialogBoundsSettings()
	 * @since 3.2
	 */
	@Override
	protected IDialogSettings getDialogBoundsSettings() {
		String sectionName= getClass().getName() + "_dialogBounds"; //$NON-NLS-1$
		IDialogSettings settings= JavaPlugin.getDefault().getDialogSettings();
		IDialogSettings section= settings.getSection(sectionName);
		if (section == null)
			section= settings.addNewSection(sectionName);
		return section;
	}

}
