package org.eclipse.jdt.internal.ui.snippeteditor;

/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 1999, 2000
 */

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.ui.IDebugUIEventFilter;

import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;import org.eclipse.jdt.internal.ui.JavaPlugin;
 
public class ScrapbookEventFilter implements IDebugUIEventFilter {

	protected ILaunch fLaunch;
	
	
	public ScrapbookEventFilter(ILaunch launch) {
		fLaunch = launch;
	}
	
	public boolean showLaunch(ILaunch launch) {
		return launch != fLaunch;
	}
	
	public boolean showDebugEvent(DebugEvent event) {
		try {
			Object s = event.getSource();
			if (s instanceof IJavaThread) {
				IJavaThread jt = (IJavaThread)s;
				if (jt.getLaunch() == fLaunch) {
					IJavaStackFrame f = (IJavaStackFrame)jt.getTopStackFrame();
					if (f.getMethodName().equals("main")) {
						return false;
					}
				}
			}
			return true;
		} catch(DebugException de) {
			JavaPlugin.log(de.getStatus());
			return true;
		}
	}
}