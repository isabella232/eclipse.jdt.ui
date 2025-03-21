/*******************************************************************************
 * Copyright (c) 2005, 2020 IBM Corporation and others.
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
 *     Microsoft Corporation - moved some methods to RefactoringAvailabilityTesterCore for jdt.core.manipulation use
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.ui.IWorkingSet;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.rename.MethodChecks;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgPolicyFactory;
import org.eclipse.jdt.internal.corext.refactoring.reorg.ReorgUtils;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;

import org.eclipse.jdt.internal.ui.javaeditor.JavaTextSelection;
import org.eclipse.jdt.internal.ui.refactoring.actions.RefactoringActions;
import org.eclipse.jdt.internal.ui.workingsets.IWorkingSetIDs;

/**
 * Helper class to detect whether a certain refactoring can be enabled on a
 * selection.
 * <p>
 * This class has been introduced to decouple actions from the refactoring code,
 * in order not to eagerly load refactoring classes during action
 * initialization.
 * </p>
 *
 * @since 3.1
 */
public final class RefactoringAvailabilityTester {

	public static IType getDeclaringType(IJavaElement element) {
		if (element == null)
			return null;
		if (!(element instanceof IType))
			element= element.getAncestor(IJavaElement.TYPE);
		return (IType) element;
	}

	public static IJavaElement[] getJavaElements(final Object[] elements) {
		List<IJavaElement> result= new ArrayList<>();
		for (Object element : elements) {
			if (element instanceof IJavaElement) {
				result.add((IJavaElement) element);
			}
		}
		return result.toArray(new IJavaElement[result.size()]);
	}

	public static IMember[] getPullUpMembers(final IType type) throws JavaModelException {
		final List<IMember> list= new ArrayList<>(3);
		if (type.exists()) {
			for (IMember member : type.getFields()) {
				if (isPullUpAvailable(member)) {
					list.add(member);
				}
			}
			for (IMember member : type.getMethods()) {
				if (isPullUpAvailable(member)) {
					list.add(member);
				}
			}
			for (IMember member : type.getTypes()) {
				if (isPullUpAvailable(member)) {
					list.add(member);
				}
			}
		}
		return list.toArray(new IMember[list.size()]);
	}

	public static IMember[] getPushDownMembers(final IType type) throws JavaModelException {
		final List<IMember> list= new ArrayList<>(3);
		if (type.exists()) {
			for (IMember member : type.getFields()) {
				if (isPushDownAvailable(member)) {
					list.add(member);
				}
			}
			for (IMember member : type.getMethods()) {
				if (isPushDownAvailable(member)) {
					list.add(member);
				}
			}
		}
		return list.toArray(new IMember[list.size()]);
	}

	public static IResource[] getResources(final Object[] elements) {
		List<IResource> result= new ArrayList<>();
		for (Object element : elements) {
			if (element instanceof IResource) {
				result.add((IResource) element);
			}
		}
		return result.toArray(new IResource[result.size()]);
	}

	public static IType getSingleSelectedType(IStructuredSelection selection) throws JavaModelException {
		Object first= selection.getFirstElement();
		if (first instanceof IType)
			return (IType) first;
		if (first instanceof ICompilationUnit) {
			final ICompilationUnit unit= (ICompilationUnit) first;
			if (unit.exists())
				return  JavaElementUtil.getMainType(unit);
		}
		return null;
	}

	public static IType getTopLevelType(final IMember[] members) {
		if (members != null && members.length == 1 && Checks.isTopLevelType(members[0]))
			return (IType) members[0];
		return null;
	}

	public static boolean isChangeSignatureAvailable(final IMethod method) throws JavaModelException {
		return (method != null) && Checks.isAvailable(method) && !Flags.isAnnotation(method.getDeclaringType().getFlags());
	}

	public static boolean isChangeSignatureAvailable(final IStructuredSelection selection) throws JavaModelException {
		final IMethod method= getSelectedMethod(selection);
		return isChangeSignatureAvailable(method);
	}

	public static boolean isChangeSignatureAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IMethod method= getSelectedMethod(selection);
		return isChangeSignatureAvailable(method);
	}

	public static IMethod getSelectedMethod(final IStructuredSelection selection) {
		if (selection.size() == 1) {
			if (selection.getFirstElement() instanceof IMethod) {
				return (IMethod) selection.getFirstElement();
			}
		}
		return null;
	}

	public static IMethod getSelectedMethod(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length == 1 && (elements[0] instanceof IMethod))
			return ((IMethod) elements[0]);
		final IJavaElement element= selection.resolveEnclosingElement();
		return (element instanceof IMethod) ? (IMethod)element : null;
	}

	public static boolean isCanonicalConstructor(IMethod method) {
		boolean isCanonicalConstructor = false;
		try {
			if (method != null && method.isConstructor()) {
				CompilationUnit cUnit= SharedASTProviderCore.getAST(method.getCompilationUnit(), SharedASTProviderCore.WAIT_YES, null);
				if (cUnit != null) {
					MethodDeclaration mDecl= ASTNodeSearchUtil.getMethodDeclarationNode(method, cUnit);
					if (mDecl != null) {
						IMethodBinding mBinding= mDecl.resolveBinding();
						if (mBinding != null && mBinding.isCanonicalConstructor()) {
							isCanonicalConstructor= true;
						}
					}
				}
			}
		} catch (JavaModelException e) {
			//do nothing
		}
		return isCanonicalConstructor;
	}

	public static boolean isCommonDeclaringType(final IMember[] members) {
		return RefactoringAvailabilityTesterCore.isCommonDeclaringType(members);
	}

	public static boolean isConvertAnonymousAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			if (selection.getFirstElement() instanceof IType) {
				return isConvertAnonymousAvailable((IType) selection.getFirstElement());
			}
		}
		return false;
	}

	public static boolean isConvertAnonymousAvailable(final IType type) throws JavaModelException {
		if (Checks.isAvailable(type)) {
			final IJavaElement element= type.getParent();
			if (element instanceof IField && JdtFlags.isEnum((IMember) element))
				return false;
			return type.isAnonymous();
		}
		return false;
	}

	public static boolean isConvertAnonymousAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IType type= RefactoringActions.getEnclosingType(selection);
		if (type != null)
			return RefactoringAvailabilityTester.isConvertAnonymousAvailable(type);
		return false;
	}

	public static boolean isCopyAvailable(final IResource[] resources, final IJavaElement[] elements) throws JavaModelException {
		return ReorgPolicyFactory.createCopyPolicy(resources, elements).canEnable();
	}

	public static boolean isDelegateCreationAvailable(final IField field) throws JavaModelException {
		return RefactoringAvailabilityTesterCore.isDelegateCreationAvailable(field);
	}

	public static boolean isDeleteAvailable(final IJavaElement element) {
		if (!element.exists())
			return false;
		if (element instanceof IJavaModel || element instanceof IJavaProject)
			return false;
		if (element.getParent() != null && element.getParent().isReadOnly())
			return false;
		if (element instanceof IPackageFragmentRoot) {
			IPackageFragmentRoot root= (IPackageFragmentRoot) element;
			if (root.isExternal() || Checks.isClasspathDelete(root)) // TODO: rename isClasspathDelete
				return false;

			if (root.getResource().equals(root.getJavaProject().getProject()))
				return false;
		}
		if (element instanceof IPackageFragment && ((IPackageFragment) element).isDefaultPackage()) {
			return false;
		}
		if (element.getResource() == null && !RefactoringAvailabilityTester.isWorkingCopyElement(element))
			return false;
		if (element instanceof IMember && ((IMember) element).isBinary())
			return false;
		return true;
	}

	public static boolean isDeleteAvailable(final IResource resource) {
		if (!resource.exists() || resource.isPhantom())
			return false;
		if (resource.getType() == IResource.ROOT || resource.getType() == IResource.PROJECT)
			return false;
		return true;
	}

	public static boolean isDeleteAvailable(final IStructuredSelection selection) {
		if (!selection.isEmpty())
			return isDeleteAvailable(selection.toArray());
		return false;
	}

	public static boolean isDeleteAvailable(final Object[] objects) {
		if (objects.length != 0) {
			if (ReorgUtils.containsOnlyWorkingSets(Arrays.asList(objects)))
				return true;
			final IResource[] resources= RefactoringAvailabilityTester.getResources(objects);
			final IJavaElement[] elements= RefactoringAvailabilityTester.getJavaElements(objects);

			if (objects.length != resources.length + elements.length)
				return false;
			for (IResource resource : resources) {
				if (!isDeleteAvailable(resource)) {
					return false;
				}
			}
			for (IJavaElement element : elements) {
				if (!isDeleteAvailable(element)) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	public static boolean isExternalizeStringsAvailable(final IStructuredSelection selection) throws JavaModelException {
		for (Object element : selection) {
			if (element instanceof IJavaElement) {
				IJavaElement javaElement= (IJavaElement)element;
				if (javaElement.exists() && !javaElement.isReadOnly()) {
					int elementType= javaElement.getElementType();
					switch (elementType) {
						case IJavaElement.PACKAGE_FRAGMENT:
						case IJavaElement.JAVA_PROJECT:
							return true;
						case IJavaElement.PACKAGE_FRAGMENT_ROOT:
							IPackageFragmentRoot root= (IPackageFragmentRoot)javaElement;
							if (!root.isExternal() && !ReorgUtils.isClassFolder(root))
								return true;
							break;
						case IJavaElement.COMPILATION_UNIT:
							ICompilationUnit cu= (ICompilationUnit)javaElement;
							if (cu.exists())
								return true;
							break;
						case IJavaElement.TYPE:
							IJavaElement parent= ((IType) element).getParent();
							if (parent instanceof ICompilationUnit && parent.exists())
								return true;
							break;
						default:
							break;
					}
				}
			} else if (element instanceof IWorkingSet) {
				IWorkingSet workingSet= (IWorkingSet) element;
				return IWorkingSetIDs.JAVA.equals(workingSet.getId());
			}
		}
		return false;
	}

	public static boolean isExtractConstantAvailable(final JavaTextSelection selection) {
		return (selection.resolveInClassInitializer() || selection.resolveInMethodBody() || selection.resolveInVariableInitializer() || selection.resolveInAnnotation())
				&& Checks.isExtractableExpression(selection.resolveSelectedNodes(), selection.resolveCoveringNode());
	}

	public static boolean isExtractInterfaceAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			Object first= selection.getFirstElement();
			if (first instanceof IType) {
				return isExtractInterfaceAvailable((IType) first);
			} else if (first instanceof ICompilationUnit) {
				ICompilationUnit unit= (ICompilationUnit) first;
				if (!unit.exists() || unit.isReadOnly())
					return false;

				return true;
			}
		}
		return false;
	}

	public static boolean isExtractInterfaceAvailable(final IType type) throws JavaModelException {
		return Checks.isAvailable(type) && !type.isBinary() && !type.isReadOnly() && !type.isAnnotation() && !type.isAnonymous() && !type.isLambda();
	}

	public static boolean isExtractInterfaceAvailable(final JavaTextSelection selection) throws JavaModelException {
		return isExtractInterfaceAvailable(RefactoringActions.getEnclosingOrPrimaryType(selection));
	}

	public static boolean isExtractMethodAvailable(final ASTNode[] nodes) {
		if (nodes != null && nodes.length != 0) {
			if (nodes.length == 1)
				return nodes[0] instanceof Statement || Checks.isExtractableExpression(nodes[0]);
			else {
				for (ASTNode node : nodes) {
					if (!(node instanceof Statement)) {
						return false;
					}
				}
				return true;
			}
		}
		return false;
	}

	public static boolean isExtractMethodAvailable(final JavaTextSelection selection) {
		return (selection.resolveInMethodBody() || selection.resolveInClassInitializer() || selection.resolveInVariableInitializer())
			&& !selection.resolveInAnnotation()
			&& RefactoringAvailabilityTester.isExtractMethodAvailable(selection.resolveSelectedNodes());
	}

	public static boolean isExtractSupertypeAvailable(IMember member) throws JavaModelException {
		if (!member.exists())
			return false;
		final int type= member.getElementType();
		if (type != IJavaElement.METHOD && type != IJavaElement.FIELD && type != IJavaElement.TYPE)
			return false;
		if (JdtFlags.isEnum(member) && type != IJavaElement.TYPE)
			return false;
		if (!Checks.isAvailable(member))
			return false;
		if (member instanceof IMethod) {
			final IMethod method= (IMethod) member;
			if (method.isConstructor())
				return false;
			if (JdtFlags.isNative(method))
				return false;
			member= method.getDeclaringType();
		} else if (member instanceof IField) {
			member= member.getDeclaringType();
		}
		if (member instanceof IType) {
			if (JdtFlags.isEnum(member) || JdtFlags.isAnnotation(member))
				return false;
			if (member.getDeclaringType() != null && !JdtFlags.isStatic(member))
				return false;
			if (((IType)member).isAnonymous())
				return false; // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=253727
			if (((IType)member).isLambda())
				return false;
		}
		return true;
	}

	public static boolean isExtractSupertypeAvailable(final IMember[] members) throws JavaModelException {
		if (members != null && members.length != 0) {
			final IType type= getTopLevelType(members);
			if (type != null && !type.isClass())
				return false;
			for (IMember member : members) {
				if (!isExtractSupertypeAvailable(member)) {
					return false;
				}
			}
			return members.length == 1 || isCommonDeclaringType(members);
		}
		return false;
	}

	public static boolean isExtractSupertypeAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (!selection.isEmpty()) {
			if (selection.size() == 1) {
				if (selection.getFirstElement() instanceof ICompilationUnit)
					return true; // Do not force opening
				final IType type= getSingleSelectedType(selection);
				if (type != null)
					return Checks.isAvailable(type) && isExtractSupertypeAvailable(new IType[] { type});
			}
			for (Object member : selection) {
				if (!(member instanceof IMember))
					return false;
			}
			final Set<IMember> members= new HashSet<>();
			@SuppressWarnings("unchecked")
			List<IMember> selectionList= (List<IMember>) (List<?>) Arrays.asList(selection.toArray());
			members.addAll(selectionList);
			return isExtractSupertypeAvailable(members.toArray(new IMember[members.size()]));
		}
		return false;
	}

	public static boolean isExtractSupertypeAvailable(final JavaTextSelection selection) throws JavaModelException {
		IJavaElement element= selection.resolveEnclosingElement();
		if (!(element instanceof IMember))
			return false;
		return isExtractSupertypeAvailable(new IMember[] { (IMember) element});
	}

	public static boolean isExtractTempAvailable(final JavaTextSelection selection) {
		final ASTNode[] nodes= selection.resolveSelectedNodes();
		return (selection.resolveInMethodBody() || selection.resolveInClassInitializer())
				&& !selection.resolveInAnnotation()
				&& (Checks.isExtractableExpression(nodes, selection.resolveCoveringNode()) || (nodes != null && nodes.length == 1 && nodes[0] instanceof ExpressionStatement));
	}

	public static boolean isGeneralizeTypeAvailable(final IJavaElement element) throws JavaModelException {
		if (element != null && element.exists()) {
			String type= null;
			if (element instanceof IMethod)
				type= ((IMethod) element).getReturnType();
			else if (element instanceof IField) {
				final IField field= (IField) element;
				if (JdtFlags.isEnum(field))
					return false;
				type= field.getTypeSignature();
			} else if (element instanceof ILocalVariable)
				return true;
			else if (element instanceof IType) {
				final IType clazz= (IType) element;
				if (JdtFlags.isEnum(clazz))
					return false;
				return true;
			}
			if (type == null || PrimitiveType.toCode(Signature.toString(type)) != null)
				return false;
			return true;
		}
		return false;
	}

	public static boolean isGeneralizeTypeAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			final Object element= selection.getFirstElement();
			if (element instanceof IMethod) {
				final IMethod method= (IMethod) element;
				if (!method.exists())
					return false;
				final String type= method.getReturnType();
				if (PrimitiveType.toCode(Signature.toString(type)) == null)
					return Checks.isAvailable(method);
			} else if (element instanceof IField) {
				final IField field= (IField) element;
				if (!field.exists())
					return false;
				if (!JdtFlags.isEnum(field))
					return Checks.isAvailable(field);
			}
		}
		return false;
	}

	public static boolean isGeneralizeTypeAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length != 1)
			return false;
		return isGeneralizeTypeAvailable(elements[0]);
	}

	public static boolean isInferTypeArgumentsAvailable(final IJavaElement element) throws JavaModelException {
		if (!Checks.isAvailable(element)) {
			return false;
		} else if (element instanceof IJavaProject) {
			IJavaProject project= (IJavaProject) element;
			for (IClasspathEntry classpathEntry : project.getRawClasspath()) {
				if (classpathEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
					return true;
				}
			}
			return false;
		} else if (element instanceof IPackageFragmentRoot) {
			return ((IPackageFragmentRoot) element).getKind() == IPackageFragmentRoot.K_SOURCE;
		} else if (element instanceof IPackageFragment) {
			return ((IPackageFragment) element).getKind() == IPackageFragmentRoot.K_SOURCE;
		} else if (element instanceof ICompilationUnit
				|| element.getAncestor(IJavaElement.COMPILATION_UNIT) != null) {
			return true;
		} else {
			return false;
		}
	}

	public static boolean isInferTypeArgumentsAvailable(final IJavaElement[] elements) throws JavaModelException {
		if (elements.length == 0)
			return false;

		for (IJavaElement element : elements) {
			if (!(isInferTypeArgumentsAvailable(element))) {
				return false;
			}
		}
		return true;
	}

	public static boolean isInferTypeArgumentsAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.isEmpty())
			return false;

		for (Object element : selection) {
			if (!(element instanceof IJavaElement))
				return false;
			if (element instanceof ICompilationUnit) {
				ICompilationUnit unit= (ICompilationUnit) element;
				if (!unit.exists() || unit.isReadOnly())
					return false;

				return true;
			}
			if (!isInferTypeArgumentsAvailable((IJavaElement) element))
				return false;
		}
		return true;
	}

	public static boolean isInlineConstantAvailable(final IField field) throws JavaModelException {
		return RefactoringAvailabilityTesterCore.isInlineConstantAvailable(field);
	}

	public static boolean isInlineConstantAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.isEmpty() || selection.size() != 1)
			return false;
		final Object first= selection.getFirstElement();
		return (first instanceof IField) && isInlineConstantAvailable(((IField) first));
	}

	public static boolean isInlineConstantAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length != 1)
			return false;
		return (elements[0] instanceof IField) && isInlineConstantAvailable(((IField) elements[0]));
	}

	public static boolean isInlineMethodAvailable(IMethod method) throws JavaModelException {
		return RefactoringAvailabilityTesterCore.isInlineMethodAvailable(method);
	}

	public static boolean isInlineMethodAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.isEmpty() || selection.size() != 1)
			return false;
		final Object first= selection.getFirstElement();
		return (first instanceof IMethod) && isInlineMethodAvailable(((IMethod) first));
	}

	public static boolean isInlineMethodAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length != 1) {
			IJavaElement enclosingElement= selection.resolveEnclosingElement();
			if (!(enclosingElement instanceof IMember))
				return false;
			ITypeRoot typeRoot= ((IMember)enclosingElement).getTypeRoot();
			CompilationUnit compilationUnit= selection.resolvePartialAstAtOffset();
			if (compilationUnit == null)
				return false;
			return getInlineableMethodNode(typeRoot, compilationUnit, selection.getOffset(), selection.getLength()) != null;
		}
		IJavaElement element= elements[0];
		if (!(element instanceof IMethod))
			return false;
		IMethod method= (IMethod) element;
		if (!isInlineMethodAvailable((method)))
			return false;

		// in binary class, only activate for method declarations
		IJavaElement enclosingElement= selection.resolveEnclosingElement();
		if (enclosingElement == null || enclosingElement.getAncestor(IJavaElement.CLASS_FILE) == null)
			return true;
		if (!(enclosingElement instanceof IMethod))
			return false;
		IMethod enclosingMethod= (IMethod) enclosingElement;
		if (enclosingMethod.isConstructor())
			return false;
		int nameOffset= enclosingMethod.getNameRange().getOffset();
		int nameLength= enclosingMethod.getNameRange().getLength();
		return (nameOffset <= selection.getOffset()) && (selection.getOffset() + selection.getLength() <= nameOffset + nameLength);
	}

	public static ASTNode getInlineableMethodNode(ITypeRoot typeRoot, CompilationUnit root, int offset, int length) {
		return RefactoringAvailabilityTesterCore.getInlineableMethodNode(typeRoot, root, offset, length);
	}

	public static boolean isInlineTempAvailable(final ILocalVariable variable) throws JavaModelException {
		return RefactoringAvailabilityTesterCore.isInlineTempAvailable(variable);
	}

	public static boolean isInlineTempAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length != 1)
			return false;
		return (elements[0] instanceof ILocalVariable) && isInlineTempAvailable((ILocalVariable) elements[0]);
	}

	public static boolean isIntroduceFactoryAvailable(final IMethod method) throws JavaModelException {
		return Checks.isAvailable(method) && method.isConstructor();
	}

	public static boolean isIntroduceFactoryAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1 && selection.getFirstElement() instanceof IMethod)
			return isIntroduceFactoryAvailable((IMethod) selection.getFirstElement());
		return false;
	}

	public static boolean isIntroduceFactoryAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length == 1 && elements[0] instanceof IMethod)
			return isIntroduceFactoryAvailable((IMethod) elements[0]);

		// there's no IMethod for the default constructor
		if (!Checks.isAvailable(selection.resolveEnclosingElement()))
			return false;
		ASTNode node= selection.resolveCoveringNode();
		if (node == null) {
			ASTNode[] selectedNodes= selection.resolveSelectedNodes();
			if (selectedNodes != null && selectedNodes.length == 1) {
				node= selectedNodes[0];
				if (node == null)
					return false;
			} else {
				return false;
			}
		}

		if (node.getNodeType() == ASTNode.CLASS_INSTANCE_CREATION)
			return true;

		node= ASTNodes.getNormalizedNode(node);
		if (node.getLocationInParent() == ClassInstanceCreation.TYPE_PROPERTY)
			return true;

		return false;
	}

	public static boolean isIntroduceIndirectionAvailable(IMethod method) throws JavaModelException {
		if (method == null)
			return false;
		if (!method.exists())
			return false;
		if (!method.isStructureKnown())
			return false;
		if (method.isConstructor())
			return false;
		if (method.getDeclaringType().isAnnotation())
			return false;
		if (JavaModelUtil.isPolymorphicSignature(method))
			return false;

		return true;
	}

	public static boolean isIntroduceIndirectionAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.isEmpty() || selection.size() != 1)
			return false;
		final Object first= selection.getFirstElement();
		return (first instanceof IMethod) && isIntroduceIndirectionAvailable(((IMethod) first));
	}

	public static boolean isIntroduceIndirectionAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length == 1)
			return (elements[0] instanceof IMethod) && isIntroduceIndirectionAvailable(((IMethod) elements[0]));
		ASTNode[] selectedNodes= selection.resolveSelectedNodes();
		if (selectedNodes == null || selectedNodes.length != 1)
			return false;
		switch (selectedNodes[0].getNodeType()) {
			case ASTNode.METHOD_DECLARATION:
			case ASTNode.METHOD_INVOCATION:
			case ASTNode.SUPER_METHOD_INVOCATION:
				return true;
			default:
				return false;
		}
	}

	public static boolean isIntroduceParameterAvailable(final ASTNode[] selectedNodes, ASTNode coveringNode) {
		return Checks.isExtractableExpression(selectedNodes, coveringNode);
	}

	public static boolean isIntroduceParameterAvailable(final JavaTextSelection selection) {
		return selection.resolveInMethodBody()
				&& !selection.resolveInAnnotation()
				&& isIntroduceParameterAvailable(selection.resolveSelectedNodes(), selection.resolveCoveringNode());
	}

	public static boolean isMoveAvailable(final IResource[] resources, final IJavaElement[] elements) throws JavaModelException {
		if (elements != null) {
			for (IJavaElement element : elements) {
				if (element == null || !element.exists())
					return false;
				if ((element instanceof IType) && ((IType) element).isLocal())
					return false;
				if ((element instanceof IPackageDeclaration))
					return false;
				if (element instanceof IField
						&& (JdtFlags.isEnum((IMember) element)
								|| ((IField) element).isRecordComponent()))
					return false;
				if ((element instanceof IMethod) && ((IMethod)element).isConstructor())
					return false;
			}
		}
		return ReorgPolicyFactory.createMovePolicy(resources, elements).canEnable();
	}

	public static boolean isMoveAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement element= selection.resolveEnclosingElement();
		if (element == null)
			return false;
		return isMoveAvailable(new IResource[0], new IJavaElement[] { element});
	}

	public static boolean isMoveInnerAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			Object first= selection.getFirstElement();
			if (first instanceof IType) {
				return isMoveInnerAvailable((IType) first);
			}
		}
		return false;
	}

	public static boolean isMoveInnerAvailable(final IType type) throws JavaModelException {
		return Checks.isAvailable(type) && !Checks.isAnonymous(type) && !JavaElementUtil.isMainType(type) && !Checks.isInsideLocalType(type);
	}

	public static boolean isMoveInnerAvailable(final JavaTextSelection selection) throws JavaModelException {
		IType type= RefactoringAvailabilityTester.getDeclaringType(selection.resolveEnclosingElement());
		if (type == null)
			return false;
		return isMoveInnerAvailable(type);
	}

	public static boolean isMoveMethodAvailable(final IMethod method) throws JavaModelException {
		return method.exists() && !method.isConstructor() && !method.isBinary() && !method.isReadOnly()
				&& !JdtFlags.isStatic(method) && (JdtFlags.isDefaultMethod(method) || !method.getDeclaringType().isInterface());
	}

	public static boolean isMoveMethodAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			final Object first= selection.getFirstElement();
			return first instanceof IMethod && isMoveMethodAvailable((IMethod) first);
		}
		return false;
	}

	public static boolean isMoveMethodAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement method= selection.resolveEnclosingElement();
		if (!(method instanceof IMethod))
			return false;
		return isMoveMethodAvailable((IMethod) method);
	}

	public static boolean isMoveStaticAvailable(final IMember member) throws JavaModelException {
		return RefactoringAvailabilityTesterCore.isMoveStaticAvailable(member);
	}

	public static boolean isMoveStaticAvailable(final IMember[] members) throws JavaModelException {
		return RefactoringAvailabilityTesterCore.isMoveStaticAvailable(members);
	}

	public static boolean isMoveStaticAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement element= selection.resolveEnclosingElement();
		if (!(element instanceof IMember))
			return false;
		return RefactoringAvailabilityTester.isMoveStaticMembersAvailable(new IMember[] { (IMember) element});
	}

	public static boolean isMoveStaticMembersAvailable(final IMember[] members) throws JavaModelException {
		return RefactoringAvailabilityTesterCore.isMoveStaticMembersAvailable(members);
	}

	public static boolean isPromoteTempAvailable(final ILocalVariable variable) throws JavaModelException {
		return Checks.isAvailable(variable);
	}

	public static boolean isPromoteTempAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length != 1)
			return false;
		return (elements[0] instanceof ILocalVariable) && isPromoteTempAvailable((ILocalVariable) elements[0]);
	}

	public static boolean isPullUpAvailable(IMember member) throws JavaModelException {
		if (!member.exists())
			return false;
		final int type= member.getElementType();
		if (type != IJavaElement.METHOD && type != IJavaElement.FIELD && type != IJavaElement.TYPE)
			return false;
		if (JdtFlags.isEnum(member) && type != IJavaElement.TYPE)
			return false;
		if (!Checks.isAvailable(member))
			return false;
		if (member instanceof IType) {
			if (!JdtFlags.isStatic(member) && !JdtFlags.isEnum(member) && !JdtFlags.isAnnotation(member))
				return false;
		}
		if (member instanceof IMethod) {
			final IMethod method= (IMethod) member;
			if (method.isConstructor())
				return false;
			if (JdtFlags.isNative(method))
				return false;
			final IType declaring= method.getDeclaringType();
			if (declaring != null && declaring.isAnnotation())
				return false;
		}
		return true;
	}

	public static boolean isPullUpAvailable(final IMember[] members) throws JavaModelException {
		if (members != null && members.length != 0) {
			final IType type= getTopLevelType(members);
			if (type != null && getPullUpMembers(type).length != 0)
				return true;
			for (IMember member : members) {
				if (!isPullUpAvailable(member)) {
					return false;
				}
			}
			return isCommonDeclaringType(members);
		}
		return false;
	}

	public static boolean isPullUpAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (!selection.isEmpty()) {
			if (selection.size() == 1) {
				if (selection.getFirstElement() instanceof ICompilationUnit)
					return true; // Do not force opening
				final IType type= getSingleSelectedType(selection);
				if (type != null)
					return Checks.isAvailable(type) && isPullUpAvailable(new IType[] { type});
			}
			for (Object member : selection) {
				if (!(member instanceof IMember))
					return false;
			}
			final Set<IMember> members= new HashSet<>();
			@SuppressWarnings("unchecked")
			List<IMember> selectionList= (List<IMember>) (List<?>) Arrays.asList(selection.toArray());
			members.addAll(selectionList);
			return isPullUpAvailable(members.toArray(new IMember[members.size()]));
		}
		return false;
	}

	public static boolean isPullUpAvailable(final JavaTextSelection selection) throws JavaModelException {
		IJavaElement element= selection.resolveEnclosingElement();
		if (!(element instanceof IMember))
			return false;
		return isPullUpAvailable(new IMember[] { (IMember) element});
	}

	public static boolean isPushDownAvailable(final IMember member) throws JavaModelException {
		if (!member.exists())
			return false;
		final int type= member.getElementType();
		if (type != IJavaElement.METHOD && type != IJavaElement.FIELD)
			return false;
		if (JdtFlags.isEnum(member))
			return false;
		if (!Checks.isAvailable(member))
			return false;
		if (JdtFlags.isStatic(member))
			return false;
		if (type == IJavaElement.METHOD) {
			final IMethod method= (IMethod) member;
			if (method.isConstructor())
				return false;
			if (JdtFlags.isNative(method))
				return false;
			final IType declaring= method.getDeclaringType();
			if (declaring != null && declaring.isAnnotation())
				return false;
		}
		return true;
	}

	public static boolean isPushDownAvailable(final IMember[] members) throws JavaModelException {
		if (members != null && members.length != 0) {
			final IType type= getTopLevelType(members);
			if (type != null && RefactoringAvailabilityTester.getPushDownMembers(type).length != 0)
				return true;
			if (type != null && JdtFlags.isEnum(type))
				return false;
			for (IMember member : members) {
				if (!isPushDownAvailable(member)) {
					return false;
				}
			}
			return isCommonDeclaringType(members);
		}
		return false;
	}

	public static boolean isPushDownAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (!selection.isEmpty()) {
			if (selection.size() == 1) {
				if (selection.getFirstElement() instanceof ICompilationUnit)
					return true; // Do not force opening
				final IType type= getSingleSelectedType(selection);
				if (type != null)
					return isPushDownAvailable(new IType[] { type});
			}
			for (Object member : selection) {
				if (!(member instanceof IMember))
					return false;
			}
			final Set<IMember> members= new HashSet<>();
			@SuppressWarnings("unchecked")
			List<IMember> selectionList= (List<IMember>) (List<?>) Arrays.asList(selection.toArray());
			members.addAll(selectionList);
			return isPushDownAvailable(members.toArray(new IMember[members.size()]));
		}
		return false;
	}

	public static boolean isPushDownAvailable(final JavaTextSelection selection) throws JavaModelException {
		IJavaElement element= selection.resolveEnclosingElement();
		if (!(element instanceof IMember))
			return false;
		return isPullUpAvailable(new IMember[] { (IMember) element});
	}

	public static boolean isRenameAvailable(final ICompilationUnit unit) {
		if (unit == null)
			return false;
		if (!unit.exists())
			return false;
		if (!JavaModelUtil.isPrimary(unit))
			return false;
		if (unit.isReadOnly())
			return false;
		return true;
	}

	public static boolean isRenameAvailable(final IJavaProject project) throws JavaModelException {
		if (project == null)
			return false;
		if (!Checks.isAvailable(project))
			return false;
		if (!project.isConsistent())
			return false;
		return true;
	}

	public static boolean isRenameAvailable(final ILocalVariable variable) throws JavaModelException {
		return Checks.isAvailable(variable);
	}

	public static boolean isRenameAvailable(final IMethod method) throws CoreException {
		if (method == null)
			return false;
		if (!Checks.isAvailable(method))
			return false;
		if (method.isConstructor())
			return false;
		if (isRenameProhibited(method))
			return false;
		return true;
	}

	public static boolean isRenameAvailable(final IPackageFragment fragment) throws JavaModelException {
		if (fragment == null)
			return false;
		if (!Checks.isAvailable(fragment))
			return false;
		if (fragment.isDefaultPackage())
			return false;
		return true;
	}

	public static boolean isRenameAvailable(final IPackageFragmentRoot root) throws JavaModelException {
		if (root == null)
			return false;
		if (!Checks.isAvailable(root))
			return false;
		if (root.isArchive())
			return false;
		if (root.isExternal())
			return false;
		if (!root.isConsistent())
			return false;
		if (root.getResource() instanceof IProject)
			return false;
		return true;
	}

	public static boolean isRenameAvailable(final IResource resource) {
		if (resource == null)
			return false;
		if (!resource.exists())
			return false;
		if (!resource.isAccessible())
			return false;
		return true;
	}

	public static boolean isRenameAvailable(final IType type) throws JavaModelException {
		if (type == null)
			return false;
		if (type.isAnonymous())
			return false;
		if (type.isLambda())
			return false;
		if (!Checks.isAvailable(type))
			return false;
		if (isRenameProhibited(type))
			return false;
		return true;
	}

	public static boolean isRenameAvailable(final ITypeParameter parameter) throws JavaModelException {
		return Checks.isAvailable(parameter);
	}

	public static boolean isRenameEnumConstAvailable(final IField field) throws JavaModelException {
		return Checks.isAvailable(field) && field.getDeclaringType().isEnum();
	}

	public static boolean isRenameFieldAvailable(final IField field) throws JavaModelException {
		return Checks.isAvailable(field) && !JdtFlags.isEnum(field);
	}

	public static boolean isRenameNonVirtualMethodAvailable(final IMethod method) throws JavaModelException, CoreException {
		return isRenameAvailable(method) && !MethodChecks.isVirtual(method);
	}

	public static boolean isRenameProhibited(final IMethod method) throws CoreException {
		if ("toString".equals(method.getElementName()) //$NON-NLS-1$
				&& (method.getNumberOfParameters() == 0) && ("Ljava.lang.String;".equals(method.getReturnType()) //$NON-NLS-1$
						|| "QString;".equals(method.getReturnType()) //$NON-NLS-1$
						|| "Qjava.lang.String;".equals(method.getReturnType()))) //$NON-NLS-1$
			return true;
		else
			return false;
	}

	public static boolean isRenameProhibited(final IType type) {
		return "java.lang".equals(type.getPackageFragment().getElementName()); //$NON-NLS-1$
	}

	public static boolean isRenameVirtualMethodAvailable(final IMethod method) throws CoreException {
		return isRenameAvailable(method) && MethodChecks.isVirtual(method);
	}

	public static boolean isRenameElementAvailable(IJavaElement element) throws CoreException {
		switch (element.getElementType()) {
			case IJavaElement.JAVA_PROJECT:
				return isRenameAvailable((IJavaProject) element);
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				return isRenameAvailable((IPackageFragmentRoot) element);
			case IJavaElement.PACKAGE_FRAGMENT:
				return isRenameAvailable((IPackageFragment) element);
			case IJavaElement.COMPILATION_UNIT:
				return isRenameAvailable((ICompilationUnit) element);
			case IJavaElement.TYPE:
				return isRenameAvailable((IType) element);
			case IJavaElement.METHOD:
				final IMethod method= (IMethod) element;
				if (method.isConstructor())
					return isRenameAvailable(method.getDeclaringType());
				else
					return isRenameAvailable(method);
			case IJavaElement.FIELD:
				final IField field= (IField) element;
				if (Flags.isEnum(field.getFlags()))
					return isRenameEnumConstAvailable(field);
				else
					return isRenameFieldAvailable(field);
			case IJavaElement.TYPE_PARAMETER:
				return isRenameAvailable((ITypeParameter) element);
			case IJavaElement.LOCAL_VARIABLE:
				return isRenameAvailable((ILocalVariable) element);
		}
		return false;
	}

	public static boolean isReplaceInvocationsAvailable(IMethod method) throws JavaModelException {
		if (method == null)
			return false;
		if (!method.exists())
			return false;
		if (method.isConstructor())
			return false;
		return true;
	}

	public static boolean isReplaceInvocationsAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.isEmpty() || selection.size() != 1)
			return false;
		final Object first= selection.getFirstElement();
		return (first instanceof IMethod) && isReplaceInvocationsAvailable(((IMethod) first));
	}

	public static boolean isReplaceInvocationsAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length != 1)
			return false;
		IJavaElement element= elements[0];
		return (element instanceof IMethod) && isReplaceInvocationsAvailable(((IMethod) element));
	}

	public static boolean isSelfEncapsulateAvailable(IField field) throws JavaModelException {
		return Checks.isAvailable(field) && !JdtFlags.isEnum(field) && !field.getDeclaringType().isInterface();
	}

	public static boolean isSelfEncapsulateAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			if (selection.getFirstElement() instanceof IField) {
				final IField field= (IField) selection.getFirstElement();
				return isSelfEncapsulateAvailable(field);
			}
		}
		return false;
	}

	public static boolean isSelfEncapsulateAvailable(final JavaTextSelection selection) throws JavaModelException {
		final IJavaElement[] elements= selection.resolveElementAtOffset();
		if (elements.length != 1)
			return false;
		return (elements[0] instanceof IField) && isSelfEncapsulateAvailable((IField) elements[0]);
	}

	public static boolean isUseSuperTypeAvailable(final IStructuredSelection selection) throws JavaModelException {
		if (selection.size() == 1) {
			final Object first= selection.getFirstElement();
			if (first instanceof IType) {
				return isUseSuperTypeAvailable((IType) first);
			} else if (first instanceof ICompilationUnit) {
				ICompilationUnit unit= (ICompilationUnit) first;
				if (!unit.exists() || unit.isReadOnly())
					return false;

				return true;
			}
		}
		return false;
	}

	public static boolean isUseSuperTypeAvailable(final IType type) throws JavaModelException {
		return type != null && type.exists() && !type.isAnnotation() && !type.isAnonymous() && !type.isLambda();
	}

	public static boolean isUseSuperTypeAvailable(final JavaTextSelection selection) throws JavaModelException {
		return isUseSuperTypeAvailable(RefactoringActions.getEnclosingOrPrimaryType(selection));
	}

	public static boolean isWorkingCopyElement(final IJavaElement element) {
		if (element instanceof ICompilationUnit)
			return ((ICompilationUnit) element).isWorkingCopy();
		if (ReorgUtils.isInsideCompilationUnit(element))
			return ReorgUtils.getCompilationUnit(element).isWorkingCopy();
		return false;
	}

	private RefactoringAvailabilityTester() {
		// Not for instantiation
	}

	public static boolean isIntroduceParameterObjectAvailable(IStructuredSelection selection) throws JavaModelException{
		IMethod method= getSelectedMethod(selection); //TODO test selected element for more than 1 parameter?
		return isChangeSignatureAvailable(method) && !isCanonicalConstructor(method);
	}

	public static boolean isIntroduceParameterObjectAvailable(JavaTextSelection selection) throws JavaModelException{
		IMethod method= getSelectedMethod(selection); //TODO test selected element for more than 1 parameter?
		return isChangeSignatureAvailable(method) && !isCanonicalConstructor(method);
	}



	public static boolean isExtractClassAvailable(IType type) throws JavaModelException {
		if (type == null)
			return false;
		if (!type.exists())
			return false;
		return ReorgUtils.isInsideCompilationUnit(type) && type.isClass() && !type.isAnonymous()  && !type.isLambda();
	}

}
