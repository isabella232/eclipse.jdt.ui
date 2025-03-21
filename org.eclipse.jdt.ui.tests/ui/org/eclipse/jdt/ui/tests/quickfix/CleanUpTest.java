/*******************************************************************************
 * Copyright (c) 2000, 2021 IBM Corporation and others.
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
 *     Alex Blewitt - https://bugs.eclipse.org/bugs/show_bug.cgi?id=168954
 *     Chris West (Faux) <eclipse@goeswhere.com> - [clean up] "Use modifier 'final' where possible" can introduce compile errors - https://bugs.eclipse.org/bugs/show_bug.cgi?id=272532
 *     Red Hat Inc. - redundant semicolons test
 *     Fabrice TIERCELIN - Autoboxing and Unboxing test
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.TestOptions;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.core.manipulation.CleanUpOptionsCore;

import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.FixMessages;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.tests.core.rules.Java13ProjectTestSetup;
import org.eclipse.jdt.ui.tests.core.rules.ProjectTestSetup;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUpCore;
import org.eclipse.jdt.internal.ui.fix.Java50CleanUp;
import org.eclipse.jdt.internal.ui.fix.MultiFixMessages;
import org.eclipse.jdt.internal.ui.fix.PlainReplacementCleanUpCore;
import org.eclipse.jdt.internal.ui.fix.RedundantModifiersCleanUp;
import org.eclipse.jdt.internal.ui.fix.UnimplementedCodeCleanUp;
import org.eclipse.jdt.internal.ui.text.correction.ProblemLocation;
import org.eclipse.jdt.internal.ui.util.ASTHelper;

public class CleanUpTest extends CleanUpTestCase {
	@Rule
	public ProjectTestSetup projectSetup= new Java13ProjectTestSetup(false);

	IJavaProject fJProject1= getProject();

	@Override
	protected IJavaProject getProject() {
		return projectSetup.getProject();
	}

	@Override
	protected IClasspathEntry[] getDefaultClasspath() throws CoreException {
		return projectSetup.getDefaultClasspath();
	}

	private static class NoChangeRedundantModifiersCleanUp extends RedundantModifiersCleanUp {
		private NoChangeRedundantModifiersCleanUp(Map<String, String> options) {
			super(options);
		}

		@Override
		protected ICleanUpFix createFix(CompilationUnit unit) throws CoreException {
			return super.createFix(unit);
		}
	}

	@Test
	public void testCleanUpConstantsAreDistinct() throws Exception {
		Field[] allCleanUpConstantsFields= CleanUpConstants.class.getDeclaredFields();

		Map<String, Field> stringFieldsByValue= new HashMap<>();

		for (Field field : allCleanUpConstantsFields) {
			if (String.class.equals(field.getType())
					&& field.getAnnotation(Deprecated.class) == null
					&& !field.getName().startsWith("DEFAULT_")) {
				final String constantValue= (String) field.get(null);

				assertFalse(stringFieldsByValue.containsKey(constantValue),
						() -> CleanUpConstants.class.getCanonicalName()
						+ "."
						+ field.getName()
						+ " and "
						+ CleanUpConstants.class.getCanonicalName()
						+ "."
						+ stringFieldsByValue.get(constantValue).getName()
						+ " should not share the same value: "
						+ constantValue);

				stringFieldsByValue.put(constantValue, field);
			}
		}
	}

	@Test
	public void testAddNLSTag01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        String s= \"\";\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public String s1 = \"\";\n" //
				+ "    public void foo() {\n" //
				+ "        String s2 = \"\";\n" //
				+ "        String s3 = s2 + \"\";\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public static final String s= \"\";\n" //
				+ "    public static String bar(String s1, String s2) {\n" //
				+ "        bar(\"\", \"\");\n" //
				+ "        return \"\";\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.ADD_MISSING_NLS_TAGS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        String s= \"\"; //$NON-NLS-1$\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public String s1 = \"\"; //$NON-NLS-1$\n" //
				+ "    public void foo() {\n" //
				+ "        String s2 = \"\"; //$NON-NLS-1$\n" //
				+ "        String s3 = s2 + \"\"; //$NON-NLS-1$\n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public static final String s= \"\"; //$NON-NLS-1$\n" //
				+ "    public static String bar(String s1, String s2) {\n" //
				+ "        bar(\"\", \"\"); //$NON-NLS-1$ //$NON-NLS-2$\n" //
				+ "        return \"\"; //$NON-NLS-1$\n" //
				+ "    }\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testRemoveNLSTag01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        String s= null; //$NON-NLS-1$\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public String s1 = null; //$NON-NLS-1$\n" //
				+ "    public void foo() {\n" //
				+ "        String s2 = null; //$NON-NLS-1$\n" //
				+ "        String s3 = s2 + s2; //$NON-NLS-1$\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public static final String s= null; //$NON-NLS-1$\n" //
				+ "    public static String bar(String s1, String s2) {\n" //
				+ "        bar(s2, s1); //$NON-NLS-1$ //$NON-NLS-2$\n" //
				+ "        return s1; //$NON-NLS-1$\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_NLS_TAGS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        String s= null; \n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public String s1 = null; \n" //
				+ "    public void foo() {\n" //
				+ "        String s2 = null; \n" //
				+ "        String s3 = s2 + s2; \n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public static final String s= null; \n" //
				+ "    public static String bar(String s1, String s2) {\n" //
				+ "        bar(s2, s1); \n" //
				+ "        return s1; \n" //
				+ "    }\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testUnusedCode01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import java.util.List;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.*;\n" //
				+ "public class E2 {\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import java.util.HashMap;\n" //
				+ "import test1.E2;\n" //
				+ "import java.io.StringReader;\n" //
				+ "import java.util.HashMap;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_IMPORTS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testUnusedCode02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private void foo() {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private void foo() {}\n" //
				+ "    private void bar() {}\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    private class E3Inner {\n" //
				+ "        private void foo() {}\n" //
				+ "    }\n" //
				+ "    public void foo() {\n" //
				+ "        Runnable r= new Runnable() {\n" //
				+ "            public void run() {}\n" //
				+ "            private void foo() {};\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_METHODS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    private class E3Inner {\n" //
				+ "    }\n" //
				+ "    public void foo() {\n" //
				+ "        Runnable r= new Runnable() {\n" //
				+ "            public void run() {};\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testUnusedCode03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private E1(int i) {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public E2() {}\n" //
				+ "    private E2(int i) {}\n" //
				+ "    private E2(String s) {}\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    public class E3Inner {\n" //
				+ "        private E3Inner(int i) {}\n" //
				+ "    }\n" //
				+ "    private void foo() {\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_CONSTRUCTORS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private E1(int i) {}\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public E2() {}\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    public class E3Inner {\n" //
				+ "        private E3Inner(int i) {}\n" //
				+ "    }\n" //
				+ "    private void foo() {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testUnusedCode04() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private int i= 10;\n" //
				+ "    private int j= 10;\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    private int i;\n" //
				+ "    private int j;\n" //
				+ "    private void foo() {\n" //
				+ "        i= 10;\n" //
				+ "        i= 20;\n" //
				+ "        i= j;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_FELDS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    private int j;\n" //
				+ "    private void foo() {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testUnusedCode05() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private class E1Inner{}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private class E2Inner1 {}\n" //
				+ "    private class E2Inner2 {}\n" //
				+ "    public class E2Inner3 {}\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    public class E3Inner {\n" //
				+ "        private class E3InnerInner {}\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_TYPES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public class E2Inner3 {}\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    public class E3Inner {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testUnusedCode06() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        int i= 10;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public void foo() {\n" //
				+ "        int i= 10;\n" //
				+ "        int j= 10;\n" //
				+ "    }\n" //
				+ "    private void bar() {\n" //
				+ "        int i= 10;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    public class E3Inner {\n" //
				+ "        public void foo() {\n" //
				+ "            int i= 10;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void foo() {\n" //
				+ "        int i= 10;\n" //
				+ "        int j= i;\n" //
				+ "        j= 10;\n" //
				+ "        j= 20;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_LOCAL_VARIABLES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public void foo() {\n" //
				+ "    }\n" //
				+ "    private void bar() {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "public class E3 {\n" //
				+ "    public class E3Inner {\n" //
				+ "        public void foo() {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void foo() {\n" //
				+ "        int i= 10;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testUnusedCode07() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        int i= bar();\n" //
				+ "        int j= 1;\n" //
				+ "    }\n" //
				+ "    public int bar() {return 1;}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_LOCAL_VARIABLES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        bar();\n" //
				+ "    }\n" //
				+ "    public int bar() {return 1;}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnusedCode08() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i= bar();\n" //
				+ "    private int j= 1;\n" //
				+ "    public int bar() {return 1;}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_FELDS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i= bar();\n" //
				+ "    public int bar() {return 1;}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnusedCode09() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        int i= 1;\n" //
				+ "        i= bar();\n" //
				+ "        int j= 1;\n" //
				+ "        j= 1;\n" //
				+ "    }\n" //
				+ "    public int bar() {return 1;}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_LOCAL_VARIABLES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        bar();\n" //
				+ "    }\n" //
				+ "    public int bar() {return 1;}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnusedCode10() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i= 1;\n" //
				+ "    private int j= 1;\n" //
				+ "    public void foo() {\n" //
				+ "        i= bar();\n" //
				+ "        j= 1;\n" //
				+ "    }\n" //
				+ "    public int bar() {return 1;}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_FELDS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i= 1;\n" //
				+ "    public void foo() {\n" //
				+ "        i= bar();\n" //
				+ "    }\n" //
				+ "    public int bar() {return 1;}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnusedCode11() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1  {\n" //
				+ "    private void foo(String s) {\n" //
				+ "        String s1= (String)s;\n" //
				+ "        Object o= (Object)new Object();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    String s1;\n" //
				+ "    String s2= (String)s1;\n" //
				+ "    public void foo(Integer i) {\n" //
				+ "        Number n= (Number)i;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_CASTS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1  {\n" //
				+ "    private void foo(String s) {\n" //
				+ "        String s1= s;\n" //
				+ "        Object o= new Object();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    String s1;\n" //
				+ "    String s2= s1;\n" //
				+ "    public void foo(Integer i) {\n" //
				+ "        Number n= i;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2}, new String[] {expected1, expected2});
	}

	@Test
	public void testUnusedCodeBug123766() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1  {\n" //
				+ "    private int i,j;\n" //
				+ "    public void foo() {\n" //
				+ "        String s1,s2;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_FELDS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_LOCAL_VARIABLES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1  {\n" //
				+ "    public void foo() {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnusedCodeBug150853() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import foo.Bar;\n" //
				+ "public class E1 {}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_IMPORTS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnusedCodeBug173014_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "        void foo() {\n" //
				+ "                class Local {}\n" //
				+ "        }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_TYPES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "        void foo() {\n" //
				+ "        }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnusedCodeBug173014_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static void main(String[] args) {\n" //
				+ "        class Local {}\n" //
				+ "        class Local2 {\n" //
				+ "            class LMember {}\n" //
				+ "            class LMember2 extends Local2 {}\n" //
				+ "            LMember m;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_TYPES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static void main(String[] args) {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnusedCodeBug189394() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import java.util.Random;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        Random ran = new Random();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNUSED_CODE_LOCAL_VARIABLES);

		sample= "" //
				+ "package test1;\n" //
				+ "import java.util.Random;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        new Random();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testUnusedCodeBug335173_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //

				+ "package test1;\n" //
				+ "import java.util.Comparator;\n" //
				+ "\n" //
				+ "class IntComp implements Comparator<Integer> {\n" //
				+ "    public int compare(Integer o1, Integer o2) {\n" //
				+ "        return ((Integer) o1).intValue() - ((Integer) o2).intValue();\n" //
				+ "    }\n" //
				+ "}\n";

		ICompilationUnit cu1= pack1.createCompilationUnit("IntComp.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_CASTS);

		sample= "" //
				+ "package test1;\n" //
				+ "import java.util.Comparator;\n" //
				+ "\n" //
				+ "class IntComp implements Comparator<Integer> {\n" //
				+ "    public int compare(Integer o1, Integer o2) {\n" //
				+ "        return o1.intValue() - o2.intValue();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testUnusedCodeBug335173_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(Integer n) {\n" //
				+ "        int i = (((Integer) n)).intValue();\n" //
				+ "        foo(((Integer) n));\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_CASTS);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(Integer n) {\n" //
				+ "        int i = ((n)).intValue();\n" //
				+ "        foo((n));\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testUnusedCodeBug335173_3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(Integer n) {\n" //
				+ "        int i = ((Integer) (n)).intValue();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_CASTS);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(Integer n) {\n" //
				+ "        int i = (n).intValue();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testUnusedCodeBug371078_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //

				+ "package test1;\n" //
				+ "class E1 {\n" //
				+ "    public static Object create(final int a, final int b) {\n" //
				+ "        return (Double) ((double) (a * Math.pow(10, -b)));\n" //
				+ "    }\n" //
				+ "}\n";

		ICompilationUnit cu1= pack1.createCompilationUnit("IntComp.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_CASTS);

		sample= "" //
				+ "package test1;\n" //
				+ "class E1 {\n" //
				+ "    public static Object create(final int a, final int b) {\n" //
				+ "        return (a * Math.pow(10, -b));\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testUnusedCodeBug371078_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //

				+ "package test1;\n" //
				+ "public class NestedCasts {\n" //
				+ "	void foo(Integer i) {\n" //
				+ "		Object o= ((((Number) (((Integer) i)))));\n" //
				+ "	}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("NestedCasts.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_CASTS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class NestedCasts {\n" //
				+ "	void foo(Integer i) {\n" //
				+ "		Object o= (((((i)))));\n" //
				+ "	}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testJava5001() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    private int field= 1;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1{\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    private int field1= 1;\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    private int field2= 2;\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_DEPRECATED);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    private int field= 1;\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1{\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    private int field1= 1;\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    private int field2= 2;\n" //
				+ "}\n";
		String expected2= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2}, new String[] {expected1, expected2});
	}

	@Test
	public void testJava5002() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    private int f() {return 1;}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1{\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    private int f1() {return 1;}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    private int f2() {return 2;}\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_DEPRECATED);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    private int f() {return 1;}\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1{\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    private int f1() {return 1;}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    private int f2() {return 2;}\n" //
				+ "}\n";
		String expected2= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2}, new String[] {expected1, expected2});
	}

	@Test
	public void testJava5003() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "/**\n" //
				+ " * @deprecated\n" //
				+ " */\n" //
				+ "public class E1 {\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1{\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    private class E2Sub1 {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    private class E2Sub2 {}\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_DEPRECATED);

		sample= "" //
				+ "package test1;\n" //
				+ "/**\n" //
				+ " * @deprecated\n" //
				+ " */\n" //
				+ "@Deprecated\n" //
				+ "public class E1 {\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1{\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    private class E2Sub1 {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    private class E2Sub2 {}\n" //
				+ "}\n";
		String expected2= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2}, new String[] {expected1, expected2});
	}

	@Test
	public void testJava5004() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo1() {}\n" //
				+ "    protected void foo2() {}\n" //
				+ "    private void foo3() {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    public void foo1() {}\n" //
				+ "    protected void foo2() {}\n" //
				+ "    protected void foo3() {}\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public void foo1() {}\n" //
				+ "    protected void foo2() {}\n" //
				+ "    public void foo3() {}\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE_FOR_INTERFACE_METHOD_IMPLEMENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    @Override\n" //
				+ "    public void foo1() {}\n" //
				+ "    @Override\n" //
				+ "    protected void foo2() {}\n" //
				+ "    protected void foo3() {}\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    @Override\n" //
				+ "    public void foo1() {}\n" //
				+ "    @Override\n" //
				+ "    protected void foo2() {}\n" //
				+ "    @Override\n" //
				+ "    public void foo3() {}\n" //
				+ "}\n";
		String expected2= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {cu1.getBuffer().getContents(), expected1, expected2});
	}

	@Test
	public void testJava5005() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    public void foo1() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    protected void foo2() {}\n" //
				+ "    private void foo3() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    public int i;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    public void foo1() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    protected void foo2() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    protected void foo3() {}\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    public void foo1() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    protected void foo2() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    public void foo3() {}\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_DEPRECATED);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE_FOR_INTERFACE_METHOD_IMPLEMENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    public void foo1() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    protected void foo2() {}\n" //
				+ "    private void foo3() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    public int i;\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    @Override\n" //
				+ "    public void foo1() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    @Override\n" //
				+ "    protected void foo2() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    protected void foo3() {}\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    @Override\n" //
				+ "    public void foo1() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    @Override\n" //
				+ "    protected void foo2() {}\n" //
				+ "    /**\n" //
				+ "     * @deprecated\n" //
				+ "     */\n" //
				+ "    @Deprecated\n" //
				+ "    @Override\n" //
				+ "    public void foo3() {}\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testJava50Bug222257() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import java.util.ArrayList;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        ArrayList list= new ArrayList<>();\n" //
				+ "        ArrayList list2= new ArrayList<>();\n" //
				+ "        \n" //
				+ "        System.out.println(list);\n" //
				+ "        System.out.println(list2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		HashMap<String, String> map= new HashMap<>();
		map.put(CleanUpConstants.VARIABLE_DECLARATION_USE_TYPE_ARGUMENTS_FOR_RAW_TYPE_REFERENCES, CleanUpOptions.TRUE);
		Java50CleanUp cleanUp= new Java50CleanUp(map);

		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setResolveBindings(true);
		parser.setProject(getProject());

		Map<String, String> options= RefactoringASTParser.getCompilerOptions(getProject());
		options.putAll(cleanUp.getRequirements().getCompilerOptions());
		parser.setCompilerOptions(options);

		final CompilationUnit[] roots= new CompilationUnit[1];
		parser.createASTs(new ICompilationUnit[] { cu1 }, new String[0], new ASTRequestor() {
			@Override
			public void acceptAST(ICompilationUnit source, CompilationUnit ast) {
				roots[0]= ast;
			}
		}, null);

		IProblem[] problems= roots[0].getProblems();
		assertEquals(2, problems.length);
		for (IProblem problem : problems) {
			ProblemLocation location= new ProblemLocation(problem);
			assertTrue(cleanUp.canFix(cu1, location));
		}
	}

	@Test
	public void testCodeStyle01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    String s = \"\"; //$NON-NLS-1$\n" //
				+ "    String t = \"\";  //$NON-NLS-1$\n" //
				+ "    \n" //
				+ "    public void foo() {\n" //
				+ "        s = \"\"; //$NON-NLS-1$\n" //
				+ "        s = s + s;\n" //
				+ "        s = t + s;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1{\n" //
				+ "    int i = 10;\n" //
				+ "    \n" //
				+ "    public class E2Inner {\n" //
				+ "        public void bar() {\n" //
				+ "            int j = i;\n" //
				+ "            String k = s + t;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    \n" //
				+ "    public void fooBar() {\n" //
				+ "        String k = s;\n" //
				+ "        int j = i;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    String s = \"\"; //$NON-NLS-1$\n" //
				+ "    String t = \"\";  //$NON-NLS-1$\n" //
				+ "    \n" //
				+ "    public void foo() {\n" //
				+ "        this.s = \"\"; //$NON-NLS-1$\n" //
				+ "        this.s = this.s + this.s;\n" //
				+ "        this.s = this.t + this.s;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1{\n" //
				+ "    int i = 10;\n" //
				+ "    \n" //
				+ "    public class E2Inner {\n" //
				+ "        public void bar() {\n" //
				+ "            int j = E2.this.i;\n" //
				+ "            String k = E2.this.s + E2.this.t;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    \n" //
				+ "    public void fooBar() {\n" //
				+ "        String k = this.s;\n" //
				+ "        int j = this.i;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2}, new String[] {expected1, expected2});
	}

	@Test
	public void testCodeStyle02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static int i= 0;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private E1 e1;\n" //
				+ "    \n" //
				+ "    public void foo() {\n" //
				+ "        e1= new E1();\n" //
				+ "        int j= e1.i;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private E1 e1;\n" //
				+ "    \n" //
				+ "    public void foo() {\n" //
				+ "        this.e1= new E1();\n" //
				+ "        int j= E1.i;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2}, new String[] {cu1.getBuffer().getContents(), expected1});
	}

	@Test
	public void testCodeStyle03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static int f;\n" //
				+ "    public void foo() {\n" //
				+ "        int i= this.f;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public static String s = \"\";\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(this.s);\n" //
				+ "        E1 e1= new E1();\n" //
				+ "        int i= e1.f;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public static int g;\n" //
				+ "    {\n" //
				+ "        this.g= (new E1()).f;\n" //
				+ "    }\n" //
				+ "    public static int f= E1.f;\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static int f;\n" //
				+ "    public void foo() {\n" //
				+ "        int i= E1.f;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public static String s = \"\";\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E2.s);\n" //
				+ "        E1 e1= new E1();\n" //
				+ "        int i= E1.f;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public static int g;\n" //
				+ "    {\n" //
				+ "        E3.g= E1.f;\n" //
				+ "    }\n" //
				+ "    public static int f= E1.f;\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});
	}

	@Test
	public void testCodeStyle04() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static int f() {return 1;}\n" //
				+ "    public void foo() {\n" //
				+ "        int i= this.f();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public static String s() {return \"\";}\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(this.s());\n" //
				+ "        E1 e1= new E1();\n" //
				+ "        int i= e1.f();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public static int g;\n" //
				+ "    {\n" //
				+ "        this.g= (new E1()).f();\n" //
				+ "    }\n" //
				+ "    public static int f= E1.f();\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static int f() {return 1;}\n" //
				+ "    public void foo() {\n" //
				+ "        int i= E1.f();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    public static String s() {return \"\";}\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E2.s());\n" //
				+ "        E1 e1= new E1();\n" //
				+ "        int i= E1.f();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public static int g;\n" //
				+ "    {\n" //
				+ "        E3.g= E1.f();\n" //
				+ "    }\n" //
				+ "    public static int f= E1.f();\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});

	}

	@Test
	public void testCodeStyle05() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public String s= \"\";\n" //
				+ "    public E2 e2;\n" //
				+ "    public static int i= 10;\n" //
				+ "    public void foo() {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    public int i = 10;\n" //
				+ "    public E1 e1;\n" //
				+ "    public void fooBar() {}\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 {\n" //
				+ "    private E1 e1;    \n" //
				+ "    public void foo() {\n" //
				+ "        e1= new E1();\n" //
				+ "        int j= e1.i;\n" //
				+ "        String s= e1.s;\n" //
				+ "        e1.foo();\n" //
				+ "        e1.e2.fooBar();\n" //
				+ "        int k= e1.e2.e2.e2.i;\n" //
				+ "        int h= e1.e2.e2.e1.e2.e1.i;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 {\n" //
				+ "    private E1 e1;    \n" //
				+ "    public void foo() {\n" //
				+ "        this.e1= new E1();\n" //
				+ "        int j= E1.i;\n" //
				+ "        String s= this.e1.s;\n" //
				+ "        this.e1.foo();\n" //
				+ "        this.e1.e2.fooBar();\n" //
				+ "        int k= this.e1.e2.e2.e2.i;\n" //
				+ "        int h= E1.i;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {cu1.getBuffer().getContents(), cu2.getBuffer().getContents(), expected1});
	}

	@Test
	public void testCodeStyle06() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public String s= \"\";\n" //
				+ "    public E1 create() {\n" //
				+ "        return new E1();\n" //
				+ "    }\n" //
				+ "    public void foo() {\n" //
				+ "        create().s= \"\";\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testCodeStyle07() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static int i = 10;\n" //
				+ "    private static int j = i + 10 * i;\n" //
				+ "    public void foo() {\n" //
				+ "        String s= i + \"\";\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testCodeStyle08() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public final static int i = 1;\n" //
				+ "    public final int j = 2;\n" //
				+ "    private final int k = 3;\n" //
				+ "    public void foo() {\n" //
				+ "        switch (3) {\n" //
				+ "        case i: break;\n" //
				+ "        case j: break;\n" //
				+ "        case k: break;\n" //
				+ "        default: break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testCodeStyle09() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public abstract class E1Inner1 {\n" //
				+ "        protected int n;\n" //
				+ "        public abstract void foo();\n" //
				+ "    }\n" //
				+ "    public abstract class E1Inner2 {\n" //
				+ "        public abstract void run();\n" //
				+ "    }\n" //
				+ "    public void foo() {\n" //
				+ "        E1Inner1 inner= new E1Inner1() {\n" //
				+ "            public void foo() {\n" //
				+ "                E1Inner2 inner2= new E1Inner2() {\n" //
				+ "                    public void run() {\n" //
				+ "                        System.out.println(n);\n" //
				+ "                    }\n" //
				+ "                };\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testCodeStyle10() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private static final int N;\n" //
				+ "    static {N= 10;}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testCodeStyle11() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {    \n" //
				+ "    public static int E1N = 10;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1N);\n" //
				+ "        E1N = 10;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    public static int E2N = 10;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1N);\n" //
				+ "        E1N = 10;\n" //
				+ "        System.out.println(E2N);\n" //
				+ "        E2N = 10;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    private static int E3N = 10;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1N);\n" //
				+ "        E1N = 10;\n" //
				+ "        System.out.println(E2N);\n" //
				+ "        E2N = 10;\n" //
				+ "        System.out.println(E3N);\n" //
				+ "        E3N = 10;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {    \n" //
				+ "    public static int E1N = 10;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        E1.E1N = 10;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    public static int E2N = 10;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        E1.E1N = 10;\n" //
				+ "        System.out.println(E2.E2N);\n" //
				+ "        E2.E2N = 10;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    private static int E3N = 10;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        E1.E1N = 10;\n" //
				+ "        System.out.println(E2.E2N);\n" //
				+ "        E2.E2N = 10;\n" //
				+ "        System.out.println(E3.E3N);\n" //
				+ "        E3.E3N = 10;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected3= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {expected1, expected2, expected3});

	}

	@Test
	public void testCodeStyle12() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public final static int N1 = 10;\n" //
				+ "    public static int N2 = N1;\n" //
				+ "    {\n" //
				+ "        System.out.println(N1);\n" //
				+ "        N2 = 10;\n" //
				+ "        System.out.println(N2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public final static int N1 = 10;\n" //
				+ "    public static int N2 = E1.N1;\n" //
				+ "    {\n" //
				+ "        System.out.println(E1.N1);\n" //
				+ "        E1.N2 = 10;\n" //
				+ "        System.out.println(E1.N2);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle13() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private static class E1Inner {\n" //
				+ "        private static class E1InnerInner {\n" //
				+ "            public static int N = 10;\n" //
				+ "            static {\n" //
				+ "                System.out.println(N);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private static class E1Inner {\n" //
				+ "        private static class E1InnerInner {\n" //
				+ "            public static int N = 10;\n" //
				+ "            static {\n" //
				+ "                System.out.println(E1InnerInner.N);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle14() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    static class E1Inner {\n" //
				+ "        public static class E1InnerInner {\n" //
				+ "            public static int N = 10;\n" //
				+ "            public void foo() {\n" //
				+ "                System.out.println(N);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    static class E1Inner {\n" //
				+ "        public static class E1InnerInner {\n" //
				+ "            public static int N = 10;\n" //
				+ "            public void foo() {\n" //
				+ "                System.out.println(E1InnerInner.N);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle15() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    static class E1Inner {\n" //
				+ "        public static class E1InnerInner {\n" //
				+ "            public static int N = 10;\n" //
				+ "            public void foo() {\n" //
				+ "                System.out.println((new E1InnerInner()).N);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    static class E1Inner {\n" //
				+ "        public static class E1InnerInner {\n" //
				+ "            public static int N = 10;\n" //
				+ "            public void foo() {\n" //
				+ "                System.out.println(E1InnerInner.N);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle16() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static int E1N;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    public static int E2N = 10;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        System.out.println(E2.E1N);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        System.out.println(E2.E1N);\n" //
				+ "        System.out.println(E3.E1N);\n" //
				+ "        System.out.println(E2.E2N);\n" //
				+ "        System.out.println(E3.E2N);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu3= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_SUBTYPE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    public static int E2N = 10;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E1;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 extends E2 {\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        System.out.println(E1.E1N);\n" //
				+ "        System.out.println(E2.E2N);\n" //
				+ "        System.out.println(E2.E2N);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1, cu2, cu3}, new String[] {cu1.getBuffer().getContents(), expected1, expected2});

	}

	@Test
	public void testCodeStyle17() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b= true;\n" //
				+ "    public void foo() {\n" //
				+ "        if (b)\n" //
				+ "            System.out.println(10);\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(10);\n" //
				+ "        } else\n" //
				+ "            System.out.println(10);\n" //
				+ "        if (b)\n" //
				+ "            System.out.println(10);\n" //
				+ "        else\n" //
				+ "            System.out.println(10);\n" //
				+ "        while (b)\n" //
				+ "            System.out.println(10);\n" //
				+ "        do\n" //
				+ "            System.out.println(10);\n" //
				+ "        while (b);\n" //
				+ "        for(;;)\n" //
				+ "            System.out.println(10);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b= true;\n" //
				+ "    public void foo() {\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(10);\n" //
				+ "        }\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(10);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(10);\n" //
				+ "        }\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(10);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(10);\n" //
				+ "        }\n" //
				+ "        while (b) {\n" //
				+ "            System.out.println(10);\n" //
				+ "        }\n" //
				+ "        do {\n" //
				+ "            System.out.println(10);\n" //
				+ "        } while (b);\n" //
				+ "        for(;;) {\n" //
				+ "            System.out.println(10);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle18() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b, q;\n" //
				+ "    public void foo() {\n" //
				+ "        if (b)\n" //
				+ "            System.out.println(1);\n" //
				+ "        else if (q)\n" //
				+ "            System.out.println(1);\n" //
				+ "        else\n" //
				+ "            if (b && q)\n" //
				+ "                System.out.println(1);\n" //
				+ "            else\n" //
				+ "                System.out.println(2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b, q;\n" //
				+ "    public void foo() {\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(1);\n" //
				+ "        } else if (q) {\n" //
				+ "            System.out.println(1);\n" //
				+ "        } else\n" //
				+ "            if (b && q) {\n" //
				+ "                System.out.println(1);\n" //
				+ "            } else {\n" //
				+ "                System.out.println(2);\n" //
				+ "            }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle19() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b, q;\n" //
				+ "    public void foo() {\n" //
				+ "        for (;b;)\n" //
				+ "            for (;q;)\n" //
				+ "                if (b)\n" //
				+ "                    System.out.println(1);\n" //
				+ "                else if (q)\n" //
				+ "                    System.out.println(2);\n" //
				+ "                else\n" //
				+ "                    System.out.println(3);\n" //
				+ "        for (;b;)\n" //
				+ "            for (;q;) {\n" //
				+ "                \n" //
				+ "            }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b, q;\n" //
				+ "    public void foo() {\n" //
				+ "        for (;b;) {\n" //
				+ "            for (;q;) {\n" //
				+ "                if (b) {\n" //
				+ "                    System.out.println(1);\n" //
				+ "                } else if (q) {\n" //
				+ "                    System.out.println(2);\n" //
				+ "                } else {\n" //
				+ "                    System.out.println(3);\n" //
				+ "                }\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "        for (;b;) {\n" //
				+ "            for (;q;) {\n" //
				+ "                \n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle20() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b, q;\n" //
				+ "    public void foo() {\n" //
				+ "        while (b)\n" //
				+ "            while (q)\n" //
				+ "                if (b)\n" //
				+ "                    System.out.println(1);\n" //
				+ "                else if (q)\n" //
				+ "                    System.out.println(2);\n" //
				+ "                else\n" //
				+ "                    System.out.println(3);\n" //
				+ "        while (b)\n" //
				+ "            while (q) {\n" //
				+ "                \n" //
				+ "            }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b, q;\n" //
				+ "    public void foo() {\n" //
				+ "        while (b) {\n" //
				+ "            while (q) {\n" //
				+ "                if (b) {\n" //
				+ "                    System.out.println(1);\n" //
				+ "                } else if (q) {\n" //
				+ "                    System.out.println(2);\n" //
				+ "                } else {\n" //
				+ "                    System.out.println(3);\n" //
				+ "                }\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "        while (b) {\n" //
				+ "            while (q) {\n" //
				+ "                \n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle21() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b, q;\n" //
				+ "    public void foo() {\n" //
				+ "        do\n" //
				+ "            do\n" //
				+ "                if (b)\n" //
				+ "                    System.out.println(1);\n" //
				+ "                else if (q)\n" //
				+ "                    System.out.println(2);\n" //
				+ "                else\n" //
				+ "                    System.out.println(3);\n" //
				+ "            while (q);\n" //
				+ "        while (b);\n" //
				+ "        do\n" //
				+ "            do {\n" //
				+ "                \n" //
				+ "            } while (q);\n" //
				+ "        while (b);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public boolean b, q;\n" //
				+ "    public void foo() {\n" //
				+ "        do {\n" //
				+ "            do {\n" //
				+ "                if (b) {\n" //
				+ "                    System.out.println(1);\n" //
				+ "                } else if (q) {\n" //
				+ "                    System.out.println(2);\n" //
				+ "                } else {\n" //
				+ "                    System.out.println(3);\n" //
				+ "                }\n" //
				+ "            } while (q);\n" //
				+ "        } while (b);\n" //
				+ "        do {\n" //
				+ "            do {\n" //
				+ "                \n" //
				+ "            } while (q);\n" //
				+ "        } while (b);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle22() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import test2.I1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        I1 i1= new I1() {\n" //
				+ "            private static final int N= 10;\n" //
				+ "            public void foo() {\n" //
				+ "                System.out.println(N);\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "public interface I1 {}\n";
		pack2.createCompilationUnit("I1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testCodeStyle23() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int fNb= 0;\n" //
				+ "    public void foo() {\n" //
				+ "        if (true)\n" //
				+ "            fNb++;\n" //
				+ "        String s; //$NON-NLS-1$\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);
		enable(CleanUpConstants.REMOVE_UNNECESSARY_NLS_TAGS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int fNb= 0;\n" //
				+ "    public void foo() {\n" //
				+ "        if (true) {\n" //
				+ "            this.fNb++;\n" //
				+ "        }\n" //
				+ "        String s; \n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle24() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true)\n" //
				+ "            System.out.println(\"\");\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);
		enable(CleanUpConstants.REMOVE_UNNECESSARY_NLS_TAGS);
		enable(CleanUpConstants.ADD_MISSING_NLS_TAGS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true) {\n" //
				+ "            System.out.println(\"\"); //$NON-NLS-1$\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle25() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(I1Impl.N);\n" //
				+ "        I1 i1= new I1();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "import test2.I1;\n" //
				+ "public class I1Impl implements I1 {}\n";
		pack1.createCompilationUnit("I1Impl.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class I1 {}\n";
		pack1.createCompilationUnit("I1.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "public interface I1 {\n" //
				+ "    public static int N= 10;\n" //
				+ "}\n";
		pack2.createCompilationUnit("I1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_SUBTYPE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(test2.I1.N);\n" //
				+ "        I1 i1= new I1();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle26() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {}\n" //
				+ "    private void bar() {\n" //
				+ "        foo();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {}\n" //
				+ "    private void bar() {\n" //
				+ "        this.foo();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyle27() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static void foo() {}\n" //
				+ "    private void bar() {\n" //
				+ "        foo();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_METHOD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static void foo() {}\n" //
				+ "    private void bar() {\n" //
				+ "        E1.foo();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug118204() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    static String s;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(s);\n" //
				+ "    }\n" //
				+ "    E1(){}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    static String s;\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(E1.s);\n" //
				+ "    }\n" //
				+ "    E1(){}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug114544() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(new E1().i);\n" //
				+ "    }\n" //
				+ "    public static int i= 10;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        new E1();\n" //
				+ "        System.out.println(E1.i);\n" //
				+ "    }\n" //
				+ "    public static int i= 10;\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug119170_01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static void foo() {}\n" //
				+ "}\n";
		pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private static class E1 {}\n" //
				+ "    public void bar() {\n" //
				+ "        test1.E1 e1= new test1.E1();\n" //
				+ "        e1.foo();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private static class E1 {}\n" //
				+ "    public void bar() {\n" //
				+ "        test1.E1 e1= new test1.E1();\n" //
				+ "        test1.E1.foo();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug119170_02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public static void foo() {}\n" //
				+ "}\n";
		pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private static String E1= \"\";\n" //
				+ "    public void foo() {\n" //
				+ "        test1.E1 e1= new test1.E1();\n" //
				+ "        e1.foo();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 {\n" //
				+ "    private static String E1= \"\";\n" //
				+ "    public void foo() {\n" //
				+ "        test1.E1 e1= new test1.E1();\n" //
				+ "        test1.E1.foo();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug123468() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    protected int field;\n" //
				+ "}\n";
		pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    private int field;\n" //
				+ "    public void foo() {\n" //
				+ "        super.field= 10;\n" //
				+ "        field= 10;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E2.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 extends E1 {\n" //
				+ "    private int field;\n" //
				+ "    public void foo() {\n" //
				+ "        super.field= 10;\n" //
				+ "        this.field= 10;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug129115() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private static int NUMBER;\n" //
				+ "    public void reset() {\n" //
				+ "        NUMBER= 0;\n" //
				+ "    }\n" //
				+ "    enum MyEnum {\n" //
				+ "        STATE_1, STATE_2, STATE_3\n" //
				+ "      };\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private static int NUMBER;\n" //
				+ "    public void reset() {\n" //
				+ "        E1.NUMBER= 0;\n" //
				+ "    }\n" //
				+ "    enum MyEnum {\n" //
				+ "        STATE_1, STATE_2, STATE_3\n" //
				+ "      };\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug135219() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E { \n" //
				+ "    public int i;\n" //
				+ "    public void print(int j) {}\n" //
				+ "    public void foo() {\n" //
				+ "        print(i);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_ALWAYS);

		sample= "" //
				+ "package test;\n" //
				+ "public class E { \n" //
				+ "    public int i;\n" //
				+ "    public void print(int j) {}\n" //
				+ "    public void foo() {\n" //
				+ "        this.print(this.i);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug_138318() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E<I> {\n" //
				+ "    private static int I;\n" //
				+ "    private static String STR() {return \"\";}\n" //
				+ "    public void foo() {\n" //
				+ "        Runnable runnable = new Runnable() {\n" //
				+ "            public void run() {\n" //
				+ "                System.out.println(I);\n" //
				+ "                System.out.println(STR());\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_METHOD);

		sample= "" //
				+ "package test;\n" //
				+ "public class E<I> {\n" //
				+ "    private static int I;\n" //
				+ "    private static String STR() {return \"\";}\n" //
				+ "    public void foo() {\n" //
				+ "        Runnable runnable = new Runnable() {\n" //
				+ "            public void run() {\n" //
				+ "                System.out.println(E.I);\n" //
				+ "                System.out.println(E.STR());\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug138325_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E<I> {\n" //
				+ "    private int i;\n" //
				+ "    private String str() {return \"\";}\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(i);\n" //
				+ "        System.out.println(str());\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_ALWAYS);

		sample= "" //
				+ "package test;\n" //
				+ "public class E<I> {\n" //
				+ "    private int i;\n" //
				+ "    private String str() {return \"\";}\n" //
				+ "    public void foo() {\n" //
				+ "        System.out.println(this.i);\n" //
				+ "        System.out.println(this.str());\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug138325_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E<I> {\n" //
				+ "    private int i;\n" //
				+ "    private String str() {return \"\";}\n" //
				+ "    public void foo() {\n" //
				+ "        Runnable runnable = new Runnable() {\n" //
				+ "            public void run() {\n" //
				+ "                System.out.println(i);\n" //
				+ "                System.out.println(str());\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_ALWAYS);

		sample= "" //
				+ "package test;\n" //
				+ "public class E<I> {\n" //
				+ "    private int i;\n" //
				+ "    private String str() {return \"\";}\n" //
				+ "    public void foo() {\n" //
				+ "        Runnable runnable = new Runnable() {\n" //
				+ "            public void run() {\n" //
				+ "                System.out.println(E.this.i);\n" //
				+ "                System.out.println(E.this.str());\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleQualifyMethodAccessesImportConflictBug_552461() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "import static java.util.Date.parse;\n" //
				+ "\n" //
				+ "import java.sql.Date;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public Object addFullyQualifiedName(String dateText, Date sqlDate) {\n" //
				+ "        return parse(dateText);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_METHOD);

		sample= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "import static java.util.Date.parse;\n" //
				+ "\n" //
				+ "import java.sql.Date;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public Object addFullyQualifiedName(String dateText, Date sqlDate) {\n" //
				+ "        return java.util.Date.parse(dateText);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCodeStyleQualifyMethodAccessesAlreadyImportedBug_552461() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "import static java.util.Date.parse;\n" //
				+ "\n" //
				+ "import java.util.Date;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public Object addFullyQualifiedName(String dateText, Date sqlDate) {\n" //
				+ "        return parse(dateText);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_METHOD);

		sample= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "import static java.util.Date.parse;\n" //
				+ "\n" //
				+ "import java.util.Date;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public Object addFullyQualifiedName(String dateText, Date sqlDate) {\n" //
				+ "        return Date.parse(dateText);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCodeStyle_Bug140565() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import java.io.*;\n" //
				+ "public class E1 {\n" //
				+ "        static class ClassA {static ClassB B;}\n" //
				+ "        static class ClassB {static ClassC C;}\n" //
				+ "        static class ClassC {static ClassD D;}\n" //
				+ "        static class ClassD {}\n" //
				+ "\n" //
				+ "        public void foo() {\n" //
				+ "                ClassA.B.C.D.toString();\n" //
				+ "        }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_IMPORTS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "        static class ClassA {static ClassB B;}\n" //
				+ "        static class ClassB {static ClassC C;}\n" //
				+ "        static class ClassC {static ClassD D;}\n" //
				+ "        static class ClassD {}\n" //
				+ "\n" //
				+ "        public void foo() {\n" //
				+ "                ClassC.D.toString();\n" //
				+ "        }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug157480() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 extends ETop {\n" //
				+ "    public void bar(boolean b) {\n" //
				+ "        if (b == true && b || b) {}\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "class ETop {\n" //
				+ "    public void bar(boolean b) {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_ALWAYS);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS);
		enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 extends ETop {\n" //
				+ "    @Override\n" //
				+ "    public void bar(boolean b) {\n" //
				+ "        if (((b == true) && b) || b) {}\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "class ETop {\n" //
				+ "    public void bar(boolean b) {}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCodeStyleBug154787() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "interface E1 {String FOO = \"FOO\";}\n";
		pack1.createCompilationUnit("E1.java", sample, false, null);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E2 implements E1 {}\n";
		pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "import test1.E2;\n" //
				+ "public class E3 {\n" //
				+ "    public String foo() {\n" //
				+ "        return E2.FOO;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack2.createCompilationUnit("E3.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_SUBTYPE_ACCESS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testCodeStyleBug189398() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(Object o) {\n" //
				+ "        if (o != null)\n" //
				+ "            System.out.println(o);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NO_FOR_RETURN_AND_THROW);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(Object o) {\n" //
				+ "        if (o != null) {\n" //
				+ "            System.out.println(o);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCodeStyleBug238828_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field;\n" //
				+ "\n" //
				+ "    public String foo() {\n" //
				+ "        return \"Foo\" + field //MyComment\n" //
				+ "                    + field;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field;\n" //
				+ "\n" //
				+ "    public String foo() {\n" //
				+ "        return \"Foo\" + this.field //MyComment\n" //
				+ "                    + this.field;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCodeStyleBug238828_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private static int FIELD;\n" //
				+ "\n" //
				+ "    public String foo() {\n" //
				+ "        return \"Foo\" + FIELD //MyComment\n" //
				+ "                    + FIELD;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_FIELD);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private static int FIELD;\n" //
				+ "\n" //
				+ "    public String foo() {\n" //
				+ "        return \"Foo\" + E1.FIELD //MyComment\n" //
				+ "                    + E1.FIELD;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCodeStyleBug346230() throws Exception {
		IJavaProject project= JavaProjectHelper.createJavaProject("CleanUpTestProject", "bin");
		try {
			JavaProjectHelper.addRTJar16(project);
			IPackageFragmentRoot src= JavaProjectHelper.addSourceContainer(project, "src");
			IPackageFragment pack1= src.createPackageFragment("test1", false, null);

			String sample= "" //
					+ "package test1;\n" //
					+ "interface CinematicEvent {\n" //
					+ "    public void stop();\n" //
					+ "    public boolean internalUpdate();\n" //
					+ "}\n";
			ICompilationUnit cu1= pack1.createCompilationUnit("CinematicEvent.java", sample, false, null);

			sample= "" //
					+ "package test1;\n" //
					+ "abstract class E1 implements CinematicEvent {\n" //
					+ "\n" //
					+ "    protected PlayState playState = PlayState.Stopped;\n" //
					+ "    protected LoopMode loopMode = LoopMode.DontLoop;\n" //
					+ "\n" //
					+ "    public boolean internalUpdate() {\n" //
					+ "        return loopMode == loopMode.DontLoop;\n" //
					+ "    }\n" //
					+ "\n" //
					+ "    public void stop() {\n" //
					+ "    }\n" //
					+ "\n" //
					+ "    public void read() {\n" //
					+ "        Object ic= new Object();\n" //
					+ "        playState.toString();\n" //
					+ "    }\n" //
					+ "\n" //
					+ "    enum PlayState {\n" //
					+ "        Stopped\n" //
					+ "    }\n" //
					+ "    enum LoopMode {\n" //
					+ "        DontLoop\n" //
					+ "    }\n" //
					+ "}\n";
			ICompilationUnit cu2= pack1.createCompilationUnit("E1.java", sample, false, null);

			enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
			enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
			enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
			enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);
			enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
			enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);
			enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS);
			enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE);
			enable(CleanUpConstants.ADD_MISSING_ANNOTATIONS_OVERRIDE_FOR_INTERFACE_METHOD_IMPLEMENTATION);

			sample= "" //
					+ "package test1;\n" //
					+ "abstract class E1 implements CinematicEvent {\n" //
					+ "\n" //
					+ "    protected PlayState playState = PlayState.Stopped;\n" //
					+ "    protected LoopMode loopMode = LoopMode.DontLoop;\n" //
					+ "\n" //
					+ "    @Override\n" //
					+ "    public boolean internalUpdate() {\n" //
					+ "        return this.loopMode == LoopMode.DontLoop;\n" //
					+ "    }\n" //
					+ "\n" //
					+ "    @Override\n" //
					+ "    public void stop() {\n" //
					+ "    }\n" //
					+ "\n" //
					+ "    public void read() {\n" //
					+ "        final Object ic= new Object();\n" //
					+ "        this.playState.toString();\n" //
					+ "    }\n" //
					+ "\n" //
					+ "    enum PlayState {\n" //
					+ "        Stopped\n" //
					+ "    }\n" //
					+ "    enum LoopMode {\n" //
					+ "        DontLoop\n" //
					+ "    }\n" //
					+ "}\n";
			String expected1= sample;

			assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1, cu2 }, new String[] { cu1.getBuffer().getContents(), expected1 });
		} finally {
			JavaProjectHelper.delete(project);
		}

	}

	@Test
	public void testCodeStyle_StaticAccessThroughInstance_Bug307407() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private final String localString = new MyClass().getMyString();\n" //
				+ "    public static class MyClass {\n" //
				+ "        public static String getMyString() {\n" //
				+ "            return \"a\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testRemoveNonStaticQualifier_Bug219204_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void test() {\n" //
				+ "        E1 t = E1.bar().g().g().foo(E1.foo(null).bar()).bar();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static E1 foo(E1 t) {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static E1 bar() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private E1 g() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void test() {\n" //
				+ "        E1.bar().g().g();\n" //
				+ "        E1.foo(null);\n" //
				+ "        E1.foo(E1.bar());\n" //
				+ "        E1 t = E1.bar();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static E1 foo(E1 t) {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static E1 bar() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private E1 g() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveNonStaticQualifier_Bug219204_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void test() {\n" //
				+ "        while (true)\n" //
				+ "            new E1().bar1().bar2().bar3();\n" //
				+ "    }\n" //
				+ "    private static E1 bar1() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "    private static E1 bar2() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "    private static E1 bar3() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void test() {\n" //
				+ "        while (true) {\n" //
				+ "            new E1();\n" //
				+ "            E1.bar1();\n" //
				+ "            E1.bar2();\n" //
				+ "            E1.bar3();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    private static E1 bar1() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "    private static E1 bar2() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "    private static E1 bar3() {\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testChangeNonstaticAccessToStatic_Bug439733() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "class Singleton {\n" //
				+ "    public static String name = \"The Singleton\";\n" //
				+ "    public static Singleton instance = new Singleton();\n" //
				+ "    public static Singleton getInstance() {\n" //
				+ "        return instance;\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public static void main(String[] args) {\n" //
				+ "        System.out.println(Singleton.instance.name);\n" //
				+ "        System.out.println(Singleton.getInstance().name);\n" //
				+ "        System.out.println(Singleton.getInstance().getInstance().name);\n" //
				+ "        System.out.println(new Singleton().name);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS);
		enable(CleanUpConstants.MEMBER_ACCESSES_STATIC_QUALIFY_WITH_DECLARING_CLASS_INSTANCE_ACCESS);

		sample= "" //
				+ "package test1;\n" //
				+ "class Singleton {\n" //
				+ "    public static String name = \"The Singleton\";\n" //
				+ "    public static Singleton instance = new Singleton();\n" //
				+ "    public static Singleton getInstance() {\n" //
				+ "        return instance;\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public static void main(String[] args) {\n" //
				+ "        System.out.println(Singleton.name);\n" //
				+ "        Singleton.getInstance();\n" //
				+ "        System.out.println(Singleton.name);\n" //
				+ "        Singleton.getInstance();\n" //
				+ "        Singleton.getInstance();\n" //
				+ "        System.out.println(Singleton.name);\n" //
				+ "        new Singleton();\n" //
				+ "        System.out.println(Singleton.name);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCombination01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private int i= 10;\n" //
				+ "    private int j= 20;\n" //
				+ "    \n" //
				+ "    public void foo() {\n" //
				+ "        i= j;\n" //
				+ "        i= 20;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_FELDS);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private int j= 20;\n" //
				+ "    \n" //
				+ "    public void foo() {\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCombination02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true)\n" //
				+ "            System.out.println(\"\");\n" //
				+ "        if (true)\n" //
				+ "            System.out.println(\"\");\n" //
				+ "        if (true)\n" //
				+ "            System.out.println(\"\");\n" //
				+ "        System.out.println(\"\");\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);
		enable(CleanUpConstants.ADD_MISSING_NLS_TAGS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true) {\n" //
				+ "            System.out.println(\"\"); //$NON-NLS-1$\n" //
				+ "        }\n" //
				+ "        if (true) {\n" //
				+ "            System.out.println(\"\"); //$NON-NLS-1$\n" //
				+ "        }\n" //
				+ "        if (true) {\n" //
				+ "            System.out.println(\"\"); //$NON-NLS-1$\n" //
				+ "        }\n" //
				+ "        System.out.println(\"\"); //$NON-NLS-1$\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCombination03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;  \n" //
				+ "import java.util.Iterator;\n" //
				+ "import java.util.List;\n" //
				+ "public class E1  {\n" //
				+ "    private List<String> fList;\n" //
				+ "    public void foo() {\n" //
				+ "        for (Iterator<String> iter = fList.iterator(); iter.hasNext();) {\n" //
				+ "            String element = (String) iter.next();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);

		sample= "" //
				+ "package test1;  \n" //
				+ "import java.util.List;\n" //
				+ "public class E1  {\n" //
				+ "    private List<String> fList;\n" //
				+ "    public void foo() {\n" //
				+ "        for (String string : this.fList) {\n" //
				+ "            String element = (String) string;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testBug245254() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i= 0;\n" //
				+ "    void method() {\n" //
				+ "        if (true\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i= 0;\n" //
				+ "    void method() {\n" //
				+ "        if (true\n" //
				+ "    }\n" //
				+ "}\n";
		String expected= sample;
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected });
	}

	@Test
	public void testCombinationBug120585() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i= 0;\n" //
				+ "    void method() {\n" //
				+ "        int[] array= null;\n" //
				+ "        for (int i= 0; i < array.length; i++)\n" //
				+ "            System.out.println(array[i]);\n" //
				+ "        i= 12;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_MEMBERS);
		enable(CleanUpConstants.REMOVE_UNUSED_CODE_PRIVATE_FELDS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    void method() {\n" //
				+ "        int[] array= null;\n" //
				+ "        for (int element : array) {\n" //
				+ "            System.out.println(element);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCombinationBug125455() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1  {\n" //
				+ "    private void bar(boolean wait) {\n" //
				+ "        if (!wait) \n" //
				+ "            return;\n" //
				+ "    }\n" //
				+ "    private void foo(String s) {\n" //
				+ "        String s1= \"\";\n" //
				+ "        if (s.equals(\"\"))\n" //
				+ "            System.out.println();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);
		enable(CleanUpConstants.ADD_MISSING_NLS_TAGS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1  {\n" //
				+ "    private void bar(boolean wait) {\n" //
				+ "        if (!wait) {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    private void foo(String s) {\n" //
				+ "        String s1= \"\"; //$NON-NLS-1$\n" //
				+ "        if (s.equals(\"\")) { //$NON-NLS-1$\n" //
				+ "            System.out.println();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCombinationBug157468() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private void bar(boolean bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb) {\n" //
				+ "        if (bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb) { // a b c d e f g h i j k\n" //
				+ "            final String s = \"\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.FORMAT_SOURCE_CODE);

		Hashtable<String, String> options= TestOptions.getDefaultOptions();

		Map<String, String> formatterSettings= DefaultCodeFormatterConstants.getEclipseDefaultSettings();
		formatterSettings.put(DefaultCodeFormatterConstants.FORMATTER_COMMENT_COUNT_LINE_LENGTH_FROM_STARTING_POSITION,
				DefaultCodeFormatterConstants.FALSE);
		options.putAll(formatterSettings);

		JavaCore.setOptions(options);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "	private void bar(boolean bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb) {\n" //
				+ "		if (bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb) { // a b c d e f g\n" //
				+ "																// h i j k\n" //
				+ "			final String s = \"\";\n" //
				+ "		}\n" //
				+ "	}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCombinationBug234984_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void method(String[] arr) {\n" //
				+ "        for (int i = 0; i < arr.length; i++) {\n" //
				+ "            String item = arr[i];\n" //
				+ "            String item2 = item + \"a\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void method(String[] arr) {\n" //
				+ "        for (final String item : arr) {\n" //
				+ "            final String item2 = item + \"a\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCombinationBug234984_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import java.util.Iterator;\n" //
				+ "import java.util.List;\n" //
				+ "public class E1 {\n" //
				+ "    public void method(List<E1> es) {\n" //
				+ "        for (Iterator<E1> iterator = es.iterator(); iterator.hasNext();) {\n" //
				+ "            E1 next = iterator.next();\n" //
				+ "            next= new E1();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);

		sample= "" //
				+ "package test1;\n" //
				+ "import java.util.List;\n" //
				+ "public class E1 {\n" //
				+ "    public void method(List<E1> es) {\n" //
				+ "        for (E1 next : es) {\n" //
				+ "            next= new E1();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testKeepCommentOnReplacement() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean[] refactorBooleanArray() {\n" //
				+ "        boolean[] array = new boolean[10];\n" //
				+ "\n" //
				+ "        // Keep this comment\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = true;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.ARRAYS_FILL);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean[] refactorBooleanArray() {\n" //
				+ "        boolean[] array = new boolean[10];\n" //
				+ "\n" //
				+ "        // Keep this comment\n" //
				+ "        Arrays.fill(array, true);\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testKeepCommentOnRemoval() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    class A {\n" //
				+ "        A(int a) {}\n" //
				+ "\n" //
				+ "        A() {\n" //
				+ "            // Keep this comment\n" //
				+ "            super();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REDUNDANT_SUPER_CALL);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    class A {\n" //
				+ "        A(int a) {}\n" //
				+ "\n" //
				+ "        A() {\n" //
				+ "            // Keep this comment\n" //
				+ "            \n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.RedundantSuperCallCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testSubstring() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private String textInInstance = \"foo\";\n" //
				+ "\n" //
				+ "    public String reduceSubstring(String text) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return text.substring(2, text.length());\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String reduceSubstringOnField() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return textInInstance.substring(3, textInInstance.length());\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String reduceSubstringOnExpression(String text) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return (textInInstance + text).substring(4, (textInInstance + text).length());\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);

		enable(CleanUpConstants.SUBSTRING);

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private String textInInstance = \"foo\";\n" //
				+ "\n" //
				+ "    public String reduceSubstring(String text) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return text.substring(2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String reduceSubstringOnField() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return textInInstance.substring(3);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String reduceSubstringOnExpression(String text) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return (textInInstance + text).substring(4);\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.SubstringCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testKeepSubstring() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public String doNotReduceSubstringOnOtherExpression(String text) {\n" //
				+ "        return text.substring(5, text.hashCode());\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotReduceSubstringOnConstant(String text) {\n" //
				+ "        return text.substring(6, 123);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotReduceSubstringOnDifferentVariable(String text1, String text2) {\n" //
				+ "        return text1.substring(7, text2.length());\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotReduceSubstringOnActiveExpression(List<String> texts) {\n" //
				+ "        return texts.remove(0).substring(7, texts.remove(0).length());\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.SUBSTRING);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testUseArraysFill() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final boolean CONSTANT = true;\n" //
				+ "    private boolean[] booleanArray = new boolean[10];\n" //
				+ "\n" //
				+ "    public boolean[] refactorBooleanArray() {\n" //
				+ "        boolean[] array = new boolean[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = true;\n" //
				+ "        }\n" //
				+ "        for (int i = 0; i < array.length; ++i) {\n" //
				+ "            array[i] = false;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean[] refactorWithConstant() {\n" //
				+ "        boolean[] array = new boolean[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = Boolean.TRUE;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] refactorNumberArray() {\n" //
				+ "        int[] array = new int[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = 123;\n" //
				+ "        }\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = Integer.MAX_VALUE;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char[] refactorCharacterArray() {\n" //
				+ "        char[] array = new char[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = '!';\n" //
				+ "        }\n" //
				+ "        for (int j = 0; array.length > j; j++) {\n" //
				+ "            array[j] = 'z';\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String[] refactorStringArray() {\n" //
				+ "        String[] array = new String[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = \"foo\";\n" //
				+ "        }\n" //
				+ "        for (int j = 0; array.length > j; j++) {\n" //
				+ "            array[j] = null;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String[] refactorStringArrayWithLocalVar(String s) {\n" //
				+ "        String[] array = new String[10];\n" //
				+ "\n" //
				+ "        String var = \"foo\";\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = var;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = s;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String[] refactorArrayWithFinalField() {\n" //
				+ "        Boolean[] array = new Boolean[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = CONSTANT;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String[] refactorBackwardLoopOnArrary() {\n" //
				+ "        String[] array = new String[10];\n" //
				+ "\n" //
				+ "        for (int i = array.length - 1; i >= 0; i--) {\n" //
				+ "            array[i] = \"foo\";\n" //
				+ "        }\n" //
				+ "        for (int i = array.length - 1; 0 <= i; --i) {\n" //
				+ "            array[i] = \"foo\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorExternalArray() {\n" //
				+ "        for (int i = 0; i < booleanArray.length; i++) {\n" //
				+ "            booleanArray[i] = true;\n" //
				+ "        }\n" //
				+ "        for (int i = 0; i < this.booleanArray.length; i++) {\n" //
				+ "            this.booleanArray[i] = false;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.ARRAYS_FILL);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final boolean CONSTANT = true;\n" //
				+ "    private boolean[] booleanArray = new boolean[10];\n" //
				+ "\n" //
				+ "    public boolean[] refactorBooleanArray() {\n" //
				+ "        boolean[] array = new boolean[10];\n" //
				+ "\n" //
				+ "        Arrays.fill(array, true);\n" //
				+ "        Arrays.fill(array, false);\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean[] refactorWithConstant() {\n" //
				+ "        boolean[] array = new boolean[10];\n" //
				+ "\n" //
				+ "        Arrays.fill(array, Boolean.TRUE);\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] refactorNumberArray() {\n" //
				+ "        int[] array = new int[10];\n" //
				+ "\n" //
				+ "        Arrays.fill(array, 123);\n" //
				+ "        Arrays.fill(array, Integer.MAX_VALUE);\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char[] refactorCharacterArray() {\n" //
				+ "        char[] array = new char[10];\n" //
				+ "\n" //
				+ "        Arrays.fill(array, '!');\n" //
				+ "        Arrays.fill(array, 'z');\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String[] refactorStringArray() {\n" //
				+ "        String[] array = new String[10];\n" //
				+ "\n" //
				+ "        Arrays.fill(array, \"foo\");\n" //
				+ "        Arrays.fill(array, null);\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String[] refactorStringArrayWithLocalVar(String s) {\n" //
				+ "        String[] array = new String[10];\n" //
				+ "\n" //
				+ "        String var = \"foo\";\n" //
				+ "        Arrays.fill(array, var);\n" //
				+ "\n" //
				+ "        Arrays.fill(array, s);\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String[] refactorArrayWithFinalField() {\n" //
				+ "        Boolean[] array = new Boolean[10];\n" //
				+ "\n" //
				+ "        Arrays.fill(array, CONSTANT);\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String[] refactorBackwardLoopOnArrary() {\n" //
				+ "        String[] array = new String[10];\n" //
				+ "\n" //
				+ "        Arrays.fill(array, \"foo\");\n" //
				+ "        Arrays.fill(array, \"foo\");\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorExternalArray() {\n" //
				+ "        Arrays.fill(booleanArray, true);\n" //
				+ "        Arrays.fill(this.booleanArray, false);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { output });
	}

	@Test
	public void testDoNotUseArraysFill() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public int field = 4;\n" //
				+ "    private static int changingValue = 0;\n" //
				+ "    private final int CONSTANT = changingValue++;\n" //
				+ "    private E1[] arrayOfE1 = null;\n" //
				+ "\n" //
				+ "    public boolean[] doNotReplaceNonForEachLoop() {\n" //
				+ "        boolean[] array = new boolean[10];\n" //
				+ "\n" //
				+ "        for (int i = 1; i < array.length; i++) {\n" //
				+ "            array[i] = true;\n" //
				+ "        }\n" //
				+ "        for (int i = 0; i < array.length - 1; i++) {\n" //
				+ "            array[i] = false;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean[] doNotReplaceWierdLoop(int k) {\n" //
				+ "        boolean[] array = new boolean[10];\n" //
				+ "\n" //
				+ "        for (int m = 0; k++ < array.length; m++) {\n" //
				+ "            array[m] = true;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] doNotRefactorInitWithoutConstant(int n) {\n" //
				+ "        int[] array = new int[10];\n" //
				+ "\n" //
				+ "        for (int p = 0; p < array.length; p++) {\n" //
				+ "            array[p] = p*p;\n" //
				+ "        }\n" //
				+ "        for (int p = array.length - 1; p >= 0; p--) {\n" //
				+ "            array[p] = n++;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] doNotRefactorInitWithIndexVarOrNonFinalField(int q) {\n" //
				+ "        int[] array = new int[10];\n" //
				+ "\n" //
				+ "        for (int r = 0; r < array.length; r++) {\n" //
				+ "            array[r] = r;\n" //
				+ "        }\n" //
				+ "        for (int r = 0; r < array.length; r++) {\n" //
				+ "            array[r] = field;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] doNotRefactorCodeThatUsesIndex() {\n" //
				+ "        int[] array = new int[10];\n" //
				+ "\n" //
				+ "        for (int s = 0; s < array.length; s++) {\n" //
				+ "            array[s] = arrayOfE1[s].CONSTANT;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] doNotRefactorWithAnotherStatement() {\n" //
				+ "        int[] array = new int[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] = 123;\n" //
				+ "            System.out.println(\"Do not forget me!\");\n" //
				+ "        }\n" //
				+ "        for (int i = array.length - 1; i >= 0; i--) {\n" //
				+ "            System.out.println(\"Do not forget me!\");\n" //
				+ "            array[i] = 123;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] doNotRefactorWithSpecificIndex() {\n" //
				+ "        int[] array = new int[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[0] = 123;\n" //
				+ "        }\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[array.length - i] = 123;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] doNotRefactorAnotherArray(int[] array3) {\n" //
				+ "        int[] array = new int[10];\n" //
				+ "        int[] array2 = new int[10];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array2[i] = 123;\n" //
				+ "        }\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array3[i] = 123;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] doNotRefactorSpecialAssignment(int[] array) {\n" //
				+ "        for (int i = 0; i < array.length; i++) {\n" //
				+ "            array[i] += 123;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return array;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.ARRAYS_FILL);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testUseLazyLogicalOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithPrimitiveTypes(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 & b2;\n" //
				+ "        boolean newBoolean2 = b1 | b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithExtendedOperands(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 & b2 & b3;\n" //
				+ "        boolean newBoolean2 = b1 | b2 | b3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithWrappers(Boolean b1, Boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 & b2;\n" //
				+ "        boolean newBoolean2 = b1 | b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithIntegers(int i1, int i2) {\n" //
				+ "        int newInteger1 = i1 & i2;\n" //
				+ "        int newInteger2 = i1 | i2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithExpressions(int i1, int i2, int i3, int i4) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (i1 == i2) & (i3 != i4);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) | (i3 != i4);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithUnparentherizedExpressions(int i1, int i2, int i3, int i4, int i5, int i6) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = i1 == i2 & i3 != i4 & i5 != i6;\n" //
				+ "        boolean newBoolean2 = i1 == i2 | i3 != i4 | i5 != i6;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithMethods(List<String> myList) {\n" //
				+ "        boolean newBoolean1 = myList.remove(\"lorem\") & myList.remove(\"ipsum\");\n" //
				+ "        boolean newBoolean2 = myList.remove(\"lorem\") | myList.remove(\"ipsum\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithArrayAccess() {\n" //
				+ "        boolean[] booleans = new boolean[] {true, true};\n" //
				+ "        boolean newBoolean1 = booleans[0] & booleans[1] & booleans[2];\n" //
				+ "        boolean newBoolean2 = booleans[0] | booleans[1] | booleans[2];\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithDivision(int i1, int i2) {\n" //
				+ "        boolean newBoolean1 = (i1 == 123) & ((10 / i1) == i2);\n" //
				+ "        boolean newBoolean2 = (i1 == 123) | ((10 / i1) == i2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithMethodOnLeftOperand(List<String> myList, boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = myList.remove(\"lorem\") & b1 & b2;\n" //
				+ "        boolean newBoolean2 = myList.remove(\"lorem\") | b1 | b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithIncrements(int i1, int i2, int i3, int i4) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) & (i3 != i4++);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) & (i3 != ++i4);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) & (i3 != i4--);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) & (i3 != --i4);\n" //
				+ "\n" //
				+ "        boolean newBoolean5 = (i1 == i2) | (i3 != i4++);\n" //
				+ "        boolean newBoolean6 = (i1 == i2) | (i3 != ++i4);\n" //
				+ "        boolean newBoolean7 = (i1 == i2) | (i3 != i4--);\n" //
				+ "        boolean newBoolean8 = (i1 == i2) | (i3 != --i4);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithAssignments(int i1, int i2, boolean b1, boolean b2) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) & (b1 = b2);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) | (b1 = b2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private class SideEffect {\n" //
				+ "        private SideEffect() {\n" //
				+ "            staticField++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithInstanciations(Boolean b1) {\n" //
				+ "        boolean newBoolean1 = b1 & new SideEffect() instanceof SideEffect;\n" //
				+ "        boolean newBoolean2 = b1 | new SideEffect() instanceof SideEffect;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.USE_LAZY_LOGICAL_OPERATOR);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithPrimitiveTypes(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 && b2;\n" //
				+ "        boolean newBoolean2 = b1 || b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithExtendedOperands(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 && b2 && b3;\n" //
				+ "        boolean newBoolean2 = b1 || b2 || b3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithWrappers(Boolean b1, Boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 && b2;\n" //
				+ "        boolean newBoolean2 = b1 || b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithIntegers(int i1, int i2) {\n" //
				+ "        int newInteger1 = i1 & i2;\n" //
				+ "        int newInteger2 = i1 | i2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithExpressions(int i1, int i2, int i3, int i4) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (i1 == i2) && (i3 != i4);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) || (i3 != i4);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithUnparentherizedExpressions(int i1, int i2, int i3, int i4, int i5, int i6) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = i1 == i2 && i3 != i4 && i5 != i6;\n" //
				+ "        boolean newBoolean2 = i1 == i2 || i3 != i4 || i5 != i6;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithMethods(List<String> myList) {\n" //
				+ "        boolean newBoolean1 = myList.remove(\"lorem\") & myList.remove(\"ipsum\");\n" //
				+ "        boolean newBoolean2 = myList.remove(\"lorem\") | myList.remove(\"ipsum\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithArrayAccess() {\n" //
				+ "        boolean[] booleans = new boolean[] {true, true};\n" //
				+ "        boolean newBoolean1 = booleans[0] & booleans[1] & booleans[2];\n" //
				+ "        boolean newBoolean2 = booleans[0] | booleans[1] | booleans[2];\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithDivision(int i1, int i2) {\n" //
				+ "        boolean newBoolean1 = (i1 == 123) & ((10 / i1) == i2);\n" //
				+ "        boolean newBoolean2 = (i1 == 123) | ((10 / i1) == i2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceOperatorWithMethodOnLeftOperand(List<String> myList, boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = myList.remove(\"lorem\") && b1 && b2;\n" //
				+ "        boolean newBoolean2 = myList.remove(\"lorem\") || b1 || b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithIncrements(int i1, int i2, int i3, int i4) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) & (i3 != i4++);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) & (i3 != ++i4);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) & (i3 != i4--);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) & (i3 != --i4);\n" //
				+ "\n" //
				+ "        boolean newBoolean5 = (i1 == i2) | (i3 != i4++);\n" //
				+ "        boolean newBoolean6 = (i1 == i2) | (i3 != ++i4);\n" //
				+ "        boolean newBoolean7 = (i1 == i2) | (i3 != i4--);\n" //
				+ "        boolean newBoolean8 = (i1 == i2) | (i3 != --i4);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithAssignments(int i1, int i2, boolean b1, boolean b2) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) & (b1 = b2);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) | (b1 = b2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private class SideEffect {\n" //
				+ "        private SideEffect() {\n" //
				+ "            staticField++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceOperatorWithInstanciations(Boolean b1) {\n" //
				+ "        boolean newBoolean1 = b1 & new SideEffect() instanceof SideEffect;\n" //
				+ "        boolean newBoolean2 = b1 | new SideEffect() instanceof SideEffect;\n" //
				+ "    }\n" //
				+ "}\n";
		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.CodeStyleCleanUp_LazyLogical_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPrimitiveComparison() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int simplifyIntegerComparison(int number, int anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyDoubleComparison(double number, double anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Double.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyFloatComparison(float number, float anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Float.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyShortComparison(short number, short anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Short.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyLongComparison(long number, long anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Long.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyCharacterComparison(char number, char anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Character.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyByteComparison(byte number, byte anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Byte.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyBooleanComparison(boolean number, boolean anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Boolean.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorIntegerInstantiation(int number, int anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return new Integer(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorIntegerCast(int number, int anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return ((Integer) number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int simplifyIntegerComparison(int number, int anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyDoubleComparison(double number, double anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Double.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyFloatComparison(float number, float anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Float.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyShortComparison(short number, short anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Short.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyLongComparison(long number, long anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Long.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyCharacterComparison(char number, char anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Character.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyByteComparison(byte number, byte anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Byte.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int simplifyBooleanComparison(boolean number, boolean anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Boolean.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorIntegerInstantiation(int number, int anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorIntegerCast(int number, int anotherNumber) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.compare(number, anotherNumber);\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.PRIMITIVE_COMPARISON);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.PrimitiveComparisonCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUsePrimitiveComparison() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int doNotRefactorWrapper(Integer number, int anotherNumber) {\n" //
				+ "        return Integer.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorWrapperComparator(int number, Integer anotherNumber) {\n" //
				+ "        return Integer.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorString(String number, int anotherNumber) {\n" //
				+ "        return Integer.valueOf(number).compareTo(anotherNumber);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorBadMethod(int number, int anotherNumber) {\n" //
				+ "        return Integer.valueOf(number).valueOf(anotherNumber);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.PRIMITIVE_COMPARISON);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testPrimitiveParsing() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static void convertValueOfCallsToParseCallsInPrimitiveContext() {\n" //
				+ "        // Keep this comment\n" //
				+ "        byte by1 = Byte.valueOf(\"0\");\n" //
				+ "        byte by2 = Byte.valueOf(\"0\", 10);\n" //
				+ "        boolean bo = Boolean.valueOf(\"true\");\n" //
				+ "        int i1 = Integer.valueOf(\"42\");\n" //
				+ "        int i2 = Integer.valueOf(\"42\", 10);\n" //
				+ "        long l1 = Long.valueOf(\"42\");\n" //
				+ "        long l2 = Long.valueOf(\"42\", 10);\n" //
				+ "        short s1 = Short.valueOf(\"42\");\n" //
				+ "        short s2 = Short.valueOf(\"42\", 10);\n" //
				+ "        float f = Float.valueOf(\"42.42\");\n" //
				+ "        double d = Double.valueOf(\"42.42\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void removeUnnecessaryValueOfCallsInPrimitiveDeclaration() {\n" //
				+ "        // Keep this comment\n" //
				+ "        char c = Character.valueOf('&');\n" //
				+ "        byte by = Byte.valueOf((byte) 0);\n" //
				+ "        boolean bo = Boolean.valueOf(true);\n" //
				+ "        int i = Integer.valueOf(42);\n" //
				+ "        long l = Long.valueOf(42);\n" //
				+ "        short s = Short.valueOf((short) 42);\n" //
				+ "        float f = Float.valueOf(42.42F);\n" //
				+ "        double d = Double.valueOf(42.42);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void removeUnnecessaryValueOfCallsInPrimitiveAssignment() {\n" //
				+ "        // Keep this comment\n" //
				+ "        char c;\n" //
				+ "        c = Character.valueOf('&');\n" //
				+ "        byte by;\n" //
				+ "        by = Byte.valueOf((byte) 0);\n" //
				+ "        boolean bo1;\n" //
				+ "        bo1 = Boolean.valueOf(true);\n" //
				+ "        int i;\n" //
				+ "        i = Integer.valueOf(42);\n" //
				+ "        long l;\n" //
				+ "        l = Long.valueOf(42);\n" //
				+ "        short s;\n" //
				+ "        s = Short.valueOf((short) 42);\n" //
				+ "        float f;\n" //
				+ "        f = Float.valueOf(42.42F);\n" //
				+ "        double d;\n" //
				+ "        d = Double.valueOf(42.42);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static char removeUnnecessaryValueOfCallsInCharacterPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Character.valueOf('&');\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static byte removeUnnecessaryValueOfCallsInBytePrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Byte.valueOf((byte) 0);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static boolean removeUnnecessaryValueOfCallsInBooleanPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Boolean.valueOf(true);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static int removeUnnecessaryValueOfCallsInIntegerPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.valueOf(42);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static long removeUnnecessaryValueOfCallsInLongPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Long.valueOf(42);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static short removeUnnecessaryValueOfCallsInShortPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Short.valueOf((short) 42);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static float removeUnnecessaryValueOfCallsInFloatPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Float.valueOf(42.42F);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static double removeUnnecessaryValueOfCallsInDoublePrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Double.valueOf(42.42);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void removeUnnecessaryObjectCreation() {\n" //
				+ "        // Keep this comment\n" //
				+ "        new Byte(\"0\").byteValue();\n" //
				+ "        new Boolean(\"true\").booleanValue();\n" //
				+ "        new Integer(\"42\").intValue();\n" //
				+ "        new Short(\"42\").shortValue();\n" //
				+ "        new Long(\"42\").longValue();\n" //
				+ "        new Float(\"42.42\").floatValue();\n" //
				+ "        new Double(\"42.42\").doubleValue();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void removeUnnecessaryValueOfCalls() {\n" //
				+ "        // Keep this comment\n" //
				+ "        Byte.valueOf(\"0\").byteValue();\n" //
				+ "        Byte.valueOf(\"0\", 8).byteValue();\n" //
				+ "        Byte.valueOf(\"0\", 10).byteValue();\n" //
				+ "        Boolean.valueOf(\"true\").booleanValue();\n" //
				+ "        Integer.valueOf(\"42\").intValue();\n" //
				+ "        Integer.valueOf(\"42\", 8).intValue();\n" //
				+ "        Integer.valueOf(\"42\", 10).intValue();\n" //
				+ "        Short.valueOf(\"42\").shortValue();\n" //
				+ "        Short.valueOf(\"42\", 8).shortValue();\n" //
				+ "        Short.valueOf(\"42\", 10).shortValue();\n" //
				+ "        Long.valueOf(\"42\").longValue();\n" //
				+ "        Long.valueOf(\"42\", 8).longValue();\n" //
				+ "        Long.valueOf(\"42\", 10).longValue();\n" //
				+ "        Float.valueOf(\"42.42\").floatValue();\n" //
				+ "        Double.valueOf(\"42.42\").doubleValue();\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static void convertValueOfCallsToParseCallsInPrimitiveContext() {\n" //
				+ "        // Keep this comment\n" //
				+ "        byte by1 = Byte.parseByte(\"0\");\n" //
				+ "        byte by2 = Byte.parseByte(\"0\", 10);\n" //
				+ "        boolean bo = Boolean.parseBoolean(\"true\");\n" //
				+ "        int i1 = Integer.parseInt(\"42\");\n" //
				+ "        int i2 = Integer.parseInt(\"42\", 10);\n" //
				+ "        long l1 = Long.parseLong(\"42\");\n" //
				+ "        long l2 = Long.parseLong(\"42\", 10);\n" //
				+ "        short s1 = Short.parseShort(\"42\");\n" //
				+ "        short s2 = Short.parseShort(\"42\", 10);\n" //
				+ "        float f = Float.parseFloat(\"42.42\");\n" //
				+ "        double d = Double.parseDouble(\"42.42\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void removeUnnecessaryValueOfCallsInPrimitiveDeclaration() {\n" //
				+ "        // Keep this comment\n" //
				+ "        char c = '&';\n" //
				+ "        byte by = (byte) 0;\n" //
				+ "        boolean bo = true;\n" //
				+ "        int i = 42;\n" //
				+ "        long l = 42;\n" //
				+ "        short s = (short) 42;\n" //
				+ "        float f = 42.42F;\n" //
				+ "        double d = 42.42;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void removeUnnecessaryValueOfCallsInPrimitiveAssignment() {\n" //
				+ "        // Keep this comment\n" //
				+ "        char c;\n" //
				+ "        c = '&';\n" //
				+ "        byte by;\n" //
				+ "        by = (byte) 0;\n" //
				+ "        boolean bo1;\n" //
				+ "        bo1 = true;\n" //
				+ "        int i;\n" //
				+ "        i = 42;\n" //
				+ "        long l;\n" //
				+ "        l = 42;\n" //
				+ "        short s;\n" //
				+ "        s = (short) 42;\n" //
				+ "        float f;\n" //
				+ "        f = 42.42F;\n" //
				+ "        double d;\n" //
				+ "        d = 42.42;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static char removeUnnecessaryValueOfCallsInCharacterPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return '&';\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static byte removeUnnecessaryValueOfCallsInBytePrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return (byte) 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static boolean removeUnnecessaryValueOfCallsInBooleanPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return true;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static int removeUnnecessaryValueOfCallsInIntegerPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return 42;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static long removeUnnecessaryValueOfCallsInLongPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return 42;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static short removeUnnecessaryValueOfCallsInShortPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return (short) 42;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static float removeUnnecessaryValueOfCallsInFloatPrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return 42.42F;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static double removeUnnecessaryValueOfCallsInDoublePrimitive() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return 42.42;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void removeUnnecessaryObjectCreation() {\n" //
				+ "        // Keep this comment\n" //
				+ "        Byte.parseByte(\"0\");\n" //
				+ "        Boolean.parseBoolean(\"true\");\n" //
				+ "        Integer.parseInt(\"42\");\n" //
				+ "        Short.parseShort(\"42\");\n" //
				+ "        Long.parseLong(\"42\");\n" //
				+ "        Float.parseFloat(\"42.42\");\n" //
				+ "        Double.parseDouble(\"42.42\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void removeUnnecessaryValueOfCalls() {\n" //
				+ "        // Keep this comment\n" //
				+ "        Byte.parseByte(\"0\");\n" //
				+ "        Byte.parseByte(\"0\", 8);\n" //
				+ "        Byte.parseByte(\"0\", 10);\n" //
				+ "        Boolean.parseBoolean(\"true\");\n" //
				+ "        Integer.parseInt(\"42\");\n" //
				+ "        Integer.parseInt(\"42\", 8);\n" //
				+ "        Integer.parseInt(\"42\", 10);\n" //
				+ "        Short.parseShort(\"42\");\n" //
				+ "        Short.parseShort(\"42\", 8);\n" //
				+ "        Short.parseShort(\"42\", 10);\n" //
				+ "        Long.parseLong(\"42\");\n" //
				+ "        Long.parseLong(\"42\", 8);\n" //
				+ "        Long.parseLong(\"42\", 10);\n" //
				+ "        Float.parseFloat(\"42.42\");\n" //
				+ "        Double.parseDouble(\"42.42\");\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.PRIMITIVE_PARSING);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.PrimitiveParsingCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUsePrimitiveParsing() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static void doNotConvertToPrimitiveWithObjectUse() {\n" //
				+ "        Byte by1 = Byte.valueOf(\"0\");\n" //
				+ "        Byte by2 = Byte.valueOf(\"0\", 10);\n" //
				+ "        Boolean bo = Boolean.valueOf(\"true\");\n" //
				+ "        Integer i1 = Integer.valueOf(\"42\");\n" //
				+ "        Integer i2 = Integer.valueOf(\"42\", 10);\n" //
				+ "        Long l1 = Long.valueOf(\"42\");\n" //
				+ "        Long l2 = Long.valueOf(\"42\", 10);\n" //
				+ "        Short s1 = Short.valueOf(\"42\");\n" //
				+ "        Short s2 = Short.valueOf(\"42\", 10);\n" //
				+ "        Float f = Float.valueOf(\"42.42\");\n" //
				+ "        Double d = Double.valueOf(\"42.42\");\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.PRIMITIVE_PARSING);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testPrimitiveSerialization() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public String simplifyIntegerSerialization(int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyDoubleSerialization(double number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Double.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyFloatSerialization(float number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Float.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyShortSerialization(short number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Short.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyLongSerialization(long number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Long.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyCharacterSerialization(char number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Character.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyByteSerialization(byte number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Byte.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyBooleanSerialization(boolean number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Boolean.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorIntegerInstantiation(int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return new Integer(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorIntegerCast(int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return ((Integer) number).toString();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.PRIMITIVE_SERIALIZATION);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public String simplifyIntegerSerialization(int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyDoubleSerialization(double number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Double.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyFloatSerialization(float number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Float.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyShortSerialization(short number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Short.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyLongSerialization(long number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Long.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyCharacterSerialization(char number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Character.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyByteSerialization(byte number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Byte.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String simplifyBooleanSerialization(boolean number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Boolean.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorIntegerInstantiation(int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.toString(number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorIntegerCast(int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return Integer.toString(number);\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", input, output);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.PrimitiveSerializationCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotUsePrimitiveSerialization() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public String doNotRefactorWrapper(Integer number) {\n" //
				+ "        return Integer.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorString(String number) {\n" //
				+ "        return Integer.valueOf(number).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorBadMethod(int number) {\n" //
				+ "        return Integer.valueOf(number).toBinaryString(0);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.PRIMITIVE_SERIALIZATION);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testEvaluateNullable() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void removeUselessNullCheck(String s) {\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b1 = s != null && \"\".equals(s);\n" //
				+ "        boolean b2 = s != null && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b3 = s != null && s instanceof String;\n" //
				+ "\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b4 = null != s && \"\".equals(s);\n" //
				+ "        boolean b5 = null != s && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b6 = null != s && s instanceof String;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean removeExtendedNullCheck(boolean enabled, String s) {\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b1 = enabled && s != null && \"\".equals(s);\n" //
				+ "        boolean b2 = enabled && s != null && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b3 = enabled && s != null && s instanceof String;\n" //
				+ "\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b4 = enabled && null != s && \"\".equals(s);\n" //
				+ "        boolean b5 = enabled && null != s && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b6 = enabled && null != s && s instanceof String;\n" //
				+ "\n" //
				+ "        return b1 && b2 && b3 && b4 && b5 && b6;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean removeExtendedNullCheck(boolean enabled, boolean isValid, String s) {\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b1 = enabled && isValid && s != null && \"\".equals(s);\n" //
				+ "        boolean b2 = enabled && isValid && s != null && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b3 = enabled && isValid && s != null && s instanceof String;\n" //
				+ "\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b4 = enabled && isValid && null != s && \"\".equals(s);\n" //
				+ "        boolean b5 = enabled && isValid && null != s && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b6 = enabled && isValid && null != s && s instanceof String;\n" //
				+ "\n" //
				+ "        return b1 && b2 && b3 && b4 && b5 && b6;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean removeNullCheckInTheMiddle(boolean enabled, boolean isValid, String s) {\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b1 = enabled && s != null && \"\".equals(s) && isValid;\n" //
				+ "        boolean b2 = enabled && s != null && \"\".equalsIgnoreCase(s) && isValid;\n" //
				+ "        boolean b3 = enabled && s != null && s instanceof String && isValid;\n" //
				+ "\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b4 = enabled && null != s && \"\".equals(s) && isValid;\n" //
				+ "        boolean b5 = enabled && null != s && \"\".equalsIgnoreCase(s) && isValid;\n" //
				+ "        boolean b6 = enabled && null != s && s instanceof String && isValid;\n" //
				+ "\n" //
				+ "        return b1 && b2 && b3 && b4 && b5 && b6;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.EVALUATE_NULLABLE);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void removeUselessNullCheck(String s) {\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b1 = \"\".equals(s);\n" //
				+ "        boolean b2 = \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b3 = s instanceof String;\n" //
				+ "\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b4 = \"\".equals(s);\n" //
				+ "        boolean b5 = \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b6 = s instanceof String;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean removeExtendedNullCheck(boolean enabled, String s) {\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b1 = enabled && \"\".equals(s);\n" //
				+ "        boolean b2 = enabled && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b3 = enabled && s instanceof String;\n" //
				+ "\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b4 = enabled && \"\".equals(s);\n" //
				+ "        boolean b5 = enabled && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b6 = enabled && s instanceof String;\n" //
				+ "\n" //
				+ "        return b1 && b2 && b3 && b4 && b5 && b6;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean removeExtendedNullCheck(boolean enabled, boolean isValid, String s) {\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b1 = enabled && isValid && \"\".equals(s);\n" //
				+ "        boolean b2 = enabled && isValid && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b3 = enabled && isValid && s instanceof String;\n" //
				+ "\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b4 = enabled && isValid && \"\".equals(s);\n" //
				+ "        boolean b5 = enabled && isValid && \"\".equalsIgnoreCase(s);\n" //
				+ "        boolean b6 = enabled && isValid && s instanceof String;\n" //
				+ "\n" //
				+ "        return b1 && b2 && b3 && b4 && b5 && b6;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean removeNullCheckInTheMiddle(boolean enabled, boolean isValid, String s) {\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b1 = enabled && \"\".equals(s) && isValid;\n" //
				+ "        boolean b2 = enabled && \"\".equalsIgnoreCase(s) && isValid;\n" //
				+ "        boolean b3 = enabled && s instanceof String && isValid;\n" //
				+ "\n" //
				+ "        // Remove redundant null checks\n" //
				+ "        boolean b4 = enabled && \"\".equals(s) && isValid;\n" //
				+ "        boolean b5 = enabled && \"\".equalsIgnoreCase(s) && isValid;\n" //
				+ "        boolean b6 = enabled && s instanceof String && isValid;\n" //
				+ "\n" //
				+ "        return b1 && b2 && b3 && b4 && b5 && b6;\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", input, output);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.EvaluateNullableCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotEvaluateNullable() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final String NULL_CONSTANT = null;\n" //
				+ "\n" //
				+ "    public boolean doNotRemoveUselessNullCheckOnInstance(Object o) {\n" //
				+ "        return o != null && equals(o);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotRemoveUselessNullCheckOnThis(Object o) {\n" //
				+ "        return o != null && this.equals(o);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotRemoveNullCheck(String s) {\n" //
				+ "        // Do not remove non redundant null checks\n" //
				+ "        boolean b1 = s != null && s.equals(NULL_CONSTANT);\n" //
				+ "        boolean b2 = s != null && s.equalsIgnoreCase(NULL_CONSTANT);\n" //
				+ "\n" //
				+ "        // Do not remove non redundant null checks\n" //
				+ "        boolean b3 = null != s && s.equals(NULL_CONSTANT);\n" //
				+ "        boolean b4 = null != s && s.equalsIgnoreCase(NULL_CONSTANT);\n" //
				+ "\n" //
				+ "        return b1 && b2 && b3 && b4;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotRemoveNullCheckOnActiveExpression(List<String> texts) {\n" //
				+ "        boolean b1 = texts.remove(0) != null && \"foo\".equals(texts.remove(0));\n" //
				+ "        boolean b2 = texts.remove(0) != null && \"foo\".equalsIgnoreCase(texts.remove(0));\n" //
				+ "        boolean b3 = null != texts.remove(0) && \"foo\".equals(texts.remove(0));\n" //
				+ "        boolean b4 = null != texts.remove(0) && \"foo\".equalsIgnoreCase(texts.remove(0));\n" //
				+ "\n" //
				+ "        boolean b5 = texts.remove(0) != null && texts.remove(0) instanceof String;\n" //
				+ "        boolean b6 = null != texts.remove(0) && texts.remove(0) instanceof String;\n" //
				+ "\n" //
				+ "        return b1 && b2 && b3 && b4 && b5 && b6;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EVALUATE_NULLABLE);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testPushDownNegationReplaceDoubleNegation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void replaceDoubleNegation(boolean b) {\n" //
				+ "        boolean b1 = !!b;\n" //
				+ "        boolean b2 = !Boolean.TRUE;\n" //
				+ "        boolean b3 = !Boolean.FALSE;\n" //
				+ "        boolean b4 = !true;\n" //
				+ "        boolean b5 = !false;\n" //
				+ "        boolean b6 = !!!!b;\n" //
				+ "        boolean b7 = !!!!!Boolean.TRUE;\n" //
				+ "        boolean b8 = !!!!!!!Boolean.FALSE;\n" //
				+ "        boolean b9 = !!!!!!!!!true;\n" //
				+ "        boolean b10 = !!!false;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void replaceDoubleNegation(boolean b) {\n" //
				+ "        boolean b1 = b;\n" //
				+ "        boolean b2 = false;\n" //
				+ "        boolean b3 = true;\n" //
				+ "        boolean b4 = false;\n" //
				+ "        boolean b5 = true;\n" //
				+ "        boolean b6 = b;\n" //
				+ "        boolean b7 = false;\n" //
				+ "        boolean b8 = true;\n" //
				+ "        boolean b9 = false;\n" //
				+ "        boolean b10 = true;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceDoubleNegationWithParentheses() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceDoubleNegationWithParentheses(boolean b) {\n" //
				+ "        return !!!(!(b /* another refactoring removes the parentheses */));\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceDoubleNegationWithParentheses(boolean b) {\n" //
				+ "        return (b /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.PushDownNegationCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationWithInfixAndOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithInfixAndOperator(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        return !(b1 && b2 && b3); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithInfixAndOperator(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        return (!b1 || !b2 || !b3); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationWithInfixOrOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithInfixOrOperator(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        return !(b1 || b2 || b3); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithInfixOrOperator(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        return (!b1 && !b2 && !b3); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceInstanceofNegationWithInfixAndOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithInfixAndOperator(boolean b1, boolean b2) {\n" //
				+ "        return !(b1 && b2 instanceof String); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithInfixAndOperator(boolean b1, boolean b2) {\n" //
				+ "        return (!b1 || !(b2 instanceof String)); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceInstanceofNegationWithInfixOrOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithInfixOrOperator(boolean b1, boolean b2) {\n" //
				+ "        return !(b1 instanceof String || b2); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithInfixOrOperator(boolean b1, boolean b2) {\n" //
				+ "        return (!(b1 instanceof String) && !b2); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationWithEqualOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithEqualOperator(boolean b1, boolean b2) {\n" //
				+ "        return !(b1 == b2); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithEqualOperator(boolean b1, boolean b2) {\n" //
				+ "        return (b1 != b2); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationWithNotEqualOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithNotEqualOperator(boolean b1, boolean b2) {\n" //
				+ "        return !(b1 != b2); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationWithNotEqualOperator(boolean b1, boolean b2) {\n" //
				+ "        return (b1 == b2); // another refactoring removes the parentheses\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationRevertInnerExpressions() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationRevertInnerExpressions(boolean b1, boolean b2) {\n" //
				+ "        return !(!b1 && !b2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationRevertInnerExpressions(boolean b1, boolean b2) {\n" //
				+ "        return (b1 || b2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationLeaveParentheses() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationLeaveParentheses(boolean b1, boolean b2) {\n" //
				+ "        return !(!(b1 && b2 /* another refactoring removes the parentheses */));\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationLeaveParentheses(boolean b1, boolean b2) {\n" //
				+ "        return (b1 && b2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationRemoveParentheses() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationRemoveParentheses(boolean b1, boolean b2) {\n" //
				+ "        return !((!b1) && (!b2));\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationRemoveParentheses(boolean b1, boolean b2) {\n" //
				+ "        return (b1 || b2);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegateNonBooleanExprs() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegateNonBooleanExprs(Object o) {\n" //
				+ "        return !(o != null /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegateNonBooleanExprs(Object o) {\n" //
				+ "        return (o == null /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegateNonBooleanPrimitiveExprs() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegateNonBooleanPrimitiveExprs(Boolean b) {\n" //
				+ "        return !(b != null /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegateNonBooleanPrimitiveExprs(Boolean b) {\n" //
				+ "        return (b == null /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationAndLessOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndLessOperator(int i1, int i2) {\n" //
				+ "        return !(i1 < i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndLessOperator(int i1, int i2) {\n" //
				+ "        return (i1 >= i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationAndLessEqualOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndLessEqualOperator(int i1, int i2) {\n" //
				+ "        return !(i1 <= i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndLessEqualOperator(int i1, int i2) {\n" //
				+ "        return (i1 > i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationAndGreaterOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndGreaterOperator(int i1, int i2) {\n" //
				+ "        return !(i1 > i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndGreaterOperator(int i1, int i2) {\n" //
				+ "        return (i1 <= i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationAndGreaterEqualOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndGreaterEqualOperator(int i1, int i2) {\n" //
				+ "        return !(i1 >= i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndGreaterEqualOperator(int i1, int i2) {\n" //
				+ "        return (i1 < i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationAndEqualOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndEqualOperator(int i1, int i2) {\n" //
				+ "        return !(i1 == i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndEqualOperator(int i1, int i2) {\n" //
				+ "        return (i1 != i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testPushDownNegationReplaceNegationAndNotEqualOperator() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndNotEqualOperator(int i1, int i2) {\n" //
				+ "        return !(i1 != i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PUSH_DOWN_NEGATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean replaceNegationAndNotEqualOperator(int i1, int i2) {\n" //
				+ "        return (i1 == i2 /* another refactoring removes the parentheses */);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testOperandFactorization() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPrimitiveTypes(boolean repeatedBoolean, boolean isValid, boolean isActive) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = repeatedBoolean && isValid || repeatedBoolean && isActive;\n" //
				+ "        boolean newBoolean2 = repeatedBoolean && !isValid || repeatedBoolean && isActive;\n" //
				+ "        boolean newBoolean3 = repeatedBoolean && isValid || repeatedBoolean && !isActive;\n" //
				+ "        boolean newBoolean4 = repeatedBoolean && !isValid || repeatedBoolean && !isActive;\n" //
				+ "        boolean newBoolean5 = !repeatedBoolean && isValid || !repeatedBoolean && isActive;\n" //
				+ "        boolean newBoolean6 = !repeatedBoolean && !isValid || !repeatedBoolean && isActive;\n" //
				+ "        boolean newBoolean7 = !repeatedBoolean && isValid || !repeatedBoolean && !isActive;\n" //
				+ "        boolean newBoolean8 = !repeatedBoolean && !isValid || !repeatedBoolean && !isActive;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithEagerOperator(boolean repeatedBoolean, boolean isValid, boolean isEnable) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = repeatedBoolean & isValid | repeatedBoolean & isEnable;\n" //
				+ "        boolean newBoolean2 = repeatedBoolean & !isValid | repeatedBoolean & isEnable;\n" //
				+ "        boolean newBoolean3 = repeatedBoolean & isValid | repeatedBoolean & !isEnable;\n" //
				+ "        boolean newBoolean4 = repeatedBoolean & !isValid | repeatedBoolean & !isEnable;\n" //
				+ "        boolean newBoolean5 = !repeatedBoolean & isValid | !repeatedBoolean & isEnable;\n" //
				+ "        boolean newBoolean6 = !repeatedBoolean & !isValid | !repeatedBoolean & isEnable;\n" //
				+ "        boolean newBoolean7 = !repeatedBoolean & isValid | !repeatedBoolean & !isEnable;\n" //
				+ "        boolean newBoolean8 = !repeatedBoolean & !isValid | !repeatedBoolean & !isEnable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPermutedBooleans(boolean repeatedBoolean, boolean isValid, boolean isActive) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = repeatedBoolean && isValid || isActive && repeatedBoolean;\n" //
				+ "        boolean newBoolean2 = repeatedBoolean && !isValid || isActive && repeatedBoolean;\n" //
				+ "        boolean newBoolean3 = repeatedBoolean && isValid || !isActive && repeatedBoolean;\n" //
				+ "        boolean newBoolean4 = repeatedBoolean && !isValid || !isActive && repeatedBoolean;\n" //
				+ "        boolean newBoolean5 = !repeatedBoolean && isValid || isActive && !repeatedBoolean;\n" //
				+ "        boolean newBoolean6 = !repeatedBoolean && !isValid || isActive && !repeatedBoolean;\n" //
				+ "        boolean newBoolean7 = !repeatedBoolean && isValid || !isActive && !repeatedBoolean;\n" //
				+ "        boolean newBoolean8 = !repeatedBoolean && !isValid || !isActive && !repeatedBoolean;\n" //
				+ "\n" //
				+ "        newBoolean1 = isValid && repeatedBoolean || repeatedBoolean && isActive;\n" //
				+ "        newBoolean2 = !isValid && repeatedBoolean || repeatedBoolean && isActive;\n" //
				+ "        newBoolean3 = isValid && repeatedBoolean || repeatedBoolean && !isActive;\n" //
				+ "        newBoolean4 = !isValid && repeatedBoolean || repeatedBoolean && !isActive;\n" //
				+ "        newBoolean5 = !repeatedBoolean && isValid || !repeatedBoolean && isActive;\n" //
				+ "        newBoolean6 = !repeatedBoolean && !isValid || !repeatedBoolean && isActive;\n" //
				+ "        newBoolean7 = !repeatedBoolean && isValid || !repeatedBoolean && !isActive;\n" //
				+ "        newBoolean8 = !repeatedBoolean && !isValid || !repeatedBoolean && !isActive;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithExpressions(int i1, int i2, int i3, int i4, int i5, int i6) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (i1 == i2 * 2) && !(i3 == i4) || (i1 == 2 * i2 * 1) && (i5 == i6);\n" //
				+ "        boolean newBoolean2 = (i1 + 1 + 0 == i2) && (i3 == i4) || (1 + i1 == i2) && !(i5 == i6);\n" //
				+ "        boolean newBoolean3 = (i1 < i2) && (i3 == i4) || !(i1 >= i2) && !(i5 == i6);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int replaceBitwiseOperation(int i1, int i2, int i3) {\n" //
				+ "        return i1 & i2 | i1 & i3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char replaceCharBitwiseOperation(char c1, char c2, char c3) {\n" //
				+ "        return c1 & c2 | c1 & c3;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);

		enable(CleanUpConstants.OPERAND_FACTORIZATION);

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPrimitiveTypes(boolean repeatedBoolean, boolean isValid, boolean isActive) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (repeatedBoolean && (isValid || isActive));\n" //
				+ "        boolean newBoolean2 = (repeatedBoolean && (!isValid || isActive));\n" //
				+ "        boolean newBoolean3 = (repeatedBoolean && (isValid || !isActive));\n" //
				+ "        boolean newBoolean4 = (repeatedBoolean && (!isValid || !isActive));\n" //
				+ "        boolean newBoolean5 = (!repeatedBoolean && (isValid || isActive));\n" //
				+ "        boolean newBoolean6 = (!repeatedBoolean && (!isValid || isActive));\n" //
				+ "        boolean newBoolean7 = (!repeatedBoolean && (isValid || !isActive));\n" //
				+ "        boolean newBoolean8 = (!repeatedBoolean && (!isValid || !isActive));\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithEagerOperator(boolean repeatedBoolean, boolean isValid, boolean isEnable) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (repeatedBoolean & (isValid | isEnable));\n" //
				+ "        boolean newBoolean2 = (repeatedBoolean & (!isValid | isEnable));\n" //
				+ "        boolean newBoolean3 = (repeatedBoolean & (isValid | !isEnable));\n" //
				+ "        boolean newBoolean4 = (repeatedBoolean & (!isValid | !isEnable));\n" //
				+ "        boolean newBoolean5 = (!repeatedBoolean & (isValid | isEnable));\n" //
				+ "        boolean newBoolean6 = (!repeatedBoolean & (!isValid | isEnable));\n" //
				+ "        boolean newBoolean7 = (!repeatedBoolean & (isValid | !isEnable));\n" //
				+ "        boolean newBoolean8 = (!repeatedBoolean & (!isValid | !isEnable));\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPermutedBooleans(boolean repeatedBoolean, boolean isValid, boolean isActive) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (repeatedBoolean && (isValid || isActive));\n" //
				+ "        boolean newBoolean2 = (repeatedBoolean && (!isValid || isActive));\n" //
				+ "        boolean newBoolean3 = (repeatedBoolean && (isValid || !isActive));\n" //
				+ "        boolean newBoolean4 = (repeatedBoolean && (!isValid || !isActive));\n" //
				+ "        boolean newBoolean5 = (!repeatedBoolean && (isValid || isActive));\n" //
				+ "        boolean newBoolean6 = (!repeatedBoolean && (!isValid || isActive));\n" //
				+ "        boolean newBoolean7 = (!repeatedBoolean && (isValid || !isActive));\n" //
				+ "        boolean newBoolean8 = (!repeatedBoolean && (!isValid || !isActive));\n" //
				+ "\n" //
				+ "        newBoolean1 = (repeatedBoolean && (isValid || isActive));\n" //
				+ "        newBoolean2 = (repeatedBoolean && (!isValid || isActive));\n" //
				+ "        newBoolean3 = (repeatedBoolean && (isValid || !isActive));\n" //
				+ "        newBoolean4 = (repeatedBoolean && (!isValid || !isActive));\n" //
				+ "        newBoolean5 = (!repeatedBoolean && (isValid || isActive));\n" //
				+ "        newBoolean6 = (!repeatedBoolean && (!isValid || isActive));\n" //
				+ "        newBoolean7 = (!repeatedBoolean && (isValid || !isActive));\n" //
				+ "        newBoolean8 = (!repeatedBoolean && (!isValid || !isActive));\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithExpressions(int i1, int i2, int i3, int i4, int i5, int i6) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = ((i1 == i2 * 2) && (!(i3 == i4) || (i5 == i6)));\n" //
				+ "        boolean newBoolean2 = ((i1 + 1 + 0 == i2) && ((i3 == i4) || !(i5 == i6)));\n" //
				+ "        boolean newBoolean3 = ((i1 < i2) && ((i3 == i4) || !(i5 == i6)));\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int replaceBitwiseOperation(int i1, int i2, int i3) {\n" //
				+ "        return (i1 & (i2 | i3));\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char replaceCharBitwiseOperation(char c1, char c2, char c3) {\n" //
				+ "        return (c1 & (c2 | c3));\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.OperandFactorizationCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUseOperandFactorization() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public boolean doNoRefactorFailingCode(boolean b1, boolean[] b2, boolean b3) {\n" //
				+ "        return b2[-1] && b1 || b3 && b1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNoReplaceDuplicateConditionsWithOtherCondition(boolean b1, boolean b2, boolean b3, boolean b4) {\n" //
				+ "        return b1 && b2 || b1 && b3 && b4;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNoReplaceDuplicateConditionsWithOtherOperandBefore(boolean b1, boolean b2, boolean b3, boolean unrelevantCondition) {\n" //
				+ "        boolean newBoolean1 = unrelevantCondition || (b1 && b2) || (!b1 && b3);\n" //
				+ "        boolean newBoolean2 = unrelevantCondition || (b1 && !b2) || (b3 && !b1);\n" //
				+ "        boolean newBoolean3 = unrelevantCondition || (b1 && b2) || (!b3 && !b1);\n" //
				+ "        boolean newBoolean4 = unrelevantCondition || (b1 && !b2) || (!b3 && !b1);\n" //
				+ "        boolean newBoolean5 = unrelevantCondition || (!b1 && b2) || (b3 && b1);\n" //
				+ "        boolean newBoolean6 = unrelevantCondition || (!b1 && !b2) || (b3 && b1);\n" //
				+ "        boolean newBoolean7 = unrelevantCondition || (!b1 && b2) || (!b3 && b1);\n" //
				+ "        boolean newBoolean8 = unrelevantCondition || (!b1 && !b2) || (!b3 && b1);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNoReplaceDuplicateConditionsWithOtherOperandAfter(boolean b1, boolean b2, boolean b3, boolean unrelevantCondition) {\n" //
				+ "        boolean newBoolean1 = (b1 && b2) || (!b1 && b3) || unrelevantCondition;\n" //
				+ "        boolean newBoolean2 = (b1 && !b2) || (b3 && !b1) || unrelevantCondition;\n" //
				+ "        boolean newBoolean3 = (b1 && b2) || (!b3 && !b1) || unrelevantCondition;\n" //
				+ "        boolean newBoolean4 = (b1 && !b2) || (!b3 && !b1) || unrelevantCondition;\n" //
				+ "        boolean newBoolean5 = (!b1 && b2) || (b3 && b1) || unrelevantCondition;\n" //
				+ "        boolean newBoolean6 = (!b1 && !b2) || (b3 && b1) || unrelevantCondition;\n" //
				+ "        boolean newBoolean7 = (!b1 && b2) || (!b3 && b1) || unrelevantCondition;\n" //
				+ "        boolean newBoolean8 = (!b1 && !b2) || (!b3 && b1) || unrelevantCondition;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNoReplaceDuplicateConditionsWithWrappers(Boolean b1, Boolean b2, Boolean b3) {\n" //
				+ "        return b1 && b2 || b1 && b3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithMethods(List<String> myList) {\n" //
				+ "        boolean newBoolean1 = myList.remove(\"lorem\") && !myList.remove(\"foo\") || myList.remove(\"lorem\")\n" //
				+ "                && myList.remove(\"ipsum\");\n" //
				+ "        boolean newBoolean2 = myList.remove(\"lorem\") && myList.remove(\"bar\") || myList.remove(\"lorem\")\n" //
				+ "                && !myList.remove(\"ipsum\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithIncrements(int i1, int i2, int i3, int i4, int i5, int i6) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) && !(i3 == i4++) || (i1 == i2) && (i5 == i6++);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) && !(i3 == ++i4) || (i1 == i2) && (i5 == ++i6);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) && !(i3 == i4--) || (i1 == i2) && (i5 == i6--);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) && !(i3 == --i4) || (i1 == i2) && (i5 == --i6);\n" //
				+ "\n" //
				+ "        boolean newBoolean5 = (i1 == i2) && (i3 == i4++) || (i1 == i2) && !(i5 == i6++);\n" //
				+ "        boolean newBoolean6 = (i1 == i2) && (i3 == ++i4) || (i1 == i2) && !(i5 == ++i6);\n" //
				+ "        boolean newBoolean7 = (i1 == i2) && (i3 == i4--) || (i1 == i2) && !(i5 == i6--);\n" //
				+ "        boolean newBoolean8 = (i1 == i2) && (i3 == --i4) || (i1 == i2) && !(i5 == --i6);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithAssignments(int i1, int i2, boolean b1, boolean b2, boolean b3) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) && !(b1 = b2) || (i1 == i2) && (b1 = b3);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) && (b1 = b2) || (i1 == i2) && !(b1 = b3);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private class SideEffect {\n" //
				+ "        private SideEffect() {\n" //
				+ "            staticField++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithInstanciations(Boolean b1) {\n" //
				+ "        boolean newBoolean1 = b1 && !(new SideEffect() instanceof SideEffect)\n" //
				+ "                || b1 && new SideEffect() instanceof Object;\n" //
				+ "        boolean newBoolean2 = b1 && new SideEffect() instanceof SideEffect\n" //
				+ "                || b1 && !(new SideEffect() instanceof Object);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.OPERAND_FACTORIZATION);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testTernaryOperator() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void replaceDuplicateConditionsWithPrimitiveTypes(boolean isValid, boolean b2, boolean b3) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = isValid && b2 || !isValid && b3;\n" //
				+ "        boolean newBoolean2 = isValid && !b2 || !isValid && b3;\n" //
				+ "        boolean newBoolean3 = isValid && b2 || !isValid && !b3;\n" //
				+ "        boolean newBoolean4 = isValid && !b2 || !isValid && !b3;\n" //
				+ "        boolean newBoolean5 = !isValid && b2 || isValid && b3;\n" //
				+ "        boolean newBoolean6 = !isValid && !b2 || isValid && b3;\n" //
				+ "        boolean newBoolean7 = !isValid && b2 || isValid && !b3;\n" //
				+ "        boolean newBoolean8 = !isValid && !b2 || isValid && !b3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithEagerOperator(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 & b2 | !b1 & b3;\n" //
				+ "        boolean newBoolean2 = b1 & !b2 | !b1 & b3;\n" //
				+ "        boolean newBoolean3 = b1 & b2 | !b1 & !b3;\n" //
				+ "        boolean newBoolean4 = b1 & !b2 | !b1 & !b3;\n" //
				+ "        boolean newBoolean5 = !b1 & b2 | b1 & b3;\n" //
				+ "        boolean newBoolean6 = !b1 & !b2 | b1 & b3;\n" //
				+ "        boolean newBoolean7 = !b1 & b2 | b1 & !b3;\n" //
				+ "        boolean newBoolean8 = !b1 & !b2 | b1 & !b3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPermutedBooleans(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 && b2 || b3 && !b1;\n" //
				+ "        boolean newBoolean2 = b1 && !b2 || b3 && !b1;\n" //
				+ "        boolean newBoolean3 = b1 && b2 || !b3 && !b1;\n" //
				+ "        boolean newBoolean4 = b1 && !b2 || !b3 && !b1;\n" //
				+ "        boolean newBoolean5 = !b1 && b2 || b3 && b1;\n" //
				+ "        boolean newBoolean6 = !b1 && !b2 || b3 && b1;\n" //
				+ "        boolean newBoolean7 = !b1 && b2 || !b3 && b1;\n" //
				+ "        boolean newBoolean8 = !b1 && !b2 || !b3 && b1;\n" //
				+ "\n" //
				+ "        newBoolean1 = b2 && b1 || !b1 && b3;\n" //
				+ "        newBoolean2 = !b2 && b1 || !b1 && b3;\n" //
				+ "        newBoolean3 = b2 && b1 || !b1 && !b3;\n" //
				+ "        newBoolean4 = !b2 && b1 || !b1 && !b3;\n" //
				+ "        newBoolean5 = !b1 && b2 || b1 && b3;\n" //
				+ "        newBoolean6 = !b1 && !b2 || b1 && b3;\n" //
				+ "        newBoolean7 = !b1 && b2 || b1 && !b3;\n" //
				+ "        newBoolean8 = !b1 && !b2 || b1 && !b3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithOtherCondition(boolean b1, boolean b2, boolean b3, boolean unrevelantCondition) {\n" //
				+ "        boolean newBoolean1 = unrevelantCondition || (b1 && b2) || (!b1 && b3);\n" //
				+ "        boolean newBoolean2 = unrevelantCondition || (b1 && !b2) || (b3 && !b1);\n" //
				+ "        boolean newBoolean3 = unrevelantCondition || (b1 && b2) || (!b3 && !b1);\n" //
				+ "        boolean newBoolean4 = unrevelantCondition || (b1 && !b2) || (!b3 && !b1);\n" //
				+ "        boolean newBoolean5 = unrevelantCondition || (!b1 && b2) || (b3 && b1);\n" //
				+ "        boolean newBoolean6 = unrevelantCondition || (!b1 && !b2) || (b3 && b1);\n" //
				+ "        boolean newBoolean7 = unrevelantCondition || (!b1 && b2) || (!b3 && b1);\n" //
				+ "        boolean newBoolean8 = unrevelantCondition || (!b1 && !b2) || (!b3 && b1);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithOtherConditionAfter(boolean b1, boolean b2, boolean b3, boolean unrevelantCondition) {\n" //
				+ "        boolean newBoolean1 = (b1 && b2) || (!b1 && b3) || unrevelantCondition;\n" //
				+ "        boolean newBoolean2 = (b1 && !b2) || (b3 && !b1) || unrevelantCondition;\n" //
				+ "        boolean newBoolean3 = (b1 && b2) || (!b3 && !b1) || unrevelantCondition;\n" //
				+ "        boolean newBoolean4 = (b1 && !b2) || (!b3 && !b1) || unrevelantCondition;\n" //
				+ "        boolean newBoolean5 = (!b1 && b2) || (b3 && b1) || unrevelantCondition;\n" //
				+ "        boolean newBoolean6 = (!b1 && !b2) || (b3 && b1) || unrevelantCondition;\n" //
				+ "        boolean newBoolean7 = (!b1 && b2) || (!b3 && b1) || unrevelantCondition;\n" //
				+ "        boolean newBoolean8 = (!b1 && !b2) || (!b3 && b1) || unrevelantCondition;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithExpressions(int i1, int i2, int i3, int i4, int i5, int i6) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (i1 == i2 * 2) && !(i3 == i4) || !(i1 == 2 * i2 * 1) && (i5 == i6);\n" //
				+ "        boolean newBoolean2 = (i1 + 1 + 0 == i2) && (i3 == i4) || !(1 + i1 == i2) && !(i5 == i6);\n" //
				+ "        boolean newBoolean3 = (i1 < i2) && (i3 == i4) || (i1 >= i2) && !(i5 == i6);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);

		enable(CleanUpConstants.TERNARY_OPERATOR);

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void replaceDuplicateConditionsWithPrimitiveTypes(boolean isValid, boolean b2, boolean b3) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (isValid ? b2 : b3);\n" //
				+ "        boolean newBoolean2 = (isValid ? !b2 : b3);\n" //
				+ "        boolean newBoolean3 = (isValid ? b2 : !b3);\n" //
				+ "        boolean newBoolean4 = (isValid ? !b2 : !b3);\n" //
				+ "        boolean newBoolean5 = (isValid ? b3 : b2);\n" //
				+ "        boolean newBoolean6 = (isValid ? b3 : !b2);\n" //
				+ "        boolean newBoolean7 = (isValid ? !b3 : b2);\n" //
				+ "        boolean newBoolean8 = (isValid ? !b3 : !b2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithEagerOperator(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (b1 ? b2 : b3);\n" //
				+ "        boolean newBoolean2 = (b1 ? !b2 : b3);\n" //
				+ "        boolean newBoolean3 = (b1 ? b2 : !b3);\n" //
				+ "        boolean newBoolean4 = (b1 ? !b2 : !b3);\n" //
				+ "        boolean newBoolean5 = (b1 ? b3 : b2);\n" //
				+ "        boolean newBoolean6 = (b1 ? b3 : !b2);\n" //
				+ "        boolean newBoolean7 = (b1 ? !b3 : b2);\n" //
				+ "        boolean newBoolean8 = (b1 ? !b3 : !b2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPermutedBooleans(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (b1 ? b2 : b3);\n" //
				+ "        boolean newBoolean2 = (b1 ? !b2 : b3);\n" //
				+ "        boolean newBoolean3 = (b1 ? b2 : !b3);\n" //
				+ "        boolean newBoolean4 = (b1 ? !b2 : !b3);\n" //
				+ "        boolean newBoolean5 = (b1 ? b3 : b2);\n" //
				+ "        boolean newBoolean6 = (b1 ? b3 : !b2);\n" //
				+ "        boolean newBoolean7 = (b1 ? !b3 : b2);\n" //
				+ "        boolean newBoolean8 = (b1 ? !b3 : !b2);\n" //
				+ "\n" //
				+ "        newBoolean1 = (b1 ? b2 : b3);\n" //
				+ "        newBoolean2 = (b1 ? !b2 : b3);\n" //
				+ "        newBoolean3 = (b1 ? b2 : !b3);\n" //
				+ "        newBoolean4 = (b1 ? !b2 : !b3);\n" //
				+ "        newBoolean5 = (b1 ? b3 : b2);\n" //
				+ "        newBoolean6 = (b1 ? b3 : !b2);\n" //
				+ "        newBoolean7 = (b1 ? !b3 : b2);\n" //
				+ "        newBoolean8 = (b1 ? !b3 : !b2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithOtherCondition(boolean b1, boolean b2, boolean b3, boolean unrevelantCondition) {\n" //
				+ "        boolean newBoolean1 = unrevelantCondition || (b1 ? b2 : b3);\n" //
				+ "        boolean newBoolean2 = unrevelantCondition || (b1 ? !b2 : b3);\n" //
				+ "        boolean newBoolean3 = unrevelantCondition || (b1 ? b2 : !b3);\n" //
				+ "        boolean newBoolean4 = unrevelantCondition || (b1 ? !b2 : !b3);\n" //
				+ "        boolean newBoolean5 = unrevelantCondition || (b1 ? b3 : b2);\n" //
				+ "        boolean newBoolean6 = unrevelantCondition || (b1 ? b3 : !b2);\n" //
				+ "        boolean newBoolean7 = unrevelantCondition || (b1 ? !b3 : b2);\n" //
				+ "        boolean newBoolean8 = unrevelantCondition || (b1 ? !b3 : !b2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithOtherConditionAfter(boolean b1, boolean b2, boolean b3, boolean unrevelantCondition) {\n" //
				+ "        boolean newBoolean1 = (b1 ? b2 : b3) || unrevelantCondition;\n" //
				+ "        boolean newBoolean2 = (b1 ? !b2 : b3) || unrevelantCondition;\n" //
				+ "        boolean newBoolean3 = (b1 ? b2 : !b3) || unrevelantCondition;\n" //
				+ "        boolean newBoolean4 = (b1 ? !b2 : !b3) || unrevelantCondition;\n" //
				+ "        boolean newBoolean5 = (b1 ? b3 : b2) || unrevelantCondition;\n" //
				+ "        boolean newBoolean6 = (b1 ? b3 : !b2) || unrevelantCondition;\n" //
				+ "        boolean newBoolean7 = (b1 ? !b3 : b2) || unrevelantCondition;\n" //
				+ "        boolean newBoolean8 = (b1 ? !b3 : !b2) || unrevelantCondition;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithExpressions(int i1, int i2, int i3, int i4, int i5, int i6) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = ((i1 == i2 * 2) ? !(i3 == i4) : (i5 == i6));\n" //
				+ "        boolean newBoolean2 = ((i1 + 1 + 0 == i2) ? (i3 == i4) : !(i5 == i6));\n" //
				+ "        boolean newBoolean3 = ((i1 < i2) ? (i3 == i4) : !(i5 == i6));\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.TernaryOperatorCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUseTernaryOperator() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public void doNoReplaceDuplicateConditionsWithOtherCondition(boolean b1, boolean b2, boolean b3, boolean b4) {\n" //
				+ "        boolean newBoolean1 = b1 && b2 || !b1 && b3 && b4;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNoUseTernaryOperatorWithSameExpressions(boolean b1, int number) {\n" //
				+ "        boolean newBoolean1 = b1 && (number > 0) || !b1 && (0 < number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNoUseTernaryOperatorWithNegativeExpressions(boolean b1, int number) {\n" //
				+ "        boolean newBoolean1 = b1 && (number > 0) || !b1 && (0 >= number);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNoReplaceDuplicateConditionsWithWrappers(Boolean b1, Boolean b2, Boolean b3) {\n" //
				+ "        boolean newBoolean1 = b1 && b2 || !b1 && b3;\n" //
				+ "        boolean newBoolean2 = b1 && !b2 || !b1 && b3;\n" //
				+ "        boolean newBoolean3 = b1 && b2 || !b1 && !b3;\n" //
				+ "        boolean newBoolean4 = b1 && !b2 || !b1 && !b3;\n" //
				+ "        boolean newBoolean5 = !b1 && b2 || b1 && b3;\n" //
				+ "        boolean newBoolean6 = !b1 && !b2 || b1 && b3;\n" //
				+ "        boolean newBoolean7 = !b1 && b2 || b1 && !b3;\n" //
				+ "        boolean newBoolean8 = !b1 && !b2 || b1 && !b3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithMethods(List<String> myList) {\n" //
				+ "        boolean newBoolean1 = myList.remove(\"foo\") && !myList.remove(\"bar\") || !myList.remove(\"lorem\")\n" //
				+ "                && myList.remove(\"ipsum\");\n" //
				+ "        boolean newBoolean2 = myList.remove(\"foo\") && myList.remove(\"bar\") || !myList.remove(\"lorem\")\n" //
				+ "                && !myList.remove(\"ipsum\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithIncrements(int i1, int i2, int i3, int i4, int i5, int i6) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) && !(i3 == i4++) || !(i1 == i2) && (i5 == i6++);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) && !(i3 == ++i4) || !(i1 == i2) && (i5 == ++i6);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) && !(i3 == i4--) || !(i1 == i2) && (i5 == i6--);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) && !(i3 == --i4) || !(i1 == i2) && (i5 == --i6);\n" //
				+ "\n" //
				+ "        boolean newBoolean5 = (i1 == i2) && (i3 == i4++) || !(i1 == i2) && !(i5 == i6++);\n" //
				+ "        boolean newBoolean6 = (i1 == i2) && (i3 == ++i4) || !(i1 == i2) && !(i5 == ++i6);\n" //
				+ "        boolean newBoolean7 = (i1 == i2) && (i3 == i4--) || !(i1 == i2) && !(i5 == i6--);\n" //
				+ "        boolean newBoolean8 = (i1 == i2) && (i3 == --i4) || !(i1 == i2) && !(i5 == --i6);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithAssignments(int i1, int i2, boolean b1, boolean b2, boolean b3) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) && !(b1 = b2) || !(i1 == i2) && (b1 = b3);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) && (b1 = b2) || !(i1 == i2) && !(b1 = b3);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private class SideEffect {\n" //
				+ "        private SideEffect() {\n" //
				+ "            staticField++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithInstanciations(Boolean b1) {\n" //
				+ "        boolean newBoolean1 = b1 && !(new SideEffect() instanceof SideEffect)\n" //
				+ "                || !b1 && new SideEffect() instanceof Object;\n" //
				+ "        boolean newBoolean2 = b1 && new SideEffect() instanceof SideEffect\n" //
				+ "                || !b1 && !(new SideEffect() instanceof Object);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.TERNARY_OPERATOR);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testStrictlyEqualOrDifferent() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithEagerOperator(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 & !b2 | !b1 & b2;\n" //
				+ "        boolean newBoolean2 = b1 & b2 | !b1 & !b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPrimitiveTypes(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 && !b2 || !b1 && b2;\n" //
				+ "        boolean newBoolean2 = b1 && b2 || !b1 && !b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPermutedBooleans(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 && !b2 || b2 && !b1;\n" //
				+ "        boolean newBoolean2 = b1 && b2 || !b2 && !b1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithWrappers(Boolean b1, Boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 && !b2 || !b1 && b2;\n" //
				+ "        boolean newBoolean2 = b1 && b2 || !b1 && !b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithExpressions(int i1, int i2, int i3, int i4) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (i1 == i2) && !(i3 == i4) || !(i2 == i1) && (i3 == i4);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) && (i3 <= i4) || !(i1 == i2) && !(i4 >= i3);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) && (i3 != i4) || (i2 != i1) && (i3 == i4);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) && (i3 < i4) || (i1 != i2) && (i4 <= i3);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithFields() {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (staticField > 0) && (staticField < 100) || (staticField <= 0) && (staticField >= 100);\n" //
				+ "        boolean newBoolean2 = (staticField > 0) && (staticField < 100) || (staticField >= 100) && !(staticField > 0);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceTernaryWithPrimitiveTypes(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 ? !b2 : b2;\n" //
				+ "        boolean newBoolean2 = b1 ? b2 : !b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceTernaryWithExpressions(int i1, int i2, int i3, int i4) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (i1 == i2) ? !(i3 == i4) : (i3 == i4);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) ? (i3 <= i4) : !(i4 >= i3);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) ? (i3 != i4) : (i3 == i4);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) ? (i3 < i4) : (i4 <= i3);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceTernaryWithFields() {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (staticField > 0) ? (staticField < 100) : (staticField >= 100);\n" //
				+ "        boolean newBoolean2 = (staticField > 0) ? (staticField < 100) : !(staticField < 100);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);

		enable(CleanUpConstants.STRICTLY_EQUAL_OR_DIFFERENT);

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithEagerOperator(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 ^ b2;\n" //
				+ "        boolean newBoolean2 = b1 == b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPrimitiveTypes(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 ^ b2;\n" //
				+ "        boolean newBoolean2 = b1 == b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithPermutedBooleans(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 ^ b2;\n" //
				+ "        boolean newBoolean2 = b1 == b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithWrappers(Boolean b1, Boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 ^ b2;\n" //
				+ "        boolean newBoolean2 = b1 == b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithExpressions(int i1, int i2, int i3, int i4) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (i1 == i2) ^ (i3 == i4);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) == (i3 <= i4);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) == (i3 != i4);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) == (i3 < i4);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceDuplicateConditionsWithFields() {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (staticField > 0) == (staticField < 100);\n" //
				+ "        boolean newBoolean2 = (staticField > 0) == (staticField < 100);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceTernaryWithPrimitiveTypes(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = b1 ^ b2;\n" //
				+ "        boolean newBoolean2 = b1 == b2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceTernaryWithExpressions(int i1, int i2, int i3, int i4) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (i1 == i2) ^ (i3 == i4);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) == (i3 <= i4);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) == (i3 != i4);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) == (i3 < i4);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceTernaryWithFields() {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean newBoolean1 = (staticField > 0) == (staticField < 100);\n" //
				+ "        boolean newBoolean2 = (staticField > 0) == (staticField < 100);\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.StrictlyEqualOrDifferentCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUseStrictlyEqualOrDifferent() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static int staticField = 0;\n" //
				+ "\n" //
				+ "    public void doNoReplaceDuplicateConditionsWithOtherCondition(boolean b1, boolean b2, boolean b3) {\n" //
				+ "        boolean newBoolean1 = b1 && !b2 || !b1 && b2 && b3;\n" //
				+ "        boolean newBoolean2 = b1 && b2 || !b1 && !b2 && b3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithMethods(List<String> myList) {\n" //
				+ "        boolean newBoolean1 = myList.remove(\"lorem\") && !myList.remove(\"ipsum\") || !myList.remove(\"lorem\")\n" //
				+ "                && myList.remove(\"ipsum\");\n" //
				+ "        boolean newBoolean2 = myList.remove(\"lorem\") && myList.remove(\"ipsum\") || !myList.remove(\"lorem\")\n" //
				+ "                && !myList.remove(\"ipsum\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithIncrements(int i1, int i2, int i3, int i4) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) && !(i3 == i4++) || !(i1 == i2) && (i3 == i4++);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) && !(i3 == ++i4) || !(i1 == i2) && (i3 == ++i4);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) && !(i3 == i4--) || !(i1 == i2) && (i3 == i4--);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) && !(i3 == --i4) || !(i1 == i2) && (i3 == --i4);\n" //
				+ "\n" //
				+ "        boolean newBoolean5 = (i1 == i2) && (i3 == i4++) || !(i1 == i2) && !(i3 == i4++);\n" //
				+ "        boolean newBoolean6 = (i1 == i2) && (i3 == ++i4) || !(i1 == i2) && !(i3 == ++i4);\n" //
				+ "        boolean newBoolean7 = (i1 == i2) && (i3 == i4--) || !(i1 == i2) && !(i3 == i4--);\n" //
				+ "        boolean newBoolean8 = (i1 == i2) && (i3 == --i4) || !(i1 == i2) && !(i3 == --i4);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithAssignments(int i1, int i2, boolean b1, boolean b2) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) && !(b1 = b2) || !(i1 == i2) && (b1 = b2);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) && (b1 = b2) || !(i1 == i2) && !(b1 = b2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private class SideEffect {\n" //
				+ "        private SideEffect() {\n" //
				+ "            staticField++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceDuplicateConditionsWithInstanciations(Boolean b1) {\n" //
				+ "        boolean newBoolean1 = b1 && !(new SideEffect() instanceof SideEffect)\n" //
				+ "                || !b1 && new SideEffect() instanceof SideEffect;\n" //
				+ "        boolean newBoolean2 = b1 && new SideEffect() instanceof SideEffect\n" //
				+ "                || !b1 && !(new SideEffect() instanceof SideEffect);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotReplaceNullableObjects(Boolean booleanObject1, Boolean booleanObject2) {\n" //
				+ "        return booleanObject1 ? booleanObject2 : !booleanObject2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceTernaryWithIncrements(int i1, int i2, int i3, int i4) {\n" //
				+ "        boolean newBoolean1 = (i1 == i2) ? !(i3 == i4++) : (i3 == i4++);\n" //
				+ "        boolean newBoolean2 = (i1 == i2) ? !(i3 == ++i4) : (i3 == ++i4);\n" //
				+ "        boolean newBoolean3 = (i1 == i2) ? !(i3 == i4--) : (i3 == i4--);\n" //
				+ "        boolean newBoolean4 = (i1 == i2) ? !(i3 == --i4) : (i3 == --i4);\n" //
				+ "\n" //
				+ "        boolean newBoolean5 = (i1 == i2) ? (i3 == i4++) : !(i3 == i4++);\n" //
				+ "        boolean newBoolean6 = (i1 == i2) ? (i3 == ++i4) : !(i3 == ++i4);\n" //
				+ "        boolean newBoolean7 = (i1 == i2) ? (i3 == i4--) : !(i3 == i4--);\n" //
				+ "        boolean newBoolean8 = (i1 == i2) ? (i3 == --i4) : !(i3 == --i4);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.STRICTLY_EQUAL_OR_DIFFERENT);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testDoubleNegation() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public boolean reduceBooleanExpression(boolean b1, boolean b2) {\n" //
				+ "        boolean b3 = !b1 == !b2;\n" //
				+ "        boolean b4 = !b1 != !b2;\n" //
				+ "        boolean b5 = !b1 ^ !b2;\n" //
				+ "        boolean b6 = !b1 == b2;\n" //
				+ "        boolean b7 = !b1 != b2;\n" //
				+ "        boolean b8 = !b1 ^ b2;\n" //
				+ "        boolean b9 = b1 == !b2;\n" //
				+ "        boolean b10 = b1 != !b2;\n" //
				+ "        boolean b11 = b1 ^ !b2;\n" //
				+ "        return b3 && b4 && b5 && b6 && b7 && b8 && b9 && b10 && b11;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.DOUBLE_NEGATION);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public boolean reduceBooleanExpression(boolean b1, boolean b2) {\n" //
				+ "        boolean b3 = b1 == b2;\n" //
				+ "        boolean b4 = b1 ^ b2;\n" //
				+ "        boolean b5 = b1 ^ b2;\n" //
				+ "        boolean b6 = b1 ^ b2;\n" //
				+ "        boolean b7 = b1 == b2;\n" //
				+ "        boolean b8 = b1 == b2;\n" //
				+ "        boolean b9 = b1 ^ b2;\n" //
				+ "        boolean b10 = b1 == b2;\n" //
				+ "        boolean b11 = b1 == b2;\n" //
				+ "        return b3 && b4 && b5 && b6 && b7 && b8 && b9 && b10 && b11;\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", input, output);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.DoubleNegationCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotRemoveDoubleNegation() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public boolean doNotRefactorPositiveExpression(boolean isValid, boolean isEnabled) {\n" //
				+ "        boolean b1 = isValid == isEnabled;\n" //
				+ "        boolean b2 = isValid != isEnabled;\n" //
				+ "        return b1 && b2;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.DOUBLE_NEGATION);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testRedundantComparisonStatement() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final String DEFAULT = \"\";\n" //
				+ "    private String input;\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable1(String input) {\n" //
				+ "        String output;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input == null) {\n" //
				+ "            output = null;\n" //
				+ "        } else {\n" //
				+ "            output = /* Keep this comment too */ input;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable2(String input) {\n" //
				+ "        String output;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null == input) {\n" //
				+ "            output = null;\n" //
				+ "        } else {\n" //
				+ "            output = input;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable3(String input) {\n" //
				+ "        String output;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input != null) {\n" //
				+ "            output = input;\n" //
				+ "        } else {\n" //
				+ "            output = null;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable4(String input) {\n" //
				+ "        String output;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null != input) {\n" //
				+ "            output = input;\n" //
				+ "        } else {\n" //
				+ "            output = null;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeHardCodedNumber(int input) {\n" //
				+ "        int output;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (123 != input) {\n" //
				+ "            output = input;\n" //
				+ "        } else {\n" //
				+ "            output = 123;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char removeHardCodedCharacter(char input) {\n" //
				+ "        char output;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input == 'a') {\n" //
				+ "            output = 'a';\n" //
				+ "        } else {\n" //
				+ "            output = input;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeHardCodedExpression(int input) {\n" //
				+ "        int output;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input != 1 + 2 + 3) {\n" //
				+ "            output = input;\n" //
				+ "        } else {\n" //
				+ "            output = 3 + 2 + 1;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable5(String input, boolean isValid) {\n" //
				+ "        String output = null;\n" //
				+ "        if (isValid)\n" //
				+ "            if (input != null) {\n" //
				+ "                output = input;\n" //
				+ "            } else {\n" //
				+ "                output = null;\n" //
				+ "            }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorFieldAssign1(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input == null) {\n" //
				+ "            this.input = null;\n" //
				+ "        } else {\n" //
				+ "            this.input = input;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorFieldAssign2(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null == input) {\n" //
				+ "            this.input = null;\n" //
				+ "        } else {\n" //
				+ "            this.input = input;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorFieldAssign3(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input != null) {\n" //
				+ "            this.input = input;\n" //
				+ "        } else {\n" //
				+ "            this.input = null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorFieldAssign4(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null != input) {\n" //
				+ "            this.input = input;\n" //
				+ "        } else {\n" //
				+ "            this.input = null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturn1(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input == null) {\n" //
				+ "            return null;\n" //
				+ "        } else {\n" //
				+ "            return /* Keep this comment too */ input;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturn2(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null == input) {\n" //
				+ "            return null;\n" //
				+ "        } else {\n" //
				+ "            return input;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturn3(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input != null) {\n" //
				+ "            return input;\n" //
				+ "        } else {\n" //
				+ "            return null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturn4(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null != input) {\n" //
				+ "            return input;\n" //
				+ "        } else {\n" //
				+ "            return null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturnNoElse1(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input == null) {\n" //
				+ "            return null;\n" //
				+ "        }\n" //
				+ "        return input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturnNoElse2(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null == input) {\n" //
				+ "            return null;\n" //
				+ "        }\n" //
				+ "        return input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturnNoElse3(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (input != null) {\n" //
				+ "            return input;\n" //
				+ "        }\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Integer refactorReturnNoElse4(Integer number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null != number) {\n" //
				+ "            return number;\n" //
				+ "        }\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);

		enable(CleanUpConstants.REMOVE_REDUNDANT_COMPARISON_STATEMENT);

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final String DEFAULT = \"\";\n" //
				+ "    private String input;\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable1(String input) {\n" //
				+ "        String output;\n" //
				+ "        // Keep this comment\n" //
				+ "        output = /* Keep this comment too */ input;\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable2(String input) {\n" //
				+ "        String output;\n" //
				+ "        // Keep this comment\n" //
				+ "        output = input;\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable3(String input) {\n" //
				+ "        String output;\n" //
				+ "        // Keep this comment\n" //
				+ "        output = input;\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable4(String input) {\n" //
				+ "        String output;\n" //
				+ "        // Keep this comment\n" //
				+ "        output = input;\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeHardCodedNumber(int input) {\n" //
				+ "        int output;\n" //
				+ "        // Keep this comment\n" //
				+ "        output = input;\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char removeHardCodedCharacter(char input) {\n" //
				+ "        char output;\n" //
				+ "        // Keep this comment\n" //
				+ "        output = input;\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeHardCodedExpression(int input) {\n" //
				+ "        int output;\n" //
				+ "        // Keep this comment\n" //
				+ "        output = input;\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorLocalVariable5(String input, boolean isValid) {\n" //
				+ "        String output = null;\n" //
				+ "        if (isValid)\n" //
				+ "            output = input;\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorFieldAssign1(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        this.input = input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorFieldAssign2(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        this.input = input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorFieldAssign3(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        this.input = input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorFieldAssign4(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        this.input = input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturn1(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return /* Keep this comment too */ input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturn2(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturn3(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturn4(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturnNoElse1(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturnNoElse2(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorReturnNoElse3(String input) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return input;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Integer refactorReturnNoElse4(Integer number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return number;\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.RedundantComparisonStatementCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testKeepComparisonStatement() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Collection;\n" //
				+ "import java.util.Collections;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final String DEFAULT = \"\";\n" //
				+ "    private String input;\n" //
				+ "\n" //
				+ "    public String doNotRefactorLocalVariable(String input) {\n" //
				+ "        String output;\n" //
				+ "        if (input == null) {\n" //
				+ "            output = DEFAULT;\n" //
				+ "        } else {\n" //
				+ "            output = input;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorConstant(String input) {\n" //
				+ "        String output;\n" //
				+ "        if (input != null) {\n" //
				+ "            output = DEFAULT;\n" //
				+ "        } else {\n" //
				+ "            output = input;\n" //
				+ "        }\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorActiveExpression(List<String> input) {\n" //
				+ "        String result;\n" //
				+ "        if (input.remove(0) == null) {\n" //
				+ "            result = null;\n" //
				+ "        } else {\n" //
				+ "            result = input.remove(0);\n" //
				+ "        }\n" //
				+ "        return result;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotUseConstantWithoutActiveExpression(List<String> input) {\n" //
				+ "        String result;\n" //
				+ "        if (input.remove(0) == null) {\n" //
				+ "            result = null;\n" //
				+ "        } else {\n" //
				+ "            result = input.remove(0);\n" //
				+ "        }\n" //
				+ "        return result;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorFieldAssignXXX(String input, E other) {\n" //
				+ "        if (input == null) {\n" //
				+ "            this.input = null;\n" //
				+ "        } else {\n" //
				+ "            other.input = input;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorFieldAssign(String input) {\n" //
				+ "        if (input == null) {\n" //
				+ "            this.input = DEFAULT;\n" //
				+ "        } else {\n" //
				+ "            this.input = input;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorConstantReturn(String input) {\n" //
				+ "        if (null != input) {\n" //
				+ "            return input;\n" //
				+ "        } else {\n" //
				+ "            return DEFAULT;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Collection<?> doNotRefactorDifferentReturn(Collection<?> c) {\n" //
				+ "        if (c == null) {\n" //
				+ "            return Collections.emptySet();\n" //
				+ "        } else {\n" //
				+ "            return c;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Date doNotRefactorActiveAssignment(List<Date> input) {\n" //
				+ "        Date date;\n" //
				+ "        if (input.remove(0) != null) {\n" //
				+ "            date = input.remove(0);\n" //
				+ "        } else {\n" //
				+ "            date = null;\n" //
				+ "        }\n" //
				+ "        return date;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Date doNotRefactorActiveReturn(List<Date> input) {\n" //
				+ "        if (input.remove(0) != null) {\n" //
				+ "            return input.remove(0);\n" //
				+ "        }\n" //
				+ "        return null;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_REDUNDANT_COMPARISON_STATEMENT);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testUselessSuperCall() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    class A {\n" //
				+ "        A(int a) {}\n" //
				+ "\n" //
				+ "        A() {\n" //
				+ "            super();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    class B extends A {\n" //
				+ "        B(int b) {\n" //
				+ "            super(b);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        B() {\n" //
				+ "            super();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REDUNDANT_SUPER_CALL);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    class A {\n" //
				+ "        A(int a) {}\n" //
				+ "\n" //
				+ "        A() {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    class B extends A {\n" //
				+ "        B(int b) {\n" //
				+ "            super(b);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        B() {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.RedundantSuperCallCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testKeepSuperCall() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    class A {\n" //
				+ "        A(int a) {}\n" //
				+ "\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    class B extends A {\n" //
				+ "        B(int b) {\n" //
				+ "            super(b);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REDUNDANT_SUPER_CALL);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testUnreachableBlock() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.IOException;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int removeDuplicateCondition(boolean isValid, boolean isFound) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isValid && isFound) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (isFound && isValid) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionWithElse(int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (i2 > i1) {\n" //
				+ "            return 1;\n" //
				+ "        } else {\n" //
				+ "            return 2;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionWithSeveralConditions(boolean isActive, int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (isActive) {\n" //
				+ "            return 1;\n" //
				+ "        } else if (i2 > i1) {\n" //
				+ "            return 2;\n" //
				+ "        } else {\n" //
				+ "            return 3;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionWithFollowingCode(boolean isActive, int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (isActive) {\n" //
				+ "            return 1;\n" //
				+ "        } else if (i2 > i1) {\n" //
				+ "            return 2;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionWithoutFallingThrough(boolean isActive, int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (isActive) {\n" //
				+ "            System.out.println(\"I do not fall through\");\n" //
				+ "        } else if (i2 > i1) {\n" //
				+ "            System.out.println(\"I do not fall through too\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionAmongOthers(int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 == 0) {\n" //
				+ "            return -1;\n" //
				+ "        } else if (i1 < i2 + 1) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (1 + i2 > i1) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeDuplicateConditionWithoutUnreachableCode(boolean isActive, boolean isFound) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive && isFound) {\n" //
				+ "            System.out.println(\"I fall through\");\n" //
				+ "            return;\n" //
				+ "        } else if (isFound && isActive) {\n" //
				+ "            System.out.println(\"I do not fall through\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeUncaughtCode(boolean b1, boolean b2) throws IOException {\n" //
				+ "        try {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (b1 && b2) {\n" //
				+ "                return 0;\n" //
				+ "            } else if (b2 && b1) {\n" //
				+ "                throw new IOException();\n" //
				+ "            }\n" //
				+ "        } catch (NullPointerException e) {\n" //
				+ "            System.out.println(\"I should be reachable\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.IOException;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int removeDuplicateCondition(boolean isValid, boolean isFound) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isValid && isFound) {\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionWithElse(int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            return 2;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionWithSeveralConditions(boolean isActive, int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (isActive) {\n" //
				+ "            return 1;\n" //
				+ "        } else {\n" //
				+ "            return 3;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionWithFollowingCode(boolean isActive, int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (isActive) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionWithoutFallingThrough(boolean isActive, int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (isActive) {\n" //
				+ "            System.out.println(\"I do not fall through\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeDuplicateConditionAmongOthers(int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 == 0) {\n" //
				+ "            return -1;\n" //
				+ "        } else if (i1 < i2 + 1) {\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeDuplicateConditionWithoutUnreachableCode(boolean isActive, boolean isFound) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive && isFound) {\n" //
				+ "            System.out.println(\"I fall through\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeUncaughtCode(boolean b1, boolean b2) throws IOException {\n" //
				+ "        try {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (b1 && b2) {\n" //
				+ "                return 0;\n" //
				+ "            }\n" //
				+ "        } catch (NullPointerException e) {\n" //
				+ "            System.out.println(\"I should be reachable\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.UNREACHABLE_BLOCK);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.UnreachableBlockCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testKeepUnreachableBlock() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.IOException;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public String doNotCreateUnreachable(int i1, int i2) {\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return \"Falls through\";\n" //
				+ "        } else if (i2 > i1) {\n" //
				+ "            System.out.println(\"Does not fall through\");\n" //
				+ "        } else {\n" //
				+ "            return \"Falls through too\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"I should be reachable\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotCreateUnreachableOverSeveralConditions(boolean isEnabled, int i1, int i2) {\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            return \"Falls through\";\n" //
				+ "        } else if (isEnabled) {\n" //
				+ "            return \"Falls through too\";\n" //
				+ "        } else if (i2 > i1) {\n" //
				+ "            System.out.println(\"Does not fall through\");\n" //
				+ "        } else {\n" //
				+ "            return \"Falls through also\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"I should be reachable\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRemoveDifferentCondition(boolean b1, boolean b2) {\n" //
				+ "        if (b1 && b2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (b2 || b1) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRemoveActiveCondition(List<String> myList) {\n" //
				+ "        if (myList.remove(\"I will be removed\")) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (myList.remove(\"I will be removed\")) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRemoveConditionPrecededByActiveCondition(int number) {\n" //
				+ "        if (number > 0) {\n" //
				+ "            return \"Falls through\";\n" //
				+ "        } else if (number++ == 42) {\n" //
				+ "            return \"Active condition\";\n" //
				+ "        } else if (number > 0) {\n" //
				+ "            return \"Falls through too\";\n" //
				+ "        } else {\n" //
				+ "            return \"Falls through also\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRemoveCaughtCode(boolean b1, boolean b2) {\n" //
				+ "        try {\n" //
				+ "            if (b1 && b2) {\n" //
				+ "                return 0;\n" //
				+ "            } else if (b2 && b1) {\n" //
				+ "                throw new IOException();\n" //
				+ "            }\n" //
				+ "        } catch (IOException e) {\n" //
				+ "            System.out.println(\"I should be reachable\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRemoveThrowingExceptionCode(boolean isValid, int number) {\n" //
				+ "        if (isValid || true) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (isValid || true || ((number / 0) == 42)) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.UNREACHABLE_BLOCK);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testMergeConditionalBlocks() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void duplicateIfAndElseIf(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else if (i == 123) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void mergeTwoStructures(int a, int b) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (a == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (a + 1));\n" //
				+ "        } else if (a == 1) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (1 + a));\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment\n" //
				+ "        if (b == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (a + 123 + 0));\n" //
				+ "        } else if (b == 1) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (a + 123));\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else code, merge it */\n" //
				+ "    public void duplicateIfAndElse(int j) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (j == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (j > 0) {\n" //
				+ "                System.out.println(\"Duplicate\");\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Duplicate too\");\n" //
				+ "            }\n" //
				+ "        } else if (j == 1) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (j <= 0) {\n" //
				+ "                System.out.println(\"Duplicate too\");\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Duplicate\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void duplicateIfAndElseIfWithoutElse(int k) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (k == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + !(k == 0));\n" //
				+ "        } else if (k == 1) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (k != 0));\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate else if codes, merge it */\n" //
				+ "    public void duplicateIfAndElseIfAmongOther(int m) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (m == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"A given code\");\n" //
				+ "        } if (m == 1) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (m > 0));\n" //
				+ "        } else if (m == 2) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (0 < m));\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void duplicateSingleStatement(int n) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (n == 0)\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (n > 0) {\n" //
				+ "                System.out.println(\"Duplicate\");\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Duplicate too\");\n" //
				+ "            }\n" //
				+ "        else if (n == 1)\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (n > 0)\n" //
				+ "                System.out.println(\"Duplicate\");\n" //
				+ "            else\n" //
				+ "                System.out.println(\"Duplicate too\");\n" //
				+ "        else\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void numerousDuplicateIfAndElseIf(int o) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (o == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else if (o == 1) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else if (o == 2)\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        else if (o == 3) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void complexIfAndElseIf(int p) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (p == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate \");\n" //
				+ "        } else if (p == 1 || p == 2) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate \");\n" //
				+ "        } else if (p > 10) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate \");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void longIfAndElseIf(int q, boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (q == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "            q = q + 1;\n" //
				+ "        } else if (isValid ? (q == 1) : (q > 1)) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "            q++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MERGE_CONDITIONAL_BLOCKS);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void duplicateIfAndElseIf(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((i == 0) || (i == 123)) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void mergeTwoStructures(int a, int b) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((a == 0) || (a == 1)) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (a + 1));\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((b == 0) || (b == 1)) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (a + 123 + 0));\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else code, merge it */\n" //
				+ "    public void duplicateIfAndElse(int j) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((j == 0) || (j != 1)) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (j > 0) {\n" //
				+ "                System.out.println(\"Duplicate\");\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Duplicate too\");\n" //
				+ "            }\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void duplicateIfAndElseIfWithoutElse(int k) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((k == 0) || (k == 1)) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + !(k == 0));\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate else if codes, merge it */\n" //
				+ "    public void duplicateIfAndElseIfAmongOther(int m) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (m == 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"A given code\");\n" //
				+ "        } if ((m == 1) || (m == 2)) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\" + (m > 0));\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void duplicateSingleStatement(int n) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((n == 0) || (n == 1))\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (n > 0) {\n" //
				+ "                System.out.println(\"Duplicate\");\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Duplicate too\");\n" //
				+ "            }\n" //
				+ "        else\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void numerousDuplicateIfAndElseIf(int o) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((o == 0) || (o == 1) || (o == 2)\n" //
				+ "                || (o == 3)) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void complexIfAndElseIf(int p) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((p == 0) || (p == 1 || p == 2) || (p > 10)) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate \");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Duplicate if and else if code, merge it */\n" //
				+ "    public void longIfAndElseIf(int q, boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((q == 0) || (isValid ? (q != 1) : (q <= 1))) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "            q = q + 1;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.MergeConditionalBlocksCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testDoNotMergeConditionalBlocks() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    /** 5 operands, not easily readable */\n" //
				+ "    public void doNotMergeMoreThanFourOperands(int i) {\n" //
				+ "        if ((i == 0) || (i == 1 || i == 2)) {\n" //
				+ "            System.out.println(\"Duplicate \");\n" //
				+ "        } else if (i > 10 && i < 100) {\n" //
				+ "            System.out.println(\"Duplicate \");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Different if and else if code, leave it */\n" //
				+ "    public void doNotMergeAdditionalCode(int i) {\n" //
				+ "        if (i == 0) {\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else if (i == 1) {\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "            System.out.println(\"but not only\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Different code in the middle, leave it */\n" //
				+ "    public void doNotMergeIntruderCode(int i) {\n" //
				+ "        if (i == 0) {\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else if (i == 1) {\n" //
				+ "            System.out.println(\"Intruder\");\n" //
				+ "        } else if (i == 2) {\n" //
				+ "            System.out.println(\"Duplicate\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Different\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MERGE_CONDITIONAL_BLOCKS);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testRedundantFallingThroughBlockEnd() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private boolean b= true;\n" //
				+ "\n" //
				+ "    public void mergeIfBlocksIntoFollowingCode(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        } else if (i == 10) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } else if (i == 20) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int mergeUselessElse(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "        } else return i + 123;\n" //
				+ "        return i + 123;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char mergeIfStatementIntoFollowingCode(int j) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (j <= 0) return 'a';\n" //
				+ "        else if (j == 10) return 'b';\n" //
				+ "        else if (j == 20) return 'a';\n" //
				+ "        return 'a';\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeEndOfIfIntoFollowingCode(int k) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (k <= 0) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        } else if (k == 10) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } else if (k == 20) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeWithoutContinue(int i, int j) {\n" //
				+ "        while (j-- > 0) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (i <= 0) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                continue;\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                continue;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeWithoutReturn(int n) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (n <= 0) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        } else if (n == 10) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } else if (n == 20) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeIfThrowingException(int i) throws Exception {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "            i += 42;\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            throw new Exception();\n" //
				+ "        } else if (i == 10) {\n" //
				+ "            i += 42;\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            throw new Exception();\n" //
				+ "        } else if (i == 20) {\n" //
				+ "            i += 42;\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            throw new Exception();\n" //
				+ "        }\n" //
				+ "        i = i + 42;\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        throw new Exception();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeDeepStatements(String number, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            if (i <= 0) {\n" //
				+ "                i += 42;\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                return;\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                i += 42;\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                return;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "                i += 42;\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeIfWithContinue(int[] numbers) {\n" //
				+ "        for (int i : numbers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (i <= 0) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                continue;\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                continue;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeIfWithBreak(int[] numbers) {\n" //
				+ "        for (int i : numbers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (i <= 0) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                break;\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                break;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeIfThatAlwaysFallThrough(int i, boolean interruptCode) throws Exception {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "            i++;\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            } else {\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "        } else if (i == 10) {\n" //
				+ "            i += 1;\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            } else {\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "        } else if (i == 20) {\n" //
				+ "            i = 1 + i;\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            } else {\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "        i = i + 1;\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        if (interruptCode) {\n" //
				+ "            throw new Exception(\"Stop!\");\n" //
				+ "        } else {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchIntoFollowingCode(String number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeEndOfCatchIntoFollowingCode(String number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchThrowingException(String number) throws Exception {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            throw new Exception();\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            throw new Exception();\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            throw new Exception();\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        throw new Exception();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchWithContinue(List<String> numbers) {\n" //
				+ "        for (String number : numbers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            try {\n" //
				+ "                Integer.valueOf(number);\n" //
				+ "            } catch (NumberFormatException nfe) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                continue;\n" //
				+ "            } catch (IllegalArgumentException iae) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                continue;\n" //
				+ "            } catch (NullPointerException npe) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchWithBreak(List<String> numbers) {\n" //
				+ "        for (String number : numbers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            try {\n" //
				+ "                Integer.valueOf(number);\n" //
				+ "            } catch (NumberFormatException nfe) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                break;\n" //
				+ "            } catch (IllegalArgumentException iae) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                break;\n" //
				+ "            } catch (NullPointerException npe) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchThatAlwaysFallThrough(String number, boolean interruptCode) throws Exception {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            } else {\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            } else {\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            if (!interruptCode) {\n" //
				+ "                return;\n" //
				+ "            } else {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        if (interruptCode) {\n" //
				+ "            throw new Exception(\"Stop!\");\n" //
				+ "        } else {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.REDUNDANT_FALLING_THROUGH_BLOCK_END);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private boolean b= true;\n" //
				+ "\n" //
				+ "    public void mergeIfBlocksIntoFollowingCode(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "        } else if (i == 10) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } else if (i == 20) {\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int mergeUselessElse(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "        }\n" //
				+ "        return i + 123;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char mergeIfStatementIntoFollowingCode(int j) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (j <= 0) {\n" //
				+ "        } else if (j == 10) return 'b';\n" //
				+ "        else if (j == 20) {\n" //
				+ "        }\n" //
				+ "        return 'a';\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeEndOfIfIntoFollowingCode(int k) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (k <= 0) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "        } else if (k == 10) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } else if (k == 20) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeWithoutContinue(int i, int j) {\n" //
				+ "        while (j-- > 0) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (i <= 0) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                continue;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeWithoutReturn(int n) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (n <= 0) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "        } else if (n == 10) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } else if (n == 20) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeIfThrowingException(int i) throws Exception {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "        } else if (i == 10) {\n" //
				+ "            i += 42;\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            throw new Exception();\n" //
				+ "        } else if (i == 20) {\n" //
				+ "        }\n" //
				+ "        i = i + 42;\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        throw new Exception();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeDeepStatements(String number, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            if (i <= 0) {\n" //
				+ "                i += 42;\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                i += 42;\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                return;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "                i += 42;\n" //
				+ "            }\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeIfWithContinue(int[] numbers) {\n" //
				+ "        for (int i : numbers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (i <= 0) {\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                continue;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeIfWithBreak(int[] numbers) {\n" //
				+ "        for (int i : numbers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (i <= 0) {\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                break;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeIfThatAlwaysFallThrough(int i, boolean interruptCode) throws Exception {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "        } else if (i == 10) {\n" //
				+ "            i += 1;\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            } else {\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "        } else if (i == 20) {\n" //
				+ "        }\n" //
				+ "        i = i + 1;\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        if (interruptCode) {\n" //
				+ "            throw new Exception(\"Stop!\");\n" //
				+ "        } else {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchIntoFollowingCode(String number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeEndOfCatchIntoFollowingCode(String number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchThrowingException(String number) throws Exception {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            throw new Exception();\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        throw new Exception();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchWithContinue(List<String> numbers) {\n" //
				+ "        for (String number : numbers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            try {\n" //
				+ "                Integer.valueOf(number);\n" //
				+ "            } catch (NumberFormatException nfe) {\n" //
				+ "            } catch (IllegalArgumentException iae) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                continue;\n" //
				+ "            } catch (NullPointerException npe) {\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchWithBreak(List<String> numbers) {\n" //
				+ "        for (String number : numbers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            try {\n" //
				+ "                Integer.valueOf(number);\n" //
				+ "            } catch (NumberFormatException nfe) {\n" //
				+ "            } catch (IllegalArgumentException iae) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                break;\n" //
				+ "            } catch (NullPointerException npe) {\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void mergeCatchThatAlwaysFallThrough(String number, boolean interruptCode) throws Exception {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            } else {\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        if (interruptCode) {\n" //
				+ "            throw new Exception(\"Stop!\");\n" //
				+ "        } else {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.RedundantFallingThroughBlockEndCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotMergeRedundantFallingThroughBlockEnd() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private boolean b= true;\n" //
				+ "\n" //
				+ "    public void doNotMergeDifferentVariable(int i) {\n" //
				+ "        if (i <= 0) {\n" //
				+ "            boolean b= false;\n" //
				+ "            System.out.println(\"Display a varaible: \" + b);\n" //
				+ "            return;\n" //
				+ "        } else if (i == 10) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            return;\n" //
				+ "        } else if (i == 20) {\n" //
				+ "            int b= 123;\n" //
				+ "            System.out.println(\"Display a varaible: \" + b);\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Display a varaible: \" + b);\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMergeWithoutLabeledContinue(int i, int j, int k) {\n" //
				+ "        loop: while (k-- > 0) {\n" //
				+ "            while (j-- > 0) {\n" //
				+ "                if (i <= 0) {\n" //
				+ "                    System.out.println(\"Doing another thing\");\n" //
				+ "                    System.out.println(\"Doing something\");\n" //
				+ "                    continue loop;\n" //
				+ "                } else if (i == 10) {\n" //
				+ "                    System.out.println(\"Doing another thing\");\n" //
				+ "                    continue loop;\n" //
				+ "                } else if (i == 20) {\n" //
				+ "                    System.out.println(\"Doing another thing\");\n" //
				+ "                    System.out.println(\"Doing something\");\n" //
				+ "                    continue loop;\n" //
				+ "                }\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMergeWithoutBreak(int i, int j) {\n" //
				+ "        while (j-- > 0) {\n" //
				+ "            if (i <= 0) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                break;\n" //
				+ "            } else if (i == 10) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                break;\n" //
				+ "            } else if (i == 20) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorCodeThatDoesntFallThrough(int m) {\n" //
				+ "        if (m <= 0) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "        } else if (m == 20) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorNotLastStatements(String number, int n) {\n" //
				+ "        if (n > 0) {\n" //
				+ "            try {\n" //
				+ "                Integer.valueOf(number);\n" //
				+ "            } catch (NumberFormatException nfe) {\n" //
				+ "                if (n == 5) {\n" //
				+ "                    n += 42;\n" //
				+ "                    System.out.println(\"Doing something\");\n" //
				+ "                    return;\n" //
				+ "                } else if (n == 10) {\n" //
				+ "                    n += 42;\n" //
				+ "                    System.out.println(\"Doing another thing\");\n" //
				+ "                    return;\n" //
				+ "                } else if (n == 20) {\n" //
				+ "                    n += 42;\n" //
				+ "                    System.out.println(\"Doing something\");\n" //
				+ "                    return;\n" //
				+ "                }\n" //
				+ "            } catch (IllegalArgumentException iae) {\n" //
				+ "                System.out.println(\"Doing another thing\");\n" //
				+ "                return;\n" //
				+ "            } catch (NullPointerException npe) {\n" //
				+ "                System.out.println(\"Doing something\");\n" //
				+ "                return;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Insidious code...\");\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMergeIfThatNotAlwaysFallThrough(int i, boolean interruptCode) throws Exception {\n" //
				+ "        if (i <= 0) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            }\n" //
				+ "        } else if (i == 10) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            }\n" //
				+ "        } else if (i == 20) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        if (interruptCode) {\n" //
				+ "            throw new Exception(\"Stop!\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorWithFinally(String number) {\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            return;\n" //
				+ "        } finally {\n" //
				+ "            System.out.println(\"Beware of finally!\");\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorCodeThatDoesntFallThrough(String number) {\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMergeCatchThatNotAlwaysFallThrough(String number, boolean interruptCode) throws Exception {\n" //
				+ "        try {\n" //
				+ "            Integer.valueOf(number);\n" //
				+ "        } catch (NumberFormatException nfe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            }\n" //
				+ "        } catch (IllegalArgumentException iae) {\n" //
				+ "            System.out.println(\"Doing another thing\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            }\n" //
				+ "        } catch (NullPointerException npe) {\n" //
				+ "            System.out.println(\"Doing something\");\n" //
				+ "            if (interruptCode) {\n" //
				+ "                throw new Exception(\"Stop!\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Doing something\");\n" //
				+ "        if (interruptCode) {\n" //
				+ "            throw new Exception(\"Stop!\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.REDUNDANT_FALLING_THROUGH_BLOCK_END);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testRedundantIfCondition() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.IOException;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int removeOppositeCondition(boolean b1, boolean b2) {\n" //
				+ "        int i = -1;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (b1 && b2) {\n" //
				+ "            i = 0;\n" //
				+ "        } else if (!b2 || !b1) {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeOppositeConditionWithElse(int i1, int i2) {\n" //
				+ "        int i = -1;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            i = 0;\n" //
				+ "        } else if (i2 <= i1) {\n" //
				+ "            i = 1;\n" //
				+ "        } else {\n" //
				+ "            i = 2;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeOppositeConditionAmongOthers(int i1, int i2) {\n" //
				+ "        int i = -1;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 == 0) {\n" //
				+ "            i = -1;\n" //
				+ "        } else if (i1 < i2 + 1) {\n" //
				+ "            i = 0;\n" //
				+ "        } else if (1 + i2 <= i1) {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorCaughtCode(boolean b1, boolean b2) {\n" //
				+ "        int i = -1;\n" //
				+ "        try {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (b1 && b2) {\n" //
				+ "                i = 0;\n" //
				+ "            } else if (!b2 || !b1) {\n" //
				+ "                throw new IOException();\n" //
				+ "            }\n" //
				+ "        } catch (IOException e) {\n" //
				+ "            System.out.println(\"I should be reachable\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeUncaughtCode(boolean b1, boolean b2) {\n" //
				+ "        int i = -1;\n" //
				+ "        try {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (!b1 == b2) {\n" //
				+ "                i = 0;\n" //
				+ "            } else if (!b2 != b1) {\n" //
				+ "                i = 1;\n" //
				+ "            } else {\n" //
				+ "                throw new NullPointerException();\n" //
				+ "            }\n" //
				+ "        } finally {\n" //
				+ "            System.out.println(\"I should be reachable\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.REDUNDANT_IF_CONDITION);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.IOException;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int removeOppositeCondition(boolean b1, boolean b2) {\n" //
				+ "        int i = -1;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (b1 && b2) {\n" //
				+ "            i = 0;\n" //
				+ "        } else {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeOppositeConditionWithElse(int i1, int i2) {\n" //
				+ "        int i = -1;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 < i2) {\n" //
				+ "            i = 0;\n" //
				+ "        } else {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeOppositeConditionAmongOthers(int i1, int i2) {\n" //
				+ "        int i = -1;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i1 == 0) {\n" //
				+ "            i = -1;\n" //
				+ "        } else if (i1 < i2 + 1) {\n" //
				+ "            i = 0;\n" //
				+ "        } else {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorCaughtCode(boolean b1, boolean b2) {\n" //
				+ "        int i = -1;\n" //
				+ "        try {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (b1 && b2) {\n" //
				+ "                i = 0;\n" //
				+ "            } else {\n" //
				+ "                throw new IOException();\n" //
				+ "            }\n" //
				+ "        } catch (IOException e) {\n" //
				+ "            System.out.println(\"I should be reachable\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int removeUncaughtCode(boolean b1, boolean b2) {\n" //
				+ "        int i = -1;\n" //
				+ "        try {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (!b1 == b2) {\n" //
				+ "                i = 0;\n" //
				+ "            } else {\n" //
				+ "                i = 1;\n" //
				+ "            }\n" //
				+ "        } finally {\n" //
				+ "            System.out.println(\"I should be reachable\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "}\n";
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.RedundantIfConditionCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testKeepIfCondition() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.IOException;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int doNotRemoveDifferentCondition(boolean b1, boolean b2) {\n" //
				+ "        int i = -1;\n" //
				+ "        if (b1 && b2) {\n" //
				+ "            i = 0;\n" //
				+ "        } else if (b2 || b1) {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRemoveMovedOperands(int number1, int number2) {\n" //
				+ "        int i = -1;\n" //
				+ "        if (number1 < number2) {\n" //
				+ "            i = 0;\n" //
				+ "        } else if (number2 < number1) {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotHandleObjectsThatCanBeNull(Boolean isValid) {\n" //
				+ "        int i = -1;\n" //
				+ "        if (isValid == Boolean.TRUE) {\n" //
				+ "            i = 0;\n" //
				+ "        } else if (isValid == Boolean.FALSE) {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRemoveActiveCondition(List<String> myList) {\n" //
				+ "        int i = -1;\n" //
				+ "        if (myList.remove(\"I will be removed\")) {\n" //
				+ "            i = 0;\n" //
				+ "        } else if (myList.remove(\"I will be removed\")) {\n" //
				+ "            i = 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRemoveCaughtCode(boolean b1, boolean b2) {\n" //
				+ "        int i = -1;\n" //
				+ "        try {\n" //
				+ "            if (b1 && b2) {\n" //
				+ "                i = 0;\n" //
				+ "            } else if (!b2 || !b1) {\n" //
				+ "                i = 1;\n" //
				+ "            } else {\n" //
				+ "                throw new IOException();\n" //
				+ "            }\n" //
				+ "        } catch (IOException e) {\n" //
				+ "            System.out.println(\"I should be reachable\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorFallThroughBlocks(boolean b1, boolean b2) {\n" //
				+ "        if (b1 && b2) {\n" //
				+ "            return 0;\n" //
				+ "        } else if (!b2 || !b1) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 2;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.REDUNDANT_IF_CONDITION);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testArrayWithCurly() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Observable;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    /**\n" //
				+ "     * Keep this comment.\n" //
				+ "     */\n" //
				+ "    private double[] refactorThisDoubleArray = new double[] { 42.42 };\n" //
				+ "\n" //
				+ "    /**\n" //
				+ "     * Keep this comment.\n" //
				+ "     */\n" //
				+ "    private int[][] refactorThis2DimensionArray = new int[][] { { 42 } };\n" //
				+ "\n" //
				+ "    /**\n" //
				+ "     * Keep this comment.\n" //
				+ "     */\n" //
				+ "    private Observable[] refactorThisObserverArray = new Observable[0];\n" //
				+ "\n" //
				+ "    /**\n" //
				+ "     * Keep this comment.\n" //
				+ "     */\n" //
				+ "    private short[] refactorThisShortArray, andThisArrayToo = new short[0];\n" //
				+ "\n" //
				+ "    public void refactorArrayInstantiations() {\n" //
				+ "        // Keep this comment\n" //
				+ "        double[] refactorLocalDoubleArray = new double[] { 42.42 };\n" //
				+ "        char[][] refactorLocal2DimensionArray = new char[][] { { 'a' } };\n" //
				+ "        Observable[] refactorLocalObserverArray = new Observable[0];\n" //
				+ "        short[] refactorThisShortArray, andThisArrayToo = new short[0];\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Observable;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    /**\n" //
				+ "     * Keep this comment.\n" //
				+ "     */\n" //
				+ "    private double[] refactorThisDoubleArray = { 42.42 };\n" //
				+ "\n" //
				+ "    /**\n" //
				+ "     * Keep this comment.\n" //
				+ "     */\n" //
				+ "    private int[][] refactorThis2DimensionArray = { { 42 } };\n" //
				+ "\n" //
				+ "    /**\n" //
				+ "     * Keep this comment.\n" //
				+ "     */\n" //
				+ "    private Observable[] refactorThisObserverArray = {};\n" //
				+ "\n" //
				+ "    /**\n" //
				+ "     * Keep this comment.\n" //
				+ "     */\n" //
				+ "    private short[] refactorThisShortArray, andThisArrayToo = {};\n" //
				+ "\n" //
				+ "    public void refactorArrayInstantiations() {\n" //
				+ "        // Keep this comment\n" //
				+ "        double[] refactorLocalDoubleArray = { 42.42 };\n" //
				+ "        char[][] refactorLocal2DimensionArray = { { 'a' } };\n" //
				+ "        Observable[] refactorLocalObserverArray = {};\n" //
				+ "        short[] refactorThisShortArray, andThisArrayToo = {};\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.ARRAY_WITH_CURLY);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.ArrayWithCurlyCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUseArrayWithCurly() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Observable;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private Byte[] doNotRefactorNotInitializedArray = new Byte[10];\n" //
				+ "\n" //
				+ "    private Object doNotRefactorThisObserverArray = new Observable[0];\n" //
				+ "\n" //
				+ "    public void doNotRefactorArrayAssignment() {\n" //
				+ "        char[] refactorLocalDoubleArray;\n" //
				+ "        refactorLocalDoubleArray = new char[] { 'a', 'b' };\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorArrayInstantiationsInBrackets() {\n" //
				+ "        boolean[] refactorLocalDoubleArray = (new boolean[] { true });\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorCastedArrayInstantiations() {\n" //
				+ "        Object refactorLocalDoubleArray = (double[]) new double[] { 42.42 };\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public double[] doNotRefactorReturnedArrayInstantiation() {\n" //
				+ "        return new double[] { 42.42 };\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorArrayInstantiationParameter() {\n" //
				+ "        System.out.println(new double[] { 42.42 });\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorArrayInstantiationExpression() {\n" //
				+ "        return new float[] { 42.42f }.toString();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.ARRAY_WITH_CURLY);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testRemoveUselessReturn() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void removeUselessReturn() {\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnWithPreviousCode() {\n" //
				+ "        System.out.println(\"Keep this line\");\n" //
				+ "        return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnWithIf(boolean isValid) {\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceByBlock(boolean isEnabled) {\n" //
				+ "        System.out.println(\"Keep this line\");\n" //
				+ "        if (isEnabled)\n" //
				+ "            return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeElseStatement(boolean isValid) {\n" //
				+ "        System.out.println(\"Keep this line\");\n" //
				+ "        if (isValid)\n" //
				+ "            System.out.println(\"isValid is true\");\n" //
				+ "        else\n" //
				+ "            return;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeElseBlock(boolean isValid) {\n" //
				+ "        System.out.println(\"Keep this line\");\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(\"isValid is true\");\n" //
				+ "        } else {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnWithSwitch(int myNumber) {\n" //
				+ "        switch (myNumber) {\n" //
				+ "        case 0:\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnWithIfElse(boolean isValid) {\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            return;\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Remove anyway\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnInLambda() {\n" //
				+ "        Runnable r = () -> {return;};\n" //
				+ "        r.run();\n" //
				+ "        System.out.println(\"Remove anyway\");\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_USELESS_RETURN);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void removeUselessReturn() {\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnWithPreviousCode() {\n" //
				+ "        System.out.println(\"Keep this line\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnWithIf(boolean isValid) {\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceByBlock(boolean isEnabled) {\n" //
				+ "        System.out.println(\"Keep this line\");\n" //
				+ "        if (isEnabled) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeElseStatement(boolean isValid) {\n" //
				+ "        System.out.println(\"Keep this line\");\n" //
				+ "        if (isValid)\n" //
				+ "            System.out.println(\"isValid is true\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeElseBlock(boolean isValid) {\n" //
				+ "        System.out.println(\"Keep this line\");\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(\"isValid is true\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnWithSwitch(int myNumber) {\n" //
				+ "        switch (myNumber) {\n" //
				+ "        case 0:\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnWithIfElse(boolean isValid) {\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Remove anyway\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessReturnInLambda() {\n" //
				+ "        Runnable r = () -> {};\n" //
				+ "        r.run();\n" //
				+ "        System.out.println(\"Remove anyway\");\n" //
				+ "    }\n" //
				+ "}\n";

		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.UselessReturnCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testDoNotRemoveReturn() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public int doNotRemoveReturnWithValue() {\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveUselessReturnInMiddleOfSwitch(int myNumber) {\n" //
				+ "        switch (myNumber) {\n" //
				+ "        case 0:\n" //
				+ "            System.out.println(\"I'm not the last statement\");\n" //
				+ "            return;\n" //
				+ "        case 1:\n" //
				+ "            System.out.println(\"Do some stuff\");\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveReturnWithFollowingCode(boolean isValid) {\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        System.out.println(\"Do not forget me\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveReturnInWhile(int myNumber) {\n" //
				+ "        while (myNumber-- > 0) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveReturnInDoWhile(int myNumber) {\n" //
				+ "        do {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            return;\n" //
				+ "        } while (myNumber-- > 0);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveReturnInFor() {\n" //
				+ "        for (int myNumber = 0; myNumber < 10; myNumber++) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveReturnInForEach(int[] integers) {\n" //
				+ "        for (int myNumber : integers) {\n" //
				+ "            System.out.println(\"Only the first value: \" + myNumber);\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_USELESS_RETURN);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testRemoveUselessContinue() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void removeUselessContinue(List<String> texts) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessContinueWithPreviousCode(List<String> texts) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessContinueWithIf(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            if (isValid) {\n" //
				+ "                System.out.println(\"Keep this line\");\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceByBlock(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            if (isValid)\n" //
				+ "                continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeElseStatement(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            if (isValid)\n" //
				+ "                System.out.println(\"isValid is true\");\n" //
				+ "            else\n" //
				+ "                continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeElseBlock(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            if (isValid) {\n" //
				+ "                System.out.println(\"isValid is true\");\n" //
				+ "            } else {\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessContinueWithSwitch(List<String> texts, int myNumber) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            switch (myNumber) {\n" //
				+ "            case 0:\n" //
				+ "                System.out.println(\"Keep this line\");\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessContinueWithIfElse(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            if (isValid) {\n" //
				+ "                System.out.println(\"Keep this line\");\n" //
				+ "                continue;\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Remove anyway\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack1.createCompilationUnit("E1.java", input, false, null);

		enable(CleanUpConstants.REMOVE_USELESS_CONTINUE);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void removeUselessContinue(List<String> texts) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessContinueWithPreviousCode(List<String> texts) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessContinueWithIf(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            if (isValid) {\n" //
				+ "                System.out.println(\"Keep this line\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceByBlock(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            if (isValid) {\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeElseStatement(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            if (isValid)\n" //
				+ "                System.out.println(\"isValid is true\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeElseBlock(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "            if (isValid) {\n" //
				+ "                System.out.println(\"isValid is true\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessContinueWithSwitch(List<String> texts, int myNumber) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            switch (myNumber) {\n" //
				+ "            case 0:\n" //
				+ "                System.out.println(\"Keep this line\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeUselessContinueWithIfElse(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            if (isValid) {\n" //
				+ "                System.out.println(\"Keep this line\");\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Remove anyway\");\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.UselessContinueCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotRemoveContinue() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void doNotRemoveBreak(List<String> texts) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveReturn(List<String> texts) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveThrow(List<String> texts) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            throw new NullPointerException();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveContinueWithLabel(List<String> texts, List<String> otherTexts) {\n" //
				+ "        begin: for (String text : texts) {\n" //
				+ "            for (String otherText : otherTexts) {\n" //
				+ "                System.out.println(\"Keep this line\");\n" //
				+ "                continue begin;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveUselessContinueInMiddleOfSwitch(List<String> texts, int myNumber) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            switch (myNumber) {\n" //
				+ "            case 0:\n" //
				+ "                System.out.println(\"I'm not the last statement\");\n" //
				+ "                continue;\n" //
				+ "            case 1:\n" //
				+ "                System.out.println(\"Do some stuff\");\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveContinueWithFollowingCode(List<String> texts, boolean isValid) {\n" //
				+ "        for (String text : texts) {\n" //
				+ "            if (isValid) {\n" //
				+ "                System.out.println(\"Keep this line\");\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"Keep this line\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_USELESS_CONTINUE);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testUnloopedWhile() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void replaceWhileByIf(boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        while (isValid) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileThrowingExceptions(boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        while (isEnabled) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            throw new NullPointerException();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileByIfAndRemoveBreak(boolean isVisible) {\n" //
				+ "        // Keep this comment\n" //
				+ "        while (isVisible) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileByIfAndReplaceBreaksByBlocks(boolean isVisible, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        while (isVisible) {\n" //
				+ "            if (i > 0)\n" //
				+ "                break;\n" //
				+ "            else\n" //
				+ "                break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileWithComplexCode(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        while (b1) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            if (b2) {\n" //
				+ "                System.out.println(\"bar\");\n" //
				+ "                return;\n" //
				+ "            } else {\n" //
				+ "                throw new NullPointerException();\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileButOnlyRemoveBreakForTheWhileLoop(boolean b, int magicValue) {\n" //
				+ "        // Keep this comment\n" //
				+ "        while (b) {\n" //
				+ "            for (int i = 0; i < 10; i++) {\n" //
				+ "                if (i == magicValue) {\n" //
				+ "                    System.out.println(\"Magic value! Goodbye!\");\n" //
				+ "                    break;\n" //
				+ "                } else {\n" //
				+ "                    System.out.println(\"Current value: \" + i);\n" //
				+ "                }\n" //
				+ "            }\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void replaceWhileByIf(boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileThrowingExceptions(boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isEnabled) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            throw new NullPointerException();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileByIfAndRemoveBreak(boolean isVisible) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isVisible) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileByIfAndReplaceBreaksByBlocks(boolean isVisible, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isVisible) {\n" //
				+ "            if (i > 0) {\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileWithComplexCode(boolean b1, boolean b2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (b1) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            if (b2) {\n" //
				+ "                System.out.println(\"bar\");\n" //
				+ "                return;\n" //
				+ "            } else {\n" //
				+ "                throw new NullPointerException();\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void replaceWhileButOnlyRemoveBreakForTheWhileLoop(boolean b, int magicValue) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (b) {\n" //
				+ "            for (int i = 0; i < 10; i++) {\n" //
				+ "                if (i == magicValue) {\n" //
				+ "                    System.out.println(\"Magic value! Goodbye!\");\n" //
				+ "                    break;\n" //
				+ "                } else {\n" //
				+ "                    System.out.println(\"Current value: \" + i);\n" //
				+ "                }\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.UNLOOPED_WHILE);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.UnloopedWhileCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testKeepUnloopedWhile() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void doNotReplaceWhileEndedByContinue(boolean b) {\n" //
				+ "        while (b) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            continue;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotReplaceInfiniteWhile() {\n" //
				+ "        while (true) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotReplaceComplexInfiniteWhile() {\n" //
				+ "        while (42 == 42) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceWhileUsingContinue(boolean b1, boolean b2) {\n" //
				+ "        while (b1) {\n" //
				+ "            if (b2) {\n" //
				+ "                System.out.println(\"bar\");\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceWhileThatMayHaveSeveralIterations(int i) {\n" //
				+ "        while (i-- > 0) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            if (i == 1) {\n" //
				+ "                System.out.println(\"bar\");\n" //
				+ "                return;\n" //
				+ "            } else if (i == 2) {\n" //
				+ "                throw new NullPointerException();\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotReplaceWhileThatHasLabeledBreak(boolean b) {\n" //
				+ "        doNotTrashThisSpecialBreak:while (b) {\n" //
				+ "            System.out.println(\"foo\");\n" //
				+ "            break doNotTrashThisSpecialBreak;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRemoveBreakThatShortcutsCode(boolean isValid, boolean isEnabled) {\n" //
				+ "        while (isValid) {\n" //
				+ "            if (isEnabled) {\n" //
				+ "                System.out.println(\"foo\");\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "            System.out.println(\"bar\");\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.UNLOOPED_WHILE);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testMapMethodRatherThanKeySetMethod() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public int replaceUnnecesaryCallsToMapKeySet(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        int x = map.keySet().size();\n" //
				+ "\n" //
				+ "        if (map.keySet().contains(\"hello\")) {\n" //
				+ "            map.keySet().remove(\"hello\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        if (map.keySet().remove(\"world\")) {\n" //
				+ "            // Cannot replace, because `map.removeKey(\"world\") != null` is not strictly equivalent\n" //
				+ "            System.out.println(map);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        map.keySet().clear();\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        if (map.keySet().isEmpty()) {\n" //
				+ "            x++;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return x;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.USE_DIRECTLY_MAP_METHOD);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public int replaceUnnecesaryCallsToMapKeySet(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        int x = map.size();\n" //
				+ "\n" //
				+ "        if (map.containsKey(\"hello\")) {\n" //
				+ "            map.remove(\"hello\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        if (map.keySet().remove(\"world\")) {\n" //
				+ "            // Cannot replace, because `map.removeKey(\"world\") != null` is not strictly equivalent\n" //
				+ "            System.out.println(map);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        map.clear();\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        if (map.isEmpty()) {\n" //
				+ "            x++;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return x;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testMapMethodRatherThanValuesMethod() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public int replaceUnnecesaryCallsToMapValues(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        int x = map.values().size();\n" //
				+ "\n" //
				+ "        if (map.values().contains(\"hello\")) {\n" //
				+ "            map.values().remove(\"hello\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        if (map.values().remove(\"world\")) {\n" //
				+ "            System.out.println(map);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        map.values().clear();\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        if (map.values().contains(\"foo\")) {\n" //
				+ "            x++;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return x;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.USE_DIRECTLY_MAP_METHOD);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public int replaceUnnecesaryCallsToMapValues(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        int x = map.size();\n" //
				+ "\n" //
				+ "        if (map.containsValue(\"hello\")) {\n" //
				+ "            map.values().remove(\"hello\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        if (map.values().remove(\"world\")) {\n" //
				+ "            System.out.println(map);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        map.clear();\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        if (map.containsValue(\"foo\")) {\n" //
				+ "            x++;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return x;\n" //
				+ "    }\n" //
				+ "}\n";

		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.UseDirectlyMapMethodCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testDoNotUseMapMethodInsideMapImplementation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.HashMap;\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1<K,V> extends HashMap<K,V> {\n" //
				+ "    @Override\n" //
				+ "    public boolean containsKey(Object key) {\n" //
				+ "        return keySet().contains(key);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.USE_DIRECTLY_MAP_METHOD);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testDoNotUseMapMethodInsideThisMapImplementation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.HashMap;\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1<K,V> extends HashMap<K,V> {\n" //
				+ "    @Override\n" //
				+ "    public boolean containsKey(Object key) {\n" //
				+ "        return this.keySet().contains(key);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.USE_DIRECTLY_MAP_METHOD);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testCloneCollection() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.Map;\n" //
				+ "import java.util.Map.Entry;\n" //
				+ "import java.util.Stack;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void replaceNewNoArgsAssignmentThenAddAll(List<String> col, List<String> output) {\n" //
				+ "        // Keep this comment\n" //
				+ "        output = new ArrayList<>();\n" //
				+ "        output.addAll(col);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<String> replaceNewNoArgsThenAddAll(List<String> col) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final List<String> output = new ArrayList<>();\n" //
				+ "        output.addAll(col);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<Date> replaceNewOneArgThenAddAll(List<Date> col) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final List<Date> output = new ArrayList<>(0);\n" //
				+ "        output.addAll(col);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<Integer> replaceNewCollectionSizeThenAddAll(List<Integer> col, List<List<Integer>> listOfCol) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final List<Integer> output = new ArrayList<>(col.size());\n" //
				+ "        output.addAll(col);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Object replaceNewThenAddAllParameterizedType(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        List<Entry<String, String>> output = new ArrayList<Entry<String, String>>();\n" //
				+ "        output.addAll(map.entrySet());\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.COLLECTION_CLONING);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.Map;\n" //
				+ "import java.util.Map.Entry;\n" //
				+ "import java.util.Stack;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void replaceNewNoArgsAssignmentThenAddAll(List<String> col, List<String> output) {\n" //
				+ "        // Keep this comment\n" //
				+ "        output = new ArrayList<>(col);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<String> replaceNewNoArgsThenAddAll(List<String> col) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final List<String> output = new ArrayList<>(col);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<Date> replaceNewOneArgThenAddAll(List<Date> col) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final List<Date> output = new ArrayList<>(col);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<Integer> replaceNewCollectionSizeThenAddAll(List<Integer> col, List<List<Integer>> listOfCol) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final List<Integer> output = new ArrayList<>(col);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Object replaceNewThenAddAllParameterizedType(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        List<Entry<String, String>> output = new ArrayList<Entry<String, String>>(map.entrySet());\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "}\n";

		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.CollectionCloningCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testDoNotCloneCollection() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.Stack;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void doNotReplaceStackCtor(List<String> col, List<String> output) {\n" //
				+ "        output = new Stack<>();\n" //
				+ "        output.addAll(col);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<String> doNotReplaceAlreadyInitedCol(List<String> col1, List<String> col2) {\n" //
				+ "        final List<String> output = new ArrayList<>(col1);\n" //
				+ "        output.addAll(col2);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<String> doNotReplaceWithSpecificSize(List<String> col) {\n" //
				+ "        final List<String> output = new ArrayList<>(10);\n" //
				+ "        output.addAll(col);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<Object> doNotReplaceNewThenAddAllIncompatibleTypes(List<String> col) {\n" //
				+ "        final List<Object> output = new ArrayList<>();\n" //
				+ "        output.addAll(col);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.COLLECTION_CLONING);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testCloneMap() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.HashMap;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void replaceNewNoArgsAssignmentThenPutAll(Map<String, String> map, Map<String, String> output) {\n" //
				+ "        // Keep this comment\n" //
				+ "        output = new HashMap<>();\n" //
				+ "        output.putAll(map);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceNewNoArgsThenPutAll(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>();\n" //
				+ "        output.putAll(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceNew0ArgThenPutAll(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(0);\n" //
				+ "        output.putAll(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceNew1ArgThenPutAll(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(0);\n" //
				+ "        output.putAll(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceNewMapSizeThenPutAll(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(map.size());\n" //
				+ "        output.putAll(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceWithSizeOfSubMap(List<Map<String, String>> listOfMap) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(listOfMap.get(0).size());\n" //
				+ "        output.putAll(listOfMap.get(0));\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MAP_CLONING);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.HashMap;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public void replaceNewNoArgsAssignmentThenPutAll(Map<String, String> map, Map<String, String> output) {\n" //
				+ "        // Keep this comment\n" //
				+ "        output = new HashMap<>(map);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceNewNoArgsThenPutAll(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceNew0ArgThenPutAll(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceNew1ArgThenPutAll(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceNewMapSizeThenPutAll(Map<String, String> map) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> replaceWithSizeOfSubMap(List<Map<String, String>> listOfMap) {\n" //
				+ "        // Keep this comment\n" //
				+ "        final Map<String, String> output = new HashMap<>(listOfMap.get(0));\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "}\n";

		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(MultiFixMessages.MapCloningCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testDoNotCloneMap() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.HashMap;\n" //
				+ "import java.util.Map;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public Map<String, String> doNotReplaceAlreadyInitedMap(Map<String, String> map1, Map<String, String> map2) {\n" //
				+ "        final Map<String, String> output = new HashMap<>(map1);\n" //
				+ "        output.putAll(map2);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> doNotReplaceWithSpecificSize(Map<String, String> map) {\n" //
				+ "        Map<String, String> output = new HashMap<>(10);\n" //
				+ "        output.putAll(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<Object, Object> doNotReplaceNewThenAddAllIncompatibleTypes(Map<String, String> map) {\n" //
				+ "        final Map<Object, Object> output = new HashMap<>();\n" //
				+ "        output.putAll(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public Map<String, String> doNotReplaceAnonymousMap(Map<String, String> map) {\n" //
				+ "        final Map<String, String> output = new HashMap<>() {\n" //
				+ "            private static final long serialVersionUID= 1L;\n" //
				+ "\n" //
				+ "            @Override\n" //
				+ "            public void putAll(Map<? extends String, ? extends String> map) {\n" //
				+ "                // Drop the map\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "        output.putAll(map);\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MAP_CLONING);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testOverriddenAssignment() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.File;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public boolean removeUselessInitialization() {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean reassignedVar = true;\n" //
				+ "        reassignedVar = \"\\n\".equals(File.pathSeparator);\n" //
				+ "        return reassignedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public long removeInitForLong() {\n" //
				+ "        // Keep this comment\n" //
				+ "        long reassignedVar = 0;\n" //
				+ "        reassignedVar = System.currentTimeMillis();\n" //
				+ "        return reassignedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String removeInitForString() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String reassignedVar = \"\";\n" //
				+ "        reassignedVar = File.pathSeparator;\n" //
				+ "        return reassignedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean removePassiveInitialization(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean reassignedPassiveVar = i > 0;\n" //
				+ "        reassignedPassiveVar = \"\\n\".equals(File.pathSeparator);\n" //
				+ "        return reassignedPassiveVar;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.OVERRIDDEN_ASSIGNMENT);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.File;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public boolean removeUselessInitialization() {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean reassignedVar;\n" //
				+ "        reassignedVar = \"\\n\".equals(File.pathSeparator);\n" //
				+ "        return reassignedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public long removeInitForLong() {\n" //
				+ "        // Keep this comment\n" //
				+ "        long reassignedVar;\n" //
				+ "        reassignedVar = System.currentTimeMillis();\n" //
				+ "        return reassignedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String removeInitForString() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String reassignedVar;\n" //
				+ "        reassignedVar = File.pathSeparator;\n" //
				+ "        return reassignedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean removePassiveInitialization(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean reassignedPassiveVar;\n" //
				+ "        reassignedPassiveVar = \"\\n\".equals(File.pathSeparator);\n" //
				+ "        return reassignedPassiveVar;\n" //
				+ "    }\n" //
				+ "}\n";

		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.OverriddenAssignmentCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotRemoveAssignment() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.io.File;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public boolean doNotRemoveWithOrAssignment() {\n" //
				+ "        boolean isValid = true;\n" //
				+ "        isValid |= false;\n" //
				+ "        isValid = false;\n" //
				+ "        return isValid;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public long doNotRemoveWithMinusAssignment() {\n" //
				+ "        long decrementedVar = 123;\n" //
				+ "        decrementedVar -= 456;\n" //
				+ "        decrementedVar = 789;\n" //
				+ "        return decrementedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public long doNotRemoveWithEmbeddedPlusAssignment() {\n" //
				+ "        long incrementedVar = 123;\n" //
				+ "        long dummy = incrementedVar += 456;\n" //
				+ "        incrementedVar = 789;\n" //
				+ "        return incrementedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public List<String> doNotRemoveActiveInit() {\n" //
				+ "        List<String> aList = Arrays.asList(\"lorem\", \"ipsum\");\n" //
				+ "\n" //
				+ "        boolean reassignedVar = aList.remove(\"lorem\");\n" //
				+ "        reassignedVar = \"\\n\".equals(File.pathSeparator);\n" //
				+ "        return aList;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRemoveInitWithoutOverriding() {\n" //
				+ "        String usedVar = \"\";\n" //
				+ "        return usedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRemoveInitWithUse() {\n" //
				+ "        String usedVar = \"\";\n" //
				+ "        System.out.println(usedVar);\n" //
				+ "        usedVar = File.pathSeparator;\n" //
				+ "        return usedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRemoveInitWithUseInIf() {\n" //
				+ "        String usedVar = \"\";\n" //
				+ "        if (\"\\n\".equals(File.pathSeparator)) {\n" //
				+ "            System.out.println(usedVar);\n" //
				+ "        }\n" //
				+ "        usedVar = File.pathSeparator;\n" //
				+ "        return usedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRemoveInitWithCall() {\n" //
				+ "        String usedVar = \"\";\n" //
				+ "        usedVar.length();\n" //
				+ "        usedVar = File.pathSeparator;\n" //
				+ "        return usedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public char[] doNotRemoveInitWithIndex() {\n" //
				+ "        char[] usedVar = new char[] {'a', 'b', 'c'};\n" //
				+ "        char oneChar = usedVar[1];\n" //
				+ "        usedVar = new char[] {'d', 'e', 'f'};\n" //
				+ "        return usedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public byte doNotRemoveInitWhenUsed() {\n" //
				+ "        byte usedVar = 0;\n" //
				+ "        usedVar = usedVar++;\n" //
				+ "        return usedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRemoveInitWhenOverriddenInIf() {\n" //
				+ "        String usedVar = \"\";\n" //
				+ "        if (\"\\n\".equals(File.pathSeparator)) {\n" //
				+ "            usedVar = File.pathSeparator;\n" //
				+ "        }\n" //
				+ "        return usedVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotRemoveActiveInitialization(List<String> aList) {\n" //
				+ "        boolean reassignedActiveVar = aList.remove(\"foo\");\n" //
				+ "        reassignedActiveVar = \"\\n\".equals(File.pathSeparator);\n" //
				+ "        return reassignedActiveVar;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRemoveInitializationWithIncrement(int i) {\n" //
				+ "        int reassignedActiveVar = i++;\n" //
				+ "        reassignedActiveVar = 123;\n" //
				+ "        return reassignedActiveVar + i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public long doNotRemoveInitializationWithAssignment(long i, long j) {\n" //
				+ "        long reassignedActiveVar = i = j;\n" //
				+ "        reassignedActiveVar = 123;\n" //
				+ "        return reassignedActiveVar + i + j;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.OVERRIDDEN_ASSIGNMENT);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testAddBlockBug149110_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true)\n" //
				+ "            throw new IllegalAccessError();\n" //
				+ "        if (true) {\n" //
				+ "            throw new IllegalAccessError();\n" //
				+ "        }\n" //
				+ "        if (false)\n" //
				+ "            System.out.println();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NO_FOR_RETURN_AND_THROW);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true)\n" //
				+ "            throw new IllegalAccessError();\n" //
				+ "        if (true)\n" //
				+ "            throw new IllegalAccessError();\n" //
				+ "        if (false) {\n" //
				+ "            System.out.println();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testAddBlockBug149110_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true)\n" //
				+ "            return;\n" //
				+ "        if (true) {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        if (false)\n" //
				+ "            System.out.println();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NO_FOR_RETURN_AND_THROW);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true)\n" //
				+ "            return;\n" //
				+ "        if (true)\n" //
				+ "            return;\n" //
				+ "        if (false) {\n" //
				+ "            System.out.println();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testRemoveBlock01() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void if_() {\n" //
				+ "        if (true) {\n" //
				+ "            ;\n" //
				+ "        } else if (false) {\n" //
				+ "            ;\n" //
				+ "        } else {\n" //
				+ "            ;\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        if (true) {\n" //
				+ "            ;;\n" //
				+ "        } else if (false) {\n" //
				+ "            ;;\n" //
				+ "        } else {\n" //
				+ "            ;;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void if_() {\n" //
				+ "        if (true)\n" //
				+ "            ;\n" //
				+ "        else if (false)\n" //
				+ "            ;\n" //
				+ "        else\n" //
				+ "            ;\n" //
				+ "        \n" //
				+ "        if (true) {\n" //
				+ "            ;;\n" //
				+ "        } else if (false) {\n" //
				+ "            ;;\n" //
				+ "        } else {\n" //
				+ "            ;;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveBlock02() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        for (;;) {\n" //
				+ "            ; \n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void bar() {\n" //
				+ "        for (;;) {\n" //
				+ "            ;; \n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        for (;;);\n" //
				+ "    }\n" //
				+ "    public void bar() {\n" //
				+ "        for (;;) {\n" //
				+ "            ;; \n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveBlock03() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        while (true) {\n" //
				+ "            ;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void bar() {\n" //
				+ "        while (true) {\n" //
				+ "            ;;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        while (true);\n" //
				+ "    }\n" //
				+ "    public void bar() {\n" //
				+ "        while (true) {\n" //
				+ "            ;;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveBlock04() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        do {\n" //
				+ "            ;\n" //
				+ "        } while (true);\n" //
				+ "    }\n" //
				+ "    public void bar() {\n" //
				+ "        do {\n" //
				+ "            ;;\n" //
				+ "        } while (true);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        do; while (true);\n" //
				+ "    }\n" //
				+ "    public void bar() {\n" //
				+ "        do {\n" //
				+ "            ;;\n" //
				+ "        } while (true);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveBlock05() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        int[] is= null;\n" //
				+ "        for (int i= 0;i < is.length;i++) {\n" //
				+ "            ;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        int[] is= null;\n" //
				+ "        for (int element : is);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveBlockBug138628() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true) {\n" //
				+ "            if (true)\n" //
				+ "                ;\n" //
				+ "        } else if (true) {\n" //
				+ "            if (false) {\n" //
				+ "                ;\n" //
				+ "            } else\n" //
				+ "                ;\n" //
				+ "        } else if (false) {\n" //
				+ "            if (true) {\n" //
				+ "                ;\n" //
				+ "            }\n" //
				+ "        } else {\n" //
				+ "            if (true)\n" //
				+ "                ;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        if (true) {\n" //
				+ "            if (true)\n" //
				+ "                ;\n" //
				+ "        } else if (true) {\n" //
				+ "            if (false)\n" //
				+ "                ;\n" //
				+ "            else\n" //
				+ "                ;\n" //
				+ "        } else if (false) {\n" //
				+ "            if (true)\n" //
				+ "                ;\n" //
				+ "        } else if (true)\n" //
				+ "            ;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveBlockBug149990() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        if (false) {\n" //
				+ "            while (true) {\n" //
				+ "                if (false) {\n" //
				+ "                    ;\n" //
				+ "                }\n" //
				+ "            }\n" //
				+ "        } else\n" //
				+ "            ;\n" //
				+ "    }\n" //
				+ "}";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        if (false) {\n" //
				+ "            while (true)\n" //
				+ "                if (false)\n" //
				+ "                    ;\n" //
				+ "        } else\n" //
				+ "            ;\n" //
				+ "    }\n" //
				+ "}";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveBlockBug156513_1() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(boolean b, int[] ints) {\n" //
				+ "        if (b) {\n" //
				+ "            for (int i = 0; i < ints.length; i++) {\n" //
				+ "                System.out.println(ints[i]);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);
		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(boolean b, int[] ints) {\n" //
				+ "        if (b)\n" //
				+ "            for (int j : ints)\n" //
				+ "                System.out.println(j);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveBlockBug156513_2() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(boolean b, int[] ints) {\n" //
				+ "        for (int i = 0; i < ints.length; i++) {\n" //
				+ "            for (int j = 0; j < ints.length; j++) {\n" //
				+ "                System.out.println(ints[j]);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);
		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(boolean b, int[] ints) {\n" //
				+ "        for (int k : ints)\n" //
				+ "            for (int l : ints)\n" //
				+ "                System.out.println(l);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testElseIf() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void refactor(boolean isValid, boolean isEnabled) throws Exception {\n" //
				+ "        if (isValid) {\n" //
				+ "            // Keep this comment\n" //
				+ "            System.out.println(isValid);\n" //
				+ "        } else {\n" //
				+ "            if (isEnabled) {\n" //
				+ "                // Keep this comment\n" //
				+ "                System.out.println(isEnabled);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.ELSE_IF);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void refactor(boolean isValid, boolean isEnabled) throws Exception {\n" //
				+ "        if (isValid) {\n" //
				+ "            // Keep this comment\n" //
				+ "            System.out.println(isValid);\n" //
				+ "        } else if (isEnabled) {\n" //
				+ "            // Keep this comment\n" //
				+ "            System.out.println(isEnabled);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", input, output);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.CodeStyleCleanUp_ElseIf_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotUseElseIf() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void doNotRefactor(boolean isValid, boolean isEnabled) throws Exception {\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(isValid);\n" //
				+ "        } else if (isEnabled) {\n" //
				+ "            System.out.println(isEnabled);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotLoseRemainingStatements(boolean isValid, boolean isEnabled) throws Exception {\n" //
				+ "        if (isValid) {\n" //
				+ "            System.out.println(isValid);\n" //
				+ "        } else {\n" //
				+ "            if (isEnabled) {\n" //
				+ "                System.out.println(isEnabled);\n" //
				+ "            }\n" //
				+ "\n" //
				+ "            System.out.println(\"Don't forget me!\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.ELSE_IF);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testReduceIndentation() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Calendar;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private Date conflictingName = new Date();\n" //
				+ "\n" //
				+ "    public int refactorThen(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            i = i + 1;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElse(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            i = i + 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorWithTryCatch(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            try {\n" //
				+ "                throw new Exception();\n" //
				+ "            } catch(Exception e) {\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorIndentation(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorInTry(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            if (i > 0) {\n" //
				+ "                // Keep this comment too\n" //
				+ "                return 1;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment also\n" //
				+ "                return 2;\n" //
				+ "            }\n" //
				+ "        } catch (Exception e) {\n" //
				+ "            e.printStackTrace();\n" //
				+ "        }\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int reduceIndentationFromElse(int i, List<Integer> integers) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            for (Integer integer : integers) {\n" //
				+ "                System.out.println(\"Reading \" + integer);\n" //
				+ "            }\n" //
				+ "            return 51;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int reduceIndentationFromIf(int i, List<Integer> integers) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            for (Integer integer : integers) {\n" //
				+ "                System.out.println(\"Reading \" + integer);\n" //
				+ "            }\n" //
				+ "\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 51;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int negateCommentedCondition(int i, List<Integer> integers) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0 /* comment */) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            for (Integer integer : integers) {\n" //
				+ "                System.out.println(\"Reading \" + integer);\n" //
				+ "            }\n" //
				+ "\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 51;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int reduceBigIndentationFromIf(int i, List<String> integers) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            try {\n" //
				+ "                for (String integer : integers) {\n" //
				+ "                    System.out.println(\"Reading \" + (Integer.parseInt(integer) + 100));\n" //
				+ "                }\n" //
				+ "            } catch (Exception e) {\n" //
				+ "                e.printStackTrace();\n" //
				+ "            }\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 51;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorThenInUnbrackettedForLoop(int[] integers) {\n" //
				+ "        for (int integer : integers)\n" //
				+ "            if (integer > 0) {\n" //
				+ "                // Keep this comment too\n" //
				+ "                integer = integer + 1;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInUnbrackettedForLoop(double[] reals) {\n" //
				+ "        for (double real : reals)\n" //
				+ "            if (real > 0) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment too\n" //
				+ "                real = real + 1;\n" //
				+ "                System.out.println(\"New value: \" + real);\n" //
				+ "            }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInSwitch(int discriminant, boolean isVisible) {\n" //
				+ "        switch (discriminant) {\n" //
				+ "        case 0:\n" //
				+ "            if (isVisible) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment too\n" //
				+ "                discriminant = discriminant + 1;\n" //
				+ "                System.out.println(\"New value: \" + discriminant);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInTry(int discriminant, boolean isVisible) {\n" //
				+ "        try {\n" //
				+ "            if (isVisible) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment too\n" //
				+ "                discriminant = discriminant + 1;\n" //
				+ "                System.out.println(\"New value: \" + discriminant);\n" //
				+ "            }\n" //
				+ "        } finally {\n" //
				+ "            System.out.println(\"Finally\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInCatch(int discriminant, boolean isVisible) {\n" //
				+ "        try {\n" //
				+ "            System.out.println(\"Very dangerous code\");\n" //
				+ "        } catch (Exception e) {\n" //
				+ "            if (isVisible) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment too\n" //
				+ "                discriminant = discriminant + 1;\n" //
				+ "                System.out.println(\"New value: \" + discriminant);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInFinally(int discriminant, boolean isVisible) {\n" //
				+ "        try {\n" //
				+ "            System.out.println(\"Very dangerous code\");\n" //
				+ "        } finally {\n" //
				+ "            if (isVisible) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment too\n" //
				+ "                discriminant = discriminant + 1;\n" //
				+ "                System.out.println(\"New value: \" + discriminant);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorWithoutNameConflict(int i) {\n" //
				+ "        System.out.println(\"Today: \" + conflictingName);\n" //
				+ "\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            int conflictingName = 123;\n" //
				+ "\n" //
				+ "            i = i + conflictingName;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorWithThrow(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            throw new IllegalArgumentException(\"Positive argument\");\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            i = i + 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorWithContinue(List<Integer> integers) {\n" //
				+ "        for (Integer integer : integers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (integer > 0) {\n" //
				+ "                // Keep this comment too\n" //
				+ "                continue;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment also\n" //
				+ "                System.out.println(integer);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorWithBreak(List<Integer> integers) {\n" //
				+ "        for (Integer integer : integers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (integer > 0) {\n" //
				+ "                // Keep this comment too\n" //
				+ "                break;\n" //
				+ "            } else {\n" //
				+ "                // Keep this comment also\n" //
				+ "                System.out.println(integer);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElse(List<Date> dates) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (dates.isEmpty()) {\n" //
				+ "            return 0;\n" //
				+ "        } else\n" //
				+ "            return 1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorUnparameterizedReturn(List<Date> dates) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (dates.isEmpty()) {\n" //
				+ "        } else {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorEmptyElse(List<Date> dates) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (dates.isEmpty()) {\n" //
				+ "            return;\n" //
				+ "        } else {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Calendar;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private Date conflictingName = new Date();\n" //
				+ "\n" //
				+ "    public int refactorThen(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "        // Keep this comment too\n" //
				+ "        i = i + 1;\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElse(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "        // Keep this comment also\n" //
				+ "        i = i + 1;\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorWithTryCatch(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "        // Keep this comment also\n" //
				+ "        try {\n" //
				+ "            throw new Exception();\n" //
				+ "        } catch(Exception e) {\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorIndentation(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "        // Keep this comment also\n" //
				+ "        return 1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorInTry(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        try {\n" //
				+ "            if (i > 0) {\n" //
				+ "                // Keep this comment too\n" //
				+ "                return 1;\n" //
				+ "            }\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 2;\n" //
				+ "        } catch (Exception e) {\n" //
				+ "            e.printStackTrace();\n" //
				+ "        }\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int reduceIndentationFromElse(int i, List<Integer> integers) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "        // Keep this comment also\n" //
				+ "        for (Integer integer : integers) {\n" //
				+ "            System.out.println(\"Reading \" + integer);\n" //
				+ "        }\n" //
				+ "        return 51;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int reduceIndentationFromIf(int i, List<Integer> integers) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 51;\n" //
				+ "        }\n" //
				+ "        // Keep this comment too\n" //
				+ "        for (Integer integer : integers) {\n" //
				+ "            System.out.println(\"Reading \" + integer);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int negateCommentedCondition(int i, List<Integer> integers) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0 /* comment */) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 51;\n" //
				+ "        }\n" //
				+ "        // Keep this comment too\n" //
				+ "        for (Integer integer : integers) {\n" //
				+ "            System.out.println(\"Reading \" + integer);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int reduceBigIndentationFromIf(int i, List<String> integers) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i <= 0) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 51;\n" //
				+ "        }\n" //
				+ "        // Keep this comment too\n" //
				+ "        try {\n" //
				+ "            for (String integer : integers) {\n" //
				+ "                System.out.println(\"Reading \" + (Integer.parseInt(integer) + 100));\n" //
				+ "            }\n" //
				+ "        } catch (Exception e) {\n" //
				+ "            e.printStackTrace();\n" //
				+ "        }\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorThenInUnbrackettedForLoop(int[] integers) {\n" //
				+ "        for (int integer : integers) {\n" //
				+ "            if (integer <= 0) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            }\n" //
				+ "            // Keep this comment too\n" //
				+ "            integer = integer + 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInUnbrackettedForLoop(double[] reals) {\n" //
				+ "        for (double real : reals) {\n" //
				+ "            if (real > 0) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            }\n" //
				+ "            // Keep this comment too\n" //
				+ "            real = real + 1;\n" //
				+ "            System.out.println(\"New value: \" + real);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInSwitch(int discriminant, boolean isVisible) {\n" //
				+ "        switch (discriminant) {\n" //
				+ "        case 0:\n" //
				+ "            if (isVisible) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            }\n" //
				+ "                // Keep this comment too\n" //
				+ "                discriminant = discriminant + 1;\n" //
				+ "                System.out.println(\"New value: \" + discriminant);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInTry(int discriminant, boolean isVisible) {\n" //
				+ "        try {\n" //
				+ "            if (isVisible) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            }\n" //
				+ "            // Keep this comment too\n" //
				+ "            discriminant = discriminant + 1;\n" //
				+ "            System.out.println(\"New value: \" + discriminant);\n" //
				+ "        } finally {\n" //
				+ "            System.out.println(\"Finally\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInCatch(int discriminant, boolean isVisible) {\n" //
				+ "        try {\n" //
				+ "            System.out.println(\"Very dangerous code\");\n" //
				+ "        } catch (Exception e) {\n" //
				+ "            if (isVisible) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            }\n" //
				+ "            // Keep this comment too\n" //
				+ "            discriminant = discriminant + 1;\n" //
				+ "            System.out.println(\"New value: \" + discriminant);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElseInFinally(int discriminant, boolean isVisible) {\n" //
				+ "        try {\n" //
				+ "            System.out.println(\"Very dangerous code\");\n" //
				+ "        } finally {\n" //
				+ "            if (isVisible) {\n" //
				+ "                // Keep this comment\n" //
				+ "                return 0;\n" //
				+ "            }\n" //
				+ "            // Keep this comment too\n" //
				+ "            discriminant = discriminant + 1;\n" //
				+ "            System.out.println(\"New value: \" + discriminant);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return -1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorWithoutNameConflict(int i) {\n" //
				+ "        System.out.println(\"Today: \" + conflictingName);\n" //
				+ "\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "        // Keep this comment also\n" //
				+ "        int conflictingName = 123;\n" //
				+ "\n" //
				+ "        i = i + conflictingName;\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorWithThrow(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            throw new IllegalArgumentException(\"Positive argument\");\n" //
				+ "        }\n" //
				+ "        // Keep this comment also\n" //
				+ "        i = i + 1;\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorWithContinue(List<Integer> integers) {\n" //
				+ "        for (Integer integer : integers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (integer > 0) {\n" //
				+ "                // Keep this comment too\n" //
				+ "                continue;\n" //
				+ "            }\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(integer);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorWithBreak(List<Integer> integers) {\n" //
				+ "        for (Integer integer : integers) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (integer > 0) {\n" //
				+ "                // Keep this comment too\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "            // Keep this comment also\n" //
				+ "            System.out.println(integer);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int refactorElse(List<Date> dates) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (dates.isEmpty()) {\n" //
				+ "            return 0;\n" //
				+ "        }\n" //
				+ "        return 1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorUnparameterizedReturn(List<Date> dates) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (!dates.isEmpty()) {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorEmptyElse(List<Date> dates) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (dates.isEmpty()) {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.REDUCE_INDENTATION);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.CodeStyleCleanUp_ReduceIndentation_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotReduceIndentation() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Date;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private Date conflictingName = new Date();\n" //
				+ "\n" //
				+ "    public int doNotRefactorWithNameConflict(int i) {\n" //
				+ "        if (i > 0) {\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            int conflictingName = 123;\n" //
				+ "            i = i + conflictingName;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        int conflictingName = 321;\n" //
				+ "\n" //
				+ "        return i + conflictingName;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorWithNameConfusion(int i) {\n" //
				+ "        if (i > 0) {\n" //
				+ "            return 0;\n" //
				+ "        } else {\n" //
				+ "            int conflictingName = 123;\n" //
				+ "            i = i + conflictingName;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        System.out.println(\"Today: \" + conflictingName);\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorWithNameConfusion(int i, int discriminant) {\n" //
				+ "        switch (discriminant) {\n" //
				+ "        case 0:\n" //
				+ "            if (i > 0) {\n" //
				+ "                return 0;\n" //
				+ "            } else {\n" //
				+ "                int conflictingName = 123;\n" //
				+ "                i = i + conflictingName;\n" //
				+ "            }\n" //
				+ "\n" //
				+ "            System.out.println(\"Today: \" + conflictingName);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.REDUCE_INDENTATION);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testUnnecessaryCodeBug127704_1() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    private boolean foo() {\n" //
				+ "        return (boolean) (Boolean) Boolean.TRUE;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_CASTS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    private boolean foo() {\n" //
				+ "        return Boolean.TRUE;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testUnnecessaryCodeBug127704_2() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    private Integer foo() {\n" //
				+ "        return (Integer) (Number) getNumber();\n" //
				+ "    }\n" //
				+ "    private Number getNumber() {return null;}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.REMOVE_UNNECESSARY_CASTS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    private Integer foo() {\n" //
				+ "        return (Integer) getNumber();\n" //
				+ "    }\n" //
				+ "    private Number getNumber() {return null;}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testAddParentheses01() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    void foo(int i) {\n" //
				+ "        if (i == 0 || i == 1)\n" //
				+ "            System.out.println(i);\n" //
				+ "        \n" //
				+ "        while (i > 0 && i < 10)\n" //
				+ "            System.out.println(1);\n" //
				+ "        \n" //
				+ "        boolean b= i != -1 && i > 10 && i < 100 || i > 20;\n" //
				+ "        \n" //
				+ "        do ; while (i > 5 && b || i < 100 && i > 90);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    void foo(int i) {\n" //
				+ "        if ((i == 0) || (i == 1)) {\n" //
				+ "            System.out.println(i);\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        while ((i > 0) && (i < 10)) {\n" //
				+ "            System.out.println(1);\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        boolean b= ((i != -1) && (i > 10) && (i < 100)) || (i > 20);\n" //
				+ "        \n" //
				+ "        do {\n" //
				+ "            ;\n" //
				+ "        } while (((i > 5) && b) || ((i < 100) && (i > 90)));\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testAddParentheses02() throws Exception {
		//https://bugs.eclipse.org/bugs/show_bug.cgi?id=331845
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    void foo(int i, int j) {\n" //
				+ "        if (i + 10 != j - 5)\n" //
				+ "            System.out.println(i - j + 10 - i * j);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_ALWAYS);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    void foo(int i, int j) {\n" //
				+ "        if ((i + 10) != (j - 5)) {\n" //
				+ "            System.out.println(((i - j) + 10) - (i * j));\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParentheses01() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    void foo(int i) {\n" //
				+ "        if ((i == 0) || (i == 1)) {\n" //
				+ "            System.out.println(i);\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        while ((i > 0) && (i < 10)) {\n" //
				+ "            System.out.println(1);\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        boolean b= ((i != -1) && (i > 10) && (i < 100)) || (i > 20);\n" //
				+ "        \n" //
				+ "        do {\n" //
				+ "            ;\n" //
				+ "        } while (((i > 5) && b) || ((i < 100) && (i > 90)));\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E {\n" //
				+ "    void foo(int i) {\n" //
				+ "        if (i == 0 || i == 1)\n" //
				+ "            System.out.println(i);\n" //
				+ "        \n" //
				+ "        while (i > 0 && i < 10)\n" //
				+ "            System.out.println(1);\n" //
				+ "        \n" //
				+ "        boolean b= i != -1 && i > 10 && i < 100 || i > 20;\n" //
				+ "        \n" //
				+ "        do; while (i > 5 && b || i < 100 && i > 90);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveParenthesesBug134739() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(boolean a) {\n" //
				+ "        if (((a)))\n" //
				+ "            return;\n" //
				+ "    }\n" //
				+ "    public void bar(boolean a, boolean b) {\n" //
				+ "        if (((a)) || ((b)))\n" //
				+ "            return;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NEVER);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(boolean a) {\n" //
				+ "        if (a)\n" //
				+ "            return;\n" //
				+ "    }\n" //
				+ "    public void bar(boolean a, boolean b) {\n" //
				+ "        if (a || b)\n" //
				+ "            return;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveParenthesesBug134741_1() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public boolean foo(Object o) {\n" //
				+ "        if ((((String)o)).equals(\"\"))\n" //
				+ "            return true;\n" //
				+ "        return false;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public boolean foo(Object o) {\n" //
				+ "        if (((String)o).equals(\"\"))\n" //
				+ "            return true;\n" //
				+ "        return false;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveParenthesesBug134741_2() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(boolean a) {\n" //
				+ "        if ((\"\" + \"b\").equals(\"a\"))\n" //
				+ "            return;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testRemoveParenthesesBug134741_3() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public String foo2(String s) {\n" //
				+ "        return (s != null ? s : \"\").toString();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testRemoveParenthesesBug134985_1() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public boolean foo(String s1, String s2, boolean a, boolean b) {\n" //
				+ "        return (a == b) == (s1 == s2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public boolean foo(String s1, String s2, boolean a, boolean b) {\n" //
				+ "        return a == b == (s1 == s2);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testRemoveParenthesesBug134985_2() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public String foo() {\n" //
				+ "        return (\"\" + 3) + (3 + 3);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public String foo() {\n" //
				+ "        return \"\" + 3 + (3 + 3);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testRemoveParenthesesBug188207() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public int foo() {\n" //
				+ "        boolean b= (true ? true : (true ? false : true));\n" //
				+ "        return ((b ? true : true) ? 0 : 1);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public int foo() {\n" //
				+ "        boolean b= true ? true : true ? false : true;\n" //
				+ "        return (b ? true : true) ? 0 : 1;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testRemoveParenthesesBug208752() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        double d = 2.0 * (0.5 / 4.0);\n" //
				+ "        int spaceCount = (3);\n" //
				+ "        spaceCount = 2 * (spaceCount / 2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        double d = 2.0 * (0.5 / 4.0);\n" //
				+ "        int spaceCount = 3;\n" //
				+ "        spaceCount = 2 * (spaceCount / 2);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testRemoveParenthesesBug190188() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        (new Object()).toString();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        new Object().toString();\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testRemoveParenthesesBug212856() throws Exception {

		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo() {\n" //
				+ "        int n= 1 + (2 - 3);\n" //
				+ "        n= 1 - (2 + 3);\n" //
				+ "        n= 1 - (2 - 3);\n" //
				+ "        n= 1 * (2 * 3);\n" //
				+ "        return 2 * (n % 10);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo() {\n" //
				+ "        int n= 1 + 2 - 3;\n" //
				+ "        n= 1 - (2 + 3);\n" //
				+ "        n= 1 - (2 - 3);\n" //
				+ "        n= 1 * 2 * 3;\n" //
				+ "        return 2 * (n % 10);\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testRemoveParenthesesBug335173_1() throws Exception {
		//while loop's expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(boolean a) {\n" //
				+ "        while (((a))) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void bar(int x) {\n" //
				+ "        while ((x > 2)) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(boolean a) {\n" //
				+ "        while (a) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void bar(int x) {\n" //
				+ "        while (x > 2) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_2() throws Exception {
		//do while loop's expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        do {\n" //
				+ "        } while ((x > 2));\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        do {\n" //
				+ "        } while (x > 2);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_3() throws Exception {
		//for loop's expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        for (int x = 0; (x > 2); x++) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        for (int x = 0; x > 2; x++) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_4() throws Exception {
		//switch statement expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        switch ((x - 2)) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        switch (x - 2) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_5() throws Exception {
		//switch case expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        switch (x) {\n" //
				+ "        case (1 + 2):\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        switch (x) {\n" //
				+ "        case 1 + 2:\n" //
				+ "            break;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_6() throws Exception {
		//throw statement expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int type) throws Exception {\n" //
				+ "        throw (type == 1 ? new IllegalArgumentException() : new Exception());\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int type) throws Exception {\n" //
				+ "        throw type == 1 ? new IllegalArgumentException() : new Exception();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_7() throws Exception {
		//synchronized statement expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private static final Object OBJECT = new Object();\n" //
				+ "    private static final String STRING = new String();\n" //
				+ "    \n" //
				+ "    public void foo(int x) {\n" //
				+ "        synchronized ((x == 1 ? STRING : OBJECT)) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private static final Object OBJECT = new Object();\n" //
				+ "    private static final String STRING = new String();\n" //
				+ "    \n" //
				+ "    public void foo(int x) {\n" //
				+ "        synchronized (x == 1 ? STRING : OBJECT) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_8() throws Exception {
		//assert statement expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        assert (x > 2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        assert x > 2;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_9() throws Exception {
		//assert statement message expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        assert x > 2 : (x - 2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        assert x > 2 : x - 2;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_10() throws Exception {
		//array access index expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int a[], int x) {\n" //
				+ "        int i = a[(x + 2)];\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int a[], int x) {\n" //
				+ "        int i = a[x + 2];\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_11() throws Exception {
		//conditional expression's then expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int i = x > 10 ? (x > 5 ? x - 1 : x - 2): x;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int i = x > 10 ? x > 5 ? x - 1 : x - 2: x;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_12() throws Exception {
		//conditional expression's else expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int i = x > 10 ? x: (x > 5 ? x - 1 : x - 2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int i = x > 10 ? x: x > 5 ? x - 1 : x - 2;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_13() throws Exception {
		//conditional expression's then expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int i = x > 10 ? (x = x - 2): x;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int i = x > 10 ? (x = x - 2): x;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_14() throws Exception {
		//conditional expression's else expression
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int i = x > 10 ? x: (x = x - 2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int i = x > 10 ? x: (x = x - 2);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_15() throws Exception {
		//shift operators
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int m= (x >> 2) >> 1;\n" //
				+ "        m= x >> (2 >> 1);\n" //
				+ "        int n= (x << 2) << 1;\n" //
				+ "        n= x << (2 << 1);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x) {\n" //
				+ "        int m= x >> 2 >> 1;\n" //
				+ "        m= x >> (2 >> 1);\n" //
				+ "        int n= x << 2 << 1;\n" //
				+ "        n= x << (2 << 1);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_16() throws Exception {
		//integer multiplication
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x, long y) {\n" //
				+ "        int m= (4 * x) * 2;\n" //
				+ "        int n= 4 * (x * 2);\n" //
				+ "        int p= 4 * (x % 3);\n" //
				+ "        int q= 4 * (x / 3);\n" //
				+ "        int r= 4 * (x * y);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x, long y) {\n" //
				+ "        int m= 4 * x * 2;\n" //
				+ "        int n= 4 * x * 2;\n" //
				+ "        int p= 4 * (x % 3);\n" //
				+ "        int q= 4 * (x / 3);\n" //
				+ "        int r= 4 * (x * y);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_17() throws Exception {
		//floating point multiplication
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(double x) {\n" //
				+ "        int m= (4.0 * x) * 0.5;\n" //
				+ "        int n= 4.0 * (x * 0.5);\n" //
				+ "        int p= 4.0 * (x / 100);\n" //
				+ "        int q= 4.0 * (x % 3);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(double x) {\n" //
				+ "        int m= 4.0 * x * 0.5;\n" //
				+ "        int n= 4.0 * (x * 0.5);\n" //
				+ "        int p= 4.0 * (x / 100);\n" //
				+ "        int q= 4.0 * (x % 3);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_18() throws Exception {
		//integer addition
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x, long y) {\n" //
				+ "        int m= (4 + x) + 2;\n" //
				+ "        int n= 4 + (x + 2);\n" //
				+ "        int p= 4 + (x + y);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(int x, long y) {\n" //
				+ "        int m= 4 + x + 2;\n" //
				+ "        int n= 4 + x + 2;\n" //
				+ "        int p= 4 + (x + y);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_19() throws Exception {
		//floating point addition
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(double x) {\n" //
				+ "        int m= (4.0 + x) + 100.0;\n" //
				+ "        int n= 4.0 + (x + 100.0);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(double x) {\n" //
				+ "        int m= 4.0 + x + 100.0;\n" //
				+ "        int n= 4.0 + (x + 100.0);\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug335173_20() throws Exception {
		//string concatenation
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(String s, String t, String u) {\n" //
				+ "        String a= (s + t) + u;\n" //
				+ "        String b= s + (t + u);\n" //
				+ "        String c= (1 + 2) + s;\n" //
				+ "        String d= 1 + (2 + s);\n" //
				+ "        String e= s + (1 + 2);\n" //
				+ "        String f= (s + 1) + 2;\n" //
				+ "        String g= (1 + s) + 2;\n" //
				+ "        String h= 1 + (s + 2);\n" //
				+ "        String i= s + (1 + t);\n" //
				+ "        String j= s + (t + 1);\n" //
				+ "        String k= s + (1 - 2);\n" //
				+ "        String l= s + (1 * 2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo(String s, String t, String u) {\n" //
				+ "        String a= s + t + u;\n" //
				+ "        String b= s + t + u;\n" //
				+ "        String c= 1 + 2 + s;\n" //
				+ "        String d= 1 + (2 + s);\n" //
				+ "        String e= s + (1 + 2);\n" //
				+ "        String f= s + 1 + 2;\n" //
				+ "        String g= 1 + s + 2;\n" //
				+ "        String h= 1 + s + 2;\n" //
				+ "        String i= s + 1 + t;\n" //
				+ "        String j= s + t + 1;\n" //
				+ "        String k= s + (1 - 2);\n" //
				+ "        String l= s + 1 * 2;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug405096_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    final Short cache[] = new Short[-(-128) + 1];\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testRemoveParenthesesBug405096_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    int a= 10;\n" //
				+ "    final Short cache[] = new Short[-(-a) + 1];\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testRemoveParenthesesBug405096_3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    int a= 10;\n" //
				+ "    final Short cache[] = new Short[-(--a) + 1];\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testRemoveParenthesesBug405096_4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    int a= 10;\n" //
				+ "    final Short cache[] = new Short[+(+a) + 1];\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testRemoveParenthesesBug405096_5() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    int a= 10\n" //
				+ "    final Short cache[] = new Short[+(++a) + +(-127)];\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    int a= 10\n" //
				+ "    final Short cache[] = new Short[+(++a) + +-127];\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug405096_6() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    final Short cache[] = new Short[+(+128) + ~(-127)];\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    final Short cache[] = new Short[+(+128) + ~-127];\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveParenthesesBug405096_7() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    String a= \"\";\n" //
				+ "    int n= 0;\n" //
				+ "    \n" //
				+ "    int i1 = 1+(1+(+128));\n" //
				+ "    int j1 = 1+(1+(+n));\n" //
				+ "    int i2 = 1-(-128);\n" //
				+ "    int j2 = 1-(-n);\n" //
				+ "    int i3 = 1+(++n);\n" //
				+ "    int j3 = 1-(--n);\n" //
				+ "    String s1 = a+(++n);\n" //
				+ "    String s2 = a+(+128);\n" //
				+ "    int i5 = 1+(--n);\n" //
				+ "    int j5 = 1-(++n);\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_NEVER);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    String a= \"\";\n" //
				+ "    int n= 0;\n" //
				+ "    \n" //
				+ "    int i1 = 1+1+(+128);\n" //
				+ "    int j1 = 1+1+(+n);\n" //
				+ "    int i2 = 1-(-128);\n" //
				+ "    int j2 = 1-(-n);\n" //
				+ "    int i3 = 1+(++n);\n" //
				+ "    int j3 = 1-(--n);\n" //
				+ "    String s1 = a+(++n);\n" //
				+ "    String s2 = a+(+128);\n" //
				+ "    int i5 = 1+--n;\n" //
				+ "    int j5 = 1-++n;\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveQualifier01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo;\n" //
				+ "    public void setFoo(int foo) {\n" //
				+ "        this.foo= foo;\n" //
				+ "    }\n" //
				+ "    public int getFoo() {\n" //
				+ "        return this.foo;\n" //
				+ "    }   \n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo;\n" //
				+ "    public void setFoo(int foo) {\n" //
				+ "        this.foo= foo;\n" //
				+ "    }\n" //
				+ "    public int getFoo() {\n" //
				+ "        return foo;\n" //
				+ "    }   \n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testNumberSuffix() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private long usual = 101l;\n" //
				+ "    private long octal = 0121l;\n" //
				+ "    private long hex = 0xdafdafdafl;\n" //
				+ "\n" //
				+ "    private float usualFloat = 101f;\n" //
				+ "    private float octalFloat = 0121f;\n" //
				+ "\n" //
				+ "    private double usualDouble = 101d;\n" //
				+ "\n" //
				+ "    public long refactorIt() {\n" //
				+ "        long localVar = 11l;\n" //
				+ "        return localVar + 333l;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public double doNotRefactor() {\n" //
				+ "        long l = 11L;\n" //
				+ "        float f = 11F;\n" //
				+ "        double d = 11D;\n" //
				+ "        float localFloat = 11f;\n" //
				+ "        double localDouble = 11d;\n" //
				+ "        return l + 101L + f + 11F + d + 11D + localFloat + 11f + localDouble + 11d;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.NUMBER_SUFFIX);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private long usual = 101L;\n" //
				+ "    private long octal = 0121L;\n" //
				+ "    private long hex = 0xdafdafdafL;\n" //
				+ "\n" //
				+ "    private float usualFloat = 101f;\n" //
				+ "    private float octalFloat = 0121f;\n" //
				+ "\n" //
				+ "    private double usualDouble = 101d;\n" //
				+ "\n" //
				+ "    public long refactorIt() {\n" //
				+ "        long localVar = 11L;\n" //
				+ "        return localVar + 333L;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public double doNotRefactor() {\n" //
				+ "        long l = 11L;\n" //
				+ "        float f = 11F;\n" //
				+ "        double d = 11D;\n" //
				+ "        float localFloat = 11f;\n" //
				+ "        double localDouble = 11d;\n" //
				+ "        return l + 101L + f + 11F + d + 11D + localFloat + 11f + localDouble + 11d;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRegExPrecompilation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "    private static boolean valid;\n" //
				+ "\n" //
				+ "    static {\n" //
				+ "        // Keep this comment\n" //
				+ "        String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "        String dateValidation2= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "        String date1= \"1962-12-18\";\n" //
				+ "        String date2= \"2000-03-15\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        valid= date1.matches(dateValidation) && date2.matches(dateValidation)\n" //
				+ "                && date1.matches(dateValidation2) && date2.matches(dateValidation2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean usePattern(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "        String dateValidation2= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return date1.matches(dateValidation) && date2.matches(dateValidation)\n" //
				+ "                && date1.matches(dateValidation2) && date2.matches(dateValidation2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean usePatternAmongStatements(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "        System.out.println(\"Do other things\");\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return date1.matches(dateValidation) && date2.matches(dateValidation);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String usePatternForReplace(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String dateText1= date1.replaceFirst(dateValidation, \"0000-00-00\");\n" //
				+ "        // Keep this comment also\n" //
				+ "        String dateText2= date2.replaceAll(dateValidation, \"0000-00-00\");\n" //
				+ "\n" //
				+ "        return dateText1 + dateText2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String usePatternForSplit1(String speech1, String speech2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String line= \"\\\\r?\\\\n\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String[] phrases1= speech1.split(line);\n" //
				+ "        // Keep this comment also\n" //
				+ "        String[] phrases2= speech2.split(line, 123);\n" //
				+ "\n" //
				+ "        return Arrays.toString(phrases1) + Arrays.toString(phrases2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String usePatternForSplit2(String speech1, String speech2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String line= \".\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String[] phrases1= speech1.split(line);\n" //
				+ "        // Keep this comment also\n" //
				+ "        String[] phrases2= speech2.split(line, 123);\n" //
				+ "\n" //
				+ "        return Arrays.toString(phrases1) + Arrays.toString(phrases2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String usePatternForSplit3(String speech1, String speech2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String line= \"\\\\a\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String[] phrases1= speech1.split(line);\n" //
				+ "        // Keep this comment also\n" //
				+ "        String[] phrases2= speech2.split(line, 123);\n" //
				+ "\n" //
				+ "        return Arrays.toString(phrases1) + Arrays.toString(phrases2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String usePatternForLocalVariableOnly(String date1, String date2, String date3) {\n" //
				+ "        String dateText1= date1.replaceFirst(dateValidation, \"0000-00-00\");\n" //
				+ "        // Keep this comment\n" //
				+ "        String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String dateText2= date2.replaceFirst(dateValidation, \"0000-00-00\");\n" //
				+ "        // Keep this comment also\n" //
				+ "        String dateText3= date3.replaceAll(dateValidation, \"0000-00-00\");\n" //
				+ "\n" //
				+ "        return dateText1 + dateText2 + dateText3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "   public boolean usePatternFromVariable(String regex, String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String dateValidation= regex;\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return date1.matches(dateValidation) && \"\".equals(date2.replaceFirst(dateValidation, \"\"));\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PRECOMPILE_REGEX);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "    private static boolean valid;\n" //
				+ "\n" //
				+ "    static {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern dateValidation= Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "        Pattern dateValidation2= Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "        String date1= \"1962-12-18\";\n" //
				+ "        String date2= \"2000-03-15\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        valid= dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches()\n" //
				+ "                && dateValidation2.matcher(date1).matches() && dateValidation2.matcher(date2).matches();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern dateValidation_pattern = Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "    private static final Pattern dateValidation2_pattern = Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "    public boolean usePattern(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern dateValidation= dateValidation_pattern;\n" //
				+ "        Pattern dateValidation2= dateValidation2_pattern;\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches()\n" //
				+ "                && dateValidation2.matcher(date1).matches() && dateValidation2.matcher(date2).matches();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern dateValidation_pattern2 = Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "    public boolean usePatternAmongStatements(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern dateValidation= dateValidation_pattern2;\n" //
				+ "        System.out.println(\"Do other things\");\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern dateValidation_pattern3 = Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "    public String usePatternForReplace(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern dateValidation= dateValidation_pattern3;\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String dateText1= dateValidation.matcher(date1).replaceFirst(\"0000-00-00\");\n" //
				+ "        // Keep this comment also\n" //
				+ "        String dateText2= dateValidation.matcher(date2).replaceAll(\"0000-00-00\");\n" //
				+ "\n" //
				+ "        return dateText1 + dateText2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern line_pattern = Pattern.compile(\"\\\\r?\\\\n\");\n" //
				+ "\n" //
				+ "    public String usePatternForSplit1(String speech1, String speech2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern line= line_pattern;\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String[] phrases1= line.split(speech1);\n" //
				+ "        // Keep this comment also\n" //
				+ "        String[] phrases2= line.split(speech2, 123);\n" //
				+ "\n" //
				+ "        return Arrays.toString(phrases1) + Arrays.toString(phrases2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern line_pattern2 = Pattern.compile(\".\");\n" //
				+ "\n" //
				+ "    public String usePatternForSplit2(String speech1, String speech2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern line= line_pattern2;\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String[] phrases1= line.split(speech1);\n" //
				+ "        // Keep this comment also\n" //
				+ "        String[] phrases2= line.split(speech2, 123);\n" //
				+ "\n" //
				+ "        return Arrays.toString(phrases1) + Arrays.toString(phrases2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern line_pattern3 = Pattern.compile(\"\\\\a\");\n" //
				+ "\n" //
				+ "    public String usePatternForSplit3(String speech1, String speech2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern line= line_pattern3;\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String[] phrases1= line.split(speech1);\n" //
				+ "        // Keep this comment also\n" //
				+ "        String[] phrases2= line.split(speech2, 123);\n" //
				+ "\n" //
				+ "        return Arrays.toString(phrases1) + Arrays.toString(phrases2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern dateValidation_pattern4 = Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "    public String usePatternForLocalVariableOnly(String date1, String date2, String date3) {\n" //
				+ "        String dateText1= date1.replaceFirst(dateValidation, \"0000-00-00\");\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern dateValidation= dateValidation_pattern4;\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        String dateText2= dateValidation.matcher(date2).replaceFirst(\"0000-00-00\");\n" //
				+ "        // Keep this comment also\n" //
				+ "        String dateText3= dateValidation.matcher(date3).replaceAll(\"0000-00-00\");\n" //
				+ "\n" //
				+ "        return dateText1 + dateText2 + dateText3;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean usePatternFromVariable(String regex, String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern dateValidation= Pattern.compile(regex);\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return dateValidation.matcher(date1).matches() && \"\".equals(dateValidation.matcher(date2).replaceFirst(\"\"));\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRegExPrecompilationInDefaultMethod() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public interface I1 {\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "\n" //
				+ "    public default boolean usePattern(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "        String dateValidation2= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return date1.matches(dateValidation) && date2.matches(dateValidation)\n" //
				+ "                && date1.matches(dateValidation2) && date2.matches(dateValidation2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("I1.java", sample, false, null);

		enable(CleanUpConstants.PRECOMPILE_REGEX);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public interface I1 {\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "\n" //
				+ "    public default boolean usePattern(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        Pattern dateValidation= Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "        Pattern dateValidation2= Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches()\n" //
				+ "                && dateValidation2.matcher(date1).matches() && dateValidation2.matcher(date2).matches();\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRegExPrecompilationInInnerClass() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "\n" //
				+ "    private class Inner1 {\n" //
				+ "        public default boolean usePattern(String date1, String date2) {\n" //
				+ "            // Keep this comment\n" //
				+ "            String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "            // Keep this comment too\n" //
				+ "            return date1.matches(dateValidation) && date2.matches(dateValidation);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static class Inner2 {\n" //
				+ "        public default boolean usePattern(String date1, String date2) {\n" //
				+ "            // Keep this comment\n" //
				+ "            String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "            // Keep this comment too\n" //
				+ "            return date1.matches(dateValidation) && date2.matches(dateValidation);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void foo() {\n" //
				+ "        public default boolean usePattern(String date1, String date2) {\n" //
				+ "            // Keep this comment\n" //
				+ "            String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "            // Keep this comment too\n" //
				+ "            return date1.matches(dateValidation) && date2.matches(dateValidation);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PRECOMPILE_REGEX);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "\n" //
				+ "    private class Inner1 {\n" //
				+ "        public default boolean usePattern(String date1, String date2) {\n" //
				+ "            // Keep this comment\n" //
				+ "            Pattern dateValidation= Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "            // Keep this comment too\n" //
				+ "            return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static class Inner2 {\n" //
				+ "        private static final Pattern dateValidation_pattern = Pattern\n" //
				+ "                .compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "        public default boolean usePattern(String date1, String date2) {\n" //
				+ "            // Keep this comment\n" //
				+ "            Pattern dateValidation= dateValidation_pattern;\n" //
				+ "\n" //
				+ "            // Keep this comment too\n" //
				+ "            return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern dateValidation_pattern = Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "    public void foo() {\n" //
				+ "        public default boolean usePattern(String date1, String date2) {\n" //
				+ "            // Keep this comment\n" //
				+ "            Pattern dateValidation= dateValidation_pattern;\n" //
				+ "\n" //
				+ "            // Keep this comment too\n" //
				+ "            return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRegExPrecompilationInLocalClass() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "\n" //
				+ "    public void foo() {\n" //
				+ "        class Inner {\n" //
				+ "            public default boolean usePattern(String date1, String date2) {\n" //
				+ "                // Keep this comment\n" //
				+ "                String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "                String dateValidation2= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "                // Keep this comment too\n" //
				+ "                return date1.matches(dateValidation) && date2.matches(dateValidation)\n" //
				+ "                    && date1.matches(dateValidation2) && date2.matches(dateValidation2);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PRECOMPILE_REGEX);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "\n" //
				+ "    public void foo() {\n" //
				+ "        class Inner {\n" //
				+ "            public default boolean usePattern(String date1, String date2) {\n" //
				+ "                // Keep this comment\n" //
				+ "                Pattern dateValidation= Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "                Pattern dateValidation2= Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "                // Keep this comment too\n" //
				+ "                return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches()\n" //
				+ "                        && dateValidation2.matcher(date1).matches() && dateValidation2.matcher(date2).matches();\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRegExPrecompilationInAnonymousClass() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "    abstract class I1 {\n" //
				+ "        public abstract boolean validate(String date1, String date2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void foo() {\n" //
				+ "        I1 i1= new I1() {\n" //
				+ "            @Override\n" //
				+ "            public boolean validate(String date1, String date2) {\n" //
				+ "                // Keep this comment\n" //
				+ "                String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "                String dateValidation2= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "                // Keep this comment too\n" //
				+ "                return date1.matches(dateValidation) && date2.matches(dateValidation)\n" //
				+ "                        && date1.matches(dateValidation2) && date2.matches(dateValidation2);\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PRECOMPILE_REGEX);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "    abstract class I1 {\n" //
				+ "        public abstract boolean validate(String date1, String date2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern dateValidation_pattern = Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "    private static final Pattern dateValidation2_pattern = Pattern.compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "    public void foo() {\n" //
				+ "        I1 i1= new I1() {\n" //
				+ "            @Override\n" //
				+ "            public boolean validate(String date1, String date2) {\n" //
				+ "                // Keep this comment\n" //
				+ "                Pattern dateValidation= dateValidation_pattern;\n" //
				+ "                Pattern dateValidation2= dateValidation2_pattern;\n" //
				+ "\n" //
				+ "                // Keep this comment too\n" //
				+ "                return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches()\n" //
				+ "                        && dateValidation2.matcher(date1).matches() && dateValidation2.matcher(date2).matches();\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testSingleUsedFieldInInnerClass() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public class SubClass {\n" //
				+ "        private int refactorField;\n" //
				+ "\n" //
				+ "        public void refactorFieldInSubClass() {\n" //
				+ "            this.refactorField = 123;\n" //
				+ "            System.out.println(refactorField);\n" //
				+ "        }\n" //
				+ "    }\n"
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public class SubClass {\n" //
				+ "        public void refactorFieldInSubClass() {\n" //
				+ "            int refactorField = 123;\n" //
				+ "            System.out.println(refactorField);\n" //
				+ "        }\n" //
				+ "    }\n"
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.SINGLE_USED_FIELD);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.SingleUsedFieldCleanUp_description_new_local_var_declaration,
				MultiFixMessages.SingleUsedFieldCleanUp_description_old_field_declaration, MultiFixMessages.SingleUsedFieldCleanUp_description_uses_of_the_var)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testSingleUsedFieldWithComplexUse() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private short refactorFieldWithComplexUse= 42;\n" //
				+ "\n" //
				+ "    public void refactorFieldWithComplexUse(boolean b, List<String> texts) {\n" //
				+ "        // Keep this comment\n" //
				+ "        refactorFieldWithComplexUse = 123;\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(refactorFieldWithComplexUse);\n" //
				+ "        } else {\n" //
				+ "            refactorFieldWithComplexUse = 321;\n" //
				+ "\n" //
				+ "            for (String text : texts) {\n" //
				+ "                System.out.println(text);\n" //
				+ "                System.out.println(this.refactorFieldWithComplexUse);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void refactorFieldWithComplexUse(boolean b, List<String> texts) {\n" //
				+ "        // Keep this comment\n" //
				+ "        short refactorFieldWithComplexUse = 123;\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(refactorFieldWithComplexUse);\n" //
				+ "        } else {\n" //
				+ "            refactorFieldWithComplexUse = 321;\n" //
				+ "\n" //
				+ "            for (String text : texts) {\n" //
				+ "                System.out.println(text);\n" //
				+ "                System.out.println(refactorFieldWithComplexUse);\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.SINGLE_USED_FIELD);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.SingleUsedFieldCleanUp_description_new_local_var_declaration,
				MultiFixMessages.SingleUsedFieldCleanUp_description_old_field_declaration, MultiFixMessages.SingleUsedFieldCleanUp_description_uses_of_the_var)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testSingleUsedFieldArray() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private int refactorArray[];\n" //
				+ "\n" //
				+ "    public void refactorArray() {\n" //
				+ "        // Keep this comment\n" //
				+ "        this.refactorArray = new int[]{123};\n" //
				+ "        System.out.println(refactorArray);\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void refactorArray() {\n" //
				+ "        // Keep this comment\n" //
				+ "        int refactorArray[] = new int[]{123};\n" //
				+ "        System.out.println(refactorArray);\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.SINGLE_USED_FIELD);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.SingleUsedFieldCleanUp_description_new_local_var_declaration,
				MultiFixMessages.SingleUsedFieldCleanUp_description_old_field_declaration, MultiFixMessages.SingleUsedFieldCleanUp_description_uses_of_the_var)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testSingleUsedFieldInMultiFragment() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private int refactorOneFragment, severalUses;\n" //
				+ "\n" //
				+ "    public void refactorOneFragment() {\n" //
				+ "        // Keep this comment\n" //
				+ "        refactorOneFragment = 123;\n" //
				+ "        System.out.println(refactorOneFragment);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void severalUses() {\n" //
				+ "        severalUses = 123;\n" //
				+ "        System.out.println(severalUses);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void severalUses(int i) {\n" //
				+ "        severalUses = i;\n" //
				+ "        System.out.println(severalUses);\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private int severalUses;\n" //
				+ "\n" //
				+ "    public void refactorOneFragment() {\n" //
				+ "        // Keep this comment\n" //
				+ "        int refactorOneFragment = 123;\n" //
				+ "        System.out.println(refactorOneFragment);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void severalUses() {\n" //
				+ "        severalUses = 123;\n" //
				+ "        System.out.println(severalUses);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void severalUses(int i) {\n" //
				+ "        severalUses = i;\n" //
				+ "        System.out.println(severalUses);\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.SINGLE_USED_FIELD);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.SingleUsedFieldCleanUp_description_new_local_var_declaration,
				MultiFixMessages.SingleUsedFieldCleanUp_description_old_field_declaration, MultiFixMessages.SingleUsedFieldCleanUp_description_uses_of_the_var)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testSingleUsedFieldStatic() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static long refactorStaticField;\n" //
				+ "\n" //
				+ "    public void refactorStaticField() {\n" //
				+ "        // Keep this comment\n" //
				+ "        refactorStaticField = 123;\n" //
				+ "        System.out.println(refactorStaticField);\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void refactorStaticField() {\n" //
				+ "        // Keep this comment\n" //
				+ "        long refactorStaticField = 123;\n" //
				+ "        System.out.println(refactorStaticField);\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.SINGLE_USED_FIELD);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.SingleUsedFieldCleanUp_description_new_local_var_declaration,
				MultiFixMessages.SingleUsedFieldCleanUp_description_old_field_declaration, MultiFixMessages.SingleUsedFieldCleanUp_description_uses_of_the_var)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testSingleUsedFieldWithSameNameAsLocalVariable() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private int refactorFieldWithSameNameAsLocalVariable;\n" //
				+ "\n" //
				+ "    public void refactorFieldWithSameNameAsLocalVariable() {\n" //
				+ "        refactorFieldWithSameNameAsLocalVariable = 123;\n" //
				+ "        System.out.println(test1.E.this.refactorFieldWithSameNameAsLocalVariable);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void methodWithLocalVariable() {\n" //
				+ "        long refactorFieldWithSameNameAsLocalVariable = 123;\n" //
				+ "        System.out.println(refactorFieldWithSameNameAsLocalVariable);\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void refactorFieldWithSameNameAsLocalVariable() {\n" //
				+ "        int refactorFieldWithSameNameAsLocalVariable = 123;\n" //
				+ "        System.out.println(refactorFieldWithSameNameAsLocalVariable);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void methodWithLocalVariable() {\n" //
				+ "        long refactorFieldWithSameNameAsLocalVariable = 123;\n" //
				+ "        System.out.println(refactorFieldWithSameNameAsLocalVariable);\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.SINGLE_USED_FIELD);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.SingleUsedFieldCleanUp_description_new_local_var_declaration,
				MultiFixMessages.SingleUsedFieldCleanUp_description_old_field_declaration, MultiFixMessages.SingleUsedFieldCleanUp_description_uses_of_the_var)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testSingleUsedFieldWithSameNameAsAttribute() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private int out;\n" //
				+ "\n" //
				+ "    public void refactorFieldWithSameNameAsAttribute() {\n" //
				+ "        out = 123;\n" //
				+ "        System.out.println(out);\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void refactorFieldWithSameNameAsAttribute() {\n" //
				+ "        int out = 123;\n" //
				+ "        System.out.println(out);\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.SINGLE_USED_FIELD);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.SingleUsedFieldCleanUp_description_new_local_var_declaration,
				MultiFixMessages.SingleUsedFieldCleanUp_description_old_field_declaration, MultiFixMessages.SingleUsedFieldCleanUp_description_uses_of_the_var)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testKeepSingleUsedField() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int doNotRefactorPublicField;\n" //
				+ "    protected int doNotRefactorProtectedField;\n" //
				+ "    int doNotRefactorPackageField;\n" //
				+ "    private int doNotRefactorFieldsInSeveralMethods;\n" //
				+ "    private int doNotRefactorFieldInOtherField;\n" //
				+ "    private int oneField = doNotRefactorFieldInOtherField;\n" //
				+ "    private int doNotRefactorReadFieldBeforeAssignment;\n" //
				+ "    private int doNotRefactorUnusedField;\n" //
				+ "    private List<String> dynamicList= new ArrayList<>(Arrays.asList(\"foo\", \"bar\"));\n" //
				+ "    private boolean doNotRefactorFieldWithActiveInitializer = dynamicList.remove(\"foo\");\n" //
				+ "    private Runnable doNotRefactorObject;\n" //
				+ "    @Deprecated\n" //
				+ "    private int doNotRefactorFieldWithAnnotation;\n" //
				+ "\n" //
				+ "    public void doNotRefactorPublicField() {\n" //
				+ "        doNotRefactorPublicField = 123;\n" //
				+ "        System.out.println(doNotRefactorPublicField);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorProtectedField() {\n" //
				+ "        doNotRefactorProtectedField = 123;\n" //
				+ "        System.out.println(doNotRefactorProtectedField);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorPackageField() {\n" //
				+ "        doNotRefactorPackageField = 123;\n" //
				+ "        System.out.println(doNotRefactorPackageField);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorFieldsInSeveralMethods() {\n" //
				+ "        doNotRefactorFieldsInSeveralMethods = 123;\n" //
				+ "        System.out.println(doNotRefactorFieldsInSeveralMethods);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorFieldsInSeveralMethods(int i) {\n" //
				+ "        doNotRefactorFieldsInSeveralMethods = i;\n" //
				+ "        System.out.println(doNotRefactorFieldsInSeveralMethods);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorReadFieldBeforeAssignment() {\n" //
				+ "        System.out.println(doNotRefactorReadFieldBeforeAssignment);\n" //
				+ "        doNotRefactorReadFieldBeforeAssignment = 123;\n" //
				+ "        System.out.println(doNotRefactorReadFieldBeforeAssignment);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorFieldInOtherField() {\n" //
				+ "        doNotRefactorFieldInOtherField = 123;\n" //
				+ "        System.out.println(doNotRefactorFieldInOtherField);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorFieldWithActiveInitializer() {\n" //
				+ "        doNotRefactorFieldWithActiveInitializer = true;\n" //
				+ "        System.out.println(doNotRefactorFieldWithActiveInitializer);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorObject() {\n" //
				+ "        doNotRefactorObject = new Runnable() {\n" //
				+ "            @Override\n" //
				+ "            public void run() {\n" //
				+ "                while (true) {\n" //
				+ "                    System.out.println(\"Don't stop me!\");\n" //
				+ "                }\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "        doNotRefactorObject.run();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorFieldWithAnnotation() {\n" //
				+ "        doNotRefactorFieldWithAnnotation = 123456;\n" //
				+ "        System.out.println(doNotRefactorFieldWithAnnotation);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class SubClass {\n" //
				+ "        private int subClassField = 42;\n" //
				+ "\n" //
				+ "        public void doNotRefactorFieldInSubClass() {\n" //
				+ "            this.subClassField = 123;\n" //
				+ "            System.out.println(subClassField);\n" //
				+ "        }\n" //
				+ "    }\n"
				+ "\n" //
				+ "    public void oneMethod() {\n" //
				+ "        SubClass aSubClass = new SubClass();\n" //
				+ "        System.out.println(aSubClass.subClassField);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.SINGLE_USED_FIELD);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testBreakLoop() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private int[] innerArray = new int[10];\n" //
				+ "\n" //
				+ "    public String addBreak(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakInForeachLoop(int[] array) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakWithField() {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < this.innerArray.length; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakWithoutBlock(int[] array) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (i == 42)\n" //
				+ "                isFound = true;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakAfterSeveralAssignments(String[] array, boolean isFound, int count) {\n" //
				+ "        for (String text : array) {\n" //
				+ "            if (text == null) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "                count = 1;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        if (isFound) {\n" //
				+ "            return \"We have found \" + count + \" result(s)\";\n" //
				+ "        } else {\n" //
				+ "            return \"The result has not been found\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakAfterComplexAssignment(int[] array) {\n" //
				+ "        int hourNumber = 0;\n" //
				+ "\n" //
				+ "        for (int dayNumber : array) {\n" //
				+ "            if (dayNumber == 7) {\n" //
				+ "                // Keep this comment\n" //
				+ "                hourNumber = 7 * 24;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"Hour number: \" + hourNumber;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakWithTemporaryVariable(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            int temporaryInteger = i * 3;\n" //
				+ "\n" //
				+ "            if (temporaryInteger == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean[] addBreakWithFixedAssignment(int number, int index) {\n" //
				+ "        boolean[] isFound = new boolean[number];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound[index] = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakWithUpdatedIterator(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i++ == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.BREAK_LOOP);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private int[] innerArray = new int[10];\n" //
				+ "\n" //
				+ "    public String addBreak(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakInForeachLoop(int[] array) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakWithField() {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < this.innerArray.length; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakWithoutBlock(int[] array) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            // Keep this comment\n" //
				+ "            if (i == 42) {\n" //
				+ "                isFound = true;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakAfterSeveralAssignments(String[] array, boolean isFound, int count) {\n" //
				+ "        for (String text : array) {\n" //
				+ "            if (text == null) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "                count = 1;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        if (isFound) {\n" //
				+ "            return \"We have found \" + count + \" result(s)\";\n" //
				+ "        } else {\n" //
				+ "            return \"The result has not been found\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakAfterComplexAssignment(int[] array) {\n" //
				+ "        int hourNumber = 0;\n" //
				+ "\n" //
				+ "        for (int dayNumber : array) {\n" //
				+ "            if (dayNumber == 7) {\n" //
				+ "                // Keep this comment\n" //
				+ "                hourNumber = 7 * 24;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"Hour number: \" + hourNumber;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakWithTemporaryVariable(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            int temporaryInteger = i * 3;\n" //
				+ "\n" //
				+ "            if (temporaryInteger == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean[] addBreakWithFixedAssignment(int number, int index) {\n" //
				+ "        boolean[] isFound = new boolean[number];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound[index] = true;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String addBreakWithUpdatedIterator(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i++ == 42) {\n" //
				+ "                // Keep this comment\n" //
				+ "                isFound = true;\n" //
				+ "                break;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : \"The result has not been found\";\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", input, output);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.BreakLoopCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotBreakLoop() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private int crazyInteger = 0;\n" //
				+ "\n" //
				+ "    public String doNotBreakWithoutAssignment(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "        return isFound ? \"The result has been found\" : (\"The result has not been found\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotBreakWithExternalIterator(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "        int i;\n" //
				+ "        for (i = 0; i < number; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "        return isFound ? \"The result has been found\" : (\"The result has not been found on \" + i + \" iteration(s)\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotBreakWithActiveConditions(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "        for (int i = 0; i < number--; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : (\"The result has not been found on \" + number + \" iteration(s)\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean[] doNotBreakWithChangingAssignment(int number) {\n" //
				+ "        boolean[] hasNumber42 = new boolean[number];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                hasNumber42[i] = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return hasNumber42;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int[] doNotBreakForeachLoopWithChangingAssignment(int[] input, int[] output) {\n" //
				+ "        for (int i : input) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                output[i] = 123456;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean[] doNotBreakWithActiveAssignment(int number, int index) {\n" //
				+ "        boolean[] isFound = new boolean[number];\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                isFound[index++] = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotBreakWithActiveUpdater(int number) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i = 0; i < number; i++, number--) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? \"The result has been found\" : (\"The result has not been found on \" + number + \" iteration(s)\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotBreakWithSeveralConditions(int[] array) {\n" //
				+ "        int tenFactor = 0;\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            if (i == 10) {\n" //
				+ "                tenFactor = 1;\n" //
				+ "            }\n" //
				+ "            if (i == 100) {\n" //
				+ "                tenFactor = 2;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"The result: \" + tenFactor;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotBreakWithActiveCondition(int[] array, int modifiedInteger) {\n" //
				+ "        boolean isFound = false;\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            if (i == modifiedInteger++) {\n" //
				+ "                isFound = true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return isFound ? 0 : modifiedInteger;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotBreakWithActiveAssignment(int[] array, int modifiedInteger) {\n" //
				+ "        int result = 0;\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                result = modifiedInteger++;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return result;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotBreakWithVariableAssignment(int[] array) {\n" //
				+ "        int result = 0;\n" //
				+ "\n" //
				+ "        new Thread() {\n" //
				+ "            @Override\n" //
				+ "            public void run() {\n" //
				+ "                while (crazyInteger++ < 10000) {}\n" //
				+ "            }\n" //
				+ "        }.start();\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            if (i == 42) {\n" //
				+ "                result = crazyInteger;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return result;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorWithSpecialAssignment(int[] array) {\n" //
				+ "        int tenFactor = 0;\n" //
				+ "\n" //
				+ "        for (int i : array) {\n" //
				+ "            if (i == 10) {\n" //
				+ "                tenFactor += 1;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"The result: \" + tenFactor;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotBreakInfiniteLoop(int[] array) {\n" //
				+ "        int tenFactor = 0;\n" //
				+ "\n" //
				+ "        for (;;) {\n" //
				+ "            if (crazyInteger == 10) {\n" //
				+ "                tenFactor = 1;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.BREAK_LOOP);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testStaticInnerClass() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import static java.lang.Integer.bitCount;\n" //
				+ "\n" //
				+ "import java.io.File;\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public class RefactorThisInnerClass {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorThisInnerClassThatAccessesField {\n" //
				+ "        File picture;\n" //
				+ "\n" //
				+ "        public char anotherMethod() {\n" //
				+ "            return picture.separatorChar;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorThisInnerClassThatUsesStaticField {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return CONSTANT != null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorThisInnerClassThatUsesQualifiedStaticField {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return E.CONSTANT != null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorThisInnerClassThatUsesFullyQualifiedStaticField {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return test1.E.CONSTANT != null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorInnerClassThatOnlyUsesItsFields {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return i == 0;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorInnerClassThatUsesStaticMethod {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return aStaticMethod();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public final class RefactorThisFinalInnerClass {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    class RefactorThisInnerClassWithoutModifier {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    @Deprecated\n" //
				+ "    class RefactorThisInnerClassWithAnnotation {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorInnerClassThatUsesStaticImport {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public int anotherMethod() {\n" //
				+ "            return bitCount(0);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorInnerClassThatUsesStaticField {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public char anotherMethod() {\n" //
				+ "            return File.separatorChar;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorInheritedInnerClass extends File {\n" //
				+ "        private static final long serialVersionUID = -1124849036813595100L;\n" //
				+ "        private int i;\n" //
				+ "\n" //
				+ "        public RefactorInheritedInnerClass(File arg0, String arg1) {\n" //
				+ "            super(arg0, arg1);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorGenericInnerClass<T> {\n" //
				+ "        T i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final String CONSTANT= \"foo\";\n" //
				+ "\n" //
				+ "    private String aString= \"bar\";\n" //
				+ "\n" //
				+ "    public static boolean aStaticMethod() {\n" //
				+ "        return false;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean aMethod() {\n" //
				+ "        return true;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorInnerClassWithThisReference {\n" //
				+ "        public RefactorInnerClassWithThisReference aMethod() {\n" //
				+ "            return this;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class RefactorInnerClassWithQualifiedThisReference {\n" //
				+ "        public RefactorInnerClassWithQualifiedThisReference anotherMethod() {\n" //
				+ "            return RefactorInnerClassWithQualifiedThisReference.this;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import static java.lang.Integer.bitCount;\n" //
				+ "\n" //
				+ "import java.io.File;\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static class RefactorThisInnerClass {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorThisInnerClassThatAccessesField {\n" //
				+ "        File picture;\n" //
				+ "\n" //
				+ "        public char anotherMethod() {\n" //
				+ "            return picture.separatorChar;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorThisInnerClassThatUsesStaticField {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return CONSTANT != null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorThisInnerClassThatUsesQualifiedStaticField {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return E.CONSTANT != null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorThisInnerClassThatUsesFullyQualifiedStaticField {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return test1.E.CONSTANT != null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorInnerClassThatOnlyUsesItsFields {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return i == 0;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorInnerClassThatUsesStaticMethod {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return aStaticMethod();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static final class RefactorThisFinalInnerClass {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    static class RefactorThisInnerClassWithoutModifier {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    @Deprecated\n" //
				+ "    static\n" //
				+ "    class RefactorThisInnerClassWithAnnotation {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorInnerClassThatUsesStaticImport {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public int anotherMethod() {\n" //
				+ "            return bitCount(0);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorInnerClassThatUsesStaticField {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public char anotherMethod() {\n" //
				+ "            return File.separatorChar;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorInheritedInnerClass extends File {\n" //
				+ "        private static final long serialVersionUID = -1124849036813595100L;\n" //
				+ "        private int i;\n" //
				+ "\n" //
				+ "        public RefactorInheritedInnerClass(File arg0, String arg1) {\n" //
				+ "            super(arg0, arg1);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorGenericInnerClass<T> {\n" //
				+ "        T i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final String CONSTANT= \"foo\";\n" //
				+ "\n" //
				+ "    private String aString= \"bar\";\n" //
				+ "\n" //
				+ "    public static boolean aStaticMethod() {\n" //
				+ "        return false;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean aMethod() {\n" //
				+ "        return true;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorInnerClassWithThisReference {\n" //
				+ "        public RefactorInnerClassWithThisReference aMethod() {\n" //
				+ "            return this;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class RefactorInnerClassWithQualifiedThisReference {\n" //
				+ "        public RefactorInnerClassWithQualifiedThisReference anotherMethod() {\n" //
				+ "            return RefactorInnerClassWithQualifiedThisReference.this;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.STATIC_INNER_CLASS);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.StaticInnerClassCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUseStaticInnerClass() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.sql.DriverPropertyInfo;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public interface DoNotRefactorInnerInterface {\n" //
				+ "        boolean anotherMethod();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class DoNotRefactorThisInnerClass {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return aString != null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class DoNotRefactorClassUsingInheritedMemberAsItSNotHandledYet extends DriverPropertyInfo {\n" //
				+ "        private static final long serialVersionUID = 1L;\n" //
				+ "\n" //
				+ "        public DoNotRefactorClassUsingInheritedMemberAsItSNotHandledYet() {\n" //
				+ "            super(\"\", \"\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        public boolean itSNotHandledYet() {\n" //
				+ "            return choices != null;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class DoNotRefactorInnerClassThatUsesMethod {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return aMethod();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean aMethodWithAMethodLocalInnerClass() {\n" //
				+ "        class DoNotRefactorMethodLocalInnerClass {\n" //
				+ "            int k;\n" //
				+ "\n" //
				+ "            boolean anotherMethod() {\n" //
				+ "                return true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return new DoNotRefactorMethodLocalInnerClass().anotherMethod();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static class DoNotRefactorAlreadyStaticInnerClass {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return true;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class DoNotRefactorInnerClassWithQualifiedThis {\n" //
				+ "        public E anotherMethod() {\n" //
				+ "            return E.this;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class DoNotRefactorInnerClassWithFullyQualifiedThis {\n" //
				+ "        public E anotherMethod() {\n" //
				+ "            return test1.E.this;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public class NotStaticClass {\n" //
				+ "        public class DoNotRefactorInnerClassInNotStaticClass {\n" //
				+ "            int i;\n" //
				+ "\n" //
				+ "            public boolean anotherMethod() {\n" //
				+ "                return true;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        public boolean anotherMethod() {\n" //
				+ "            return aMethod();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final String CONSTANT= \"foo\";\n" //
				+ "\n" //
				+ "    private String aString= \"bar\";\n" //
				+ "\n" //
				+ "    public static boolean aStaticMethod() {\n" //
				+ "        return false;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean aMethod() {\n" //
				+ "        return true;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.STATIC_INNER_CLASS);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testStringBuilder() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static String useStringBuilder() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String text = \"\";\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text += \"foo\";\n" //
				+ "        text += \"bar\";\n" //
				+ "        text += \"foobar\";\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderInFinalConcatenation() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String text = \"\";\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text += \"foo\";\n" //
				+ "        text += \"bar\";\n" //
				+ "        text += \"foobar\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text + \"append me!\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderInPreviousConcatenation() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String text = \"\";\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text += \"foo\";\n" //
				+ "        text += \"bar\";\n" //
				+ "        text += \"foobar\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return \"previous text\" + text + \"append me!\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithInitializer() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String concatenation = \"foo\";\n" //
				+ "        // Keep this comment also\n" //
				+ "        concatenation += \"bar\";\n" //
				+ "        concatenation += \"foobar\";\n" //
				+ "        // Keep this comment too\n" //
				+ "        return concatenation;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithConcatenationInitializer() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String concatenation = \"foo\" + \"bar\";\n" //
				+ "        // Keep this comment also\n" //
				+ "        concatenation += \"bar\";\n" //
				+ "        concatenation += \"foobar\";\n" //
				+ "        // Keep this comment too\n" //
				+ "        return concatenation;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithNonStringInitializer() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String concatenation = 123 + \"bar\";\n" //
				+ "        // Keep this comment also\n" //
				+ "        concatenation += \"bar\";\n" //
				+ "        concatenation += \"foobar\";\n" //
				+ "        // Keep this comment too\n" //
				+ "        return concatenation;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderAndRemoveValueOfMethod() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String text = \"\";\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text += \"foo\";\n" //
				+ "        text += \"bar\";\n" //
				+ "        text += String.valueOf(123);\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderAndRemoveValueOfMethodInFinalConcatenation() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String text = \"\";\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text += \"foo\";\n" //
				+ "        text += \"bar\";\n" //
				+ "        text += \"foobar\";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text + String.valueOf(123) + new String(456);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderOnBasicAssignment(int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String serialization = \"\";\n" //
				+ "        // Keep this comment also\n" //
				+ "        serialization = serialization + \"foo\";\n" //
				+ "        serialization = serialization + number;\n" //
				+ "        serialization = serialization + \"bar\";\n" //
				+ "        // Keep this comment too\n" //
				+ "        return serialization;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithExtendedOperation(String text) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variable = \"\";\n" //
				+ "        // Keep this comment also\n" //
				+ "        variable += text + \"foo\";\n" //
				+ "        variable = variable + text + \"bar\";\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithDifferentAssignment() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variousConcatenations = \"\";\n" //
				+ "        // Keep this comment also\n" //
				+ "        variousConcatenations += \"foo\";\n" //
				+ "        variousConcatenations = variousConcatenations + \"bar\" + \"foobar\";\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variousConcatenations;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithBlock(boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variable = \"\";\n" //
				+ "\n" //
				+ "        if (isEnabled) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable += \"foo\";\n" //
				+ "            variable = variable + \"bar\";\n" //
				+ "            variable = variable + \"foobar\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithLoop(List<String> texts) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variable = \"\";\n" //
				+ "\n" //
				+ "        for (String text : texts) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable = variable + \"[\";\n" //
				+ "            variable += text;\n" //
				+ "            variable = variable + \"]\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderOnOneLoopedAssignment(List<String> texts) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variable = \"\";\n" //
				+ "\n" //
				+ "        for (String text : texts) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable += text;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderOnOneLoopedReassignment(List<String> words) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variable = \"\";\n" //
				+ "\n" //
				+ "        for (String word : words) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable = variable + word;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithWhile(String text, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variable = \"\";\n" //
				+ "\n" //
				+ "        while (i-- > 0) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable = variable + \"{\";\n" //
				+ "            variable += text;\n" //
				+ "            variable = variable + \"}\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithTry(String number, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String iterableConcatenation = \"\";\n" //
				+ "\n" //
				+ "        try {\n" //
				+ "            while (i-- > 0) {\n" //
				+ "                // Keep this comment also\n" //
				+ "                iterableConcatenation = iterableConcatenation + \"(\";\n" //
				+ "                iterableConcatenation += (Integer.parseInt(number) + 1);\n" //
				+ "                iterableConcatenation = iterableConcatenation + \")\";\n" //
				+ "            }\n" //
				+ "        } catch (NumberFormatException e) {\n" //
				+ "            return \"0\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return iterableConcatenation;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithFinally(String number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variable = \"\";\n" //
				+ "        int i = 123;\n" //
				+ "\n" //
				+ "        try {\n" //
				+ "            i+= Integer.parseInt(number);\n" //
				+ "        } catch (NumberFormatException e) {\n" //
				+ "            System.out.println(\"error\");\n" //
				+ "        } finally {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable += \"foo\";\n" //
				+ "            variable = variable + \"bar\";\n" //
				+ "            variable = variable + \"foobar\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable + i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithConditionalRead(boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String variable = \"\";\n" //
				+ "\n" //
				+ "        if (isEnabled) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable += \"foo\";\n" //
				+ "            variable = variable + \"bar\";\n" //
				+ "            variable = variable + \"foobar\";\n" //
				+ "            // Keep this comment too\n" //
				+ "            return variable;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderInElse(boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        String conditionalConcatenation = \"\";\n" //
				+ "\n" //
				+ "        if (isEnabled) {\n" //
				+ "            return \"OK\";\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            conditionalConcatenation += \"foo\";\n" //
				+ "            conditionalConcatenation = conditionalConcatenation + \"bar\";\n" //
				+ "            conditionalConcatenation = conditionalConcatenation + \"foobar\";\n" //
				+ "            // Keep this comment too\n" //
				+ "            return \"Another \" + \"text \" + conditionalConcatenation;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithAdditions() {\n" //
				+ "        // Keep this comment\n" //
				+ "        String text = \"1 + 2 = \" + (1 + 2);\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text += \" foo\";\n" //
				+ "        text += \"bar \";\n" //
				+ "        text += \"3 + 4 = \";\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text + (3 + 4);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);

		enable(CleanUpConstants.STRINGBUILDER);

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static String useStringBuilder() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder text = new StringBuilder();\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text.append(\"foo\");\n" //
				+ "        text.append(\"bar\");\n" //
				+ "        text.append(\"foobar\");\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderInFinalConcatenation() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder text = new StringBuilder();\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text.append(\"foo\");\n" //
				+ "        text.append(\"bar\");\n" //
				+ "        text.append(\"foobar\");\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text.append(\"append me!\").toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderInPreviousConcatenation() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder text = new StringBuilder();\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text.append(\"foo\");\n" //
				+ "        text.append(\"bar\");\n" //
				+ "        text.append(\"foobar\");\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return \"previous text\" + text.append(\"append me!\").toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithInitializer() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder concatenation = new StringBuilder(\"foo\");\n" //
				+ "        // Keep this comment also\n" //
				+ "        concatenation.append(\"bar\");\n" //
				+ "        concatenation.append(\"foobar\");\n" //
				+ "        // Keep this comment too\n" //
				+ "        return concatenation.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithConcatenationInitializer() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder concatenation = new StringBuilder(\"foo\").append(\"bar\");\n" //
				+ "        // Keep this comment also\n" //
				+ "        concatenation.append(\"bar\");\n" //
				+ "        concatenation.append(\"foobar\");\n" //
				+ "        // Keep this comment too\n" //
				+ "        return concatenation.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithNonStringInitializer() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder concatenation = new StringBuilder().append(123).append(\"bar\");\n" //
				+ "        // Keep this comment also\n" //
				+ "        concatenation.append(\"bar\");\n" //
				+ "        concatenation.append(\"foobar\");\n" //
				+ "        // Keep this comment too\n" //
				+ "        return concatenation.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderAndRemoveValueOfMethod() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder text = new StringBuilder();\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text.append(\"foo\");\n" //
				+ "        text.append(\"bar\");\n" //
				+ "        text.append(123);\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderAndRemoveValueOfMethodInFinalConcatenation() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder text = new StringBuilder();\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text.append(\"foo\");\n" //
				+ "        text.append(\"bar\");\n" //
				+ "        text.append(\"foobar\");\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text.append(123).append(456).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderOnBasicAssignment(int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder serialization = new StringBuilder();\n" //
				+ "        // Keep this comment also\n" //
				+ "        serialization.append(\"foo\");\n" //
				+ "        serialization.append(number);\n" //
				+ "        serialization.append(\"bar\");\n" //
				+ "        // Keep this comment too\n" //
				+ "        return serialization.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithExtendedOperation(String text) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variable = new StringBuilder();\n" //
				+ "        // Keep this comment also\n" //
				+ "        variable.append(text).append(\"foo\");\n" //
				+ "        variable.append(text).append(\"bar\");\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithDifferentAssignment() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variousConcatenations = new StringBuilder();\n" //
				+ "        // Keep this comment also\n" //
				+ "        variousConcatenations.append(\"foo\");\n" //
				+ "        variousConcatenations.append(\"bar\").append(\"foobar\");\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variousConcatenations.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithBlock(boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variable = new StringBuilder();\n" //
				+ "\n" //
				+ "        if (isEnabled) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable.append(\"foo\");\n" //
				+ "            variable.append(\"bar\");\n" //
				+ "            variable.append(\"foobar\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithLoop(List<String> texts) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variable = new StringBuilder();\n" //
				+ "\n" //
				+ "        for (String text : texts) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable.append(\"[\");\n" //
				+ "            variable.append(text);\n" //
				+ "            variable.append(\"]\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderOnOneLoopedAssignment(List<String> texts) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variable = new StringBuilder();\n" //
				+ "\n" //
				+ "        for (String text : texts) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable.append(text);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderOnOneLoopedReassignment(List<String> words) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variable = new StringBuilder();\n" //
				+ "\n" //
				+ "        for (String word : words) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable.append(word);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithWhile(String text, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variable = new StringBuilder();\n" //
				+ "\n" //
				+ "        while (i-- > 0) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable.append(\"{\");\n" //
				+ "            variable.append(text);\n" //
				+ "            variable.append(\"}\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithTry(String number, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder iterableConcatenation = new StringBuilder();\n" //
				+ "\n" //
				+ "        try {\n" //
				+ "            while (i-- > 0) {\n" //
				+ "                // Keep this comment also\n" //
				+ "                iterableConcatenation.append(\"(\");\n" //
				+ "                iterableConcatenation.append(Integer.parseInt(number)).append(1);\n" //
				+ "                iterableConcatenation.append(\")\");\n" //
				+ "            }\n" //
				+ "        } catch (NumberFormatException e) {\n" //
				+ "            return \"0\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return iterableConcatenation.toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithFinally(String number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variable = new StringBuilder();\n" //
				+ "        int i = 123;\n" //
				+ "\n" //
				+ "        try {\n" //
				+ "            i+= Integer.parseInt(number);\n" //
				+ "        } catch (NumberFormatException e) {\n" //
				+ "            System.out.println(\"error\");\n" //
				+ "        } finally {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable.append(\"foo\");\n" //
				+ "            variable.append(\"bar\");\n" //
				+ "            variable.append(\"foobar\");\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return variable.append(i).toString();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithConditionalRead(boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder variable = new StringBuilder();\n" //
				+ "\n" //
				+ "        if (isEnabled) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            variable.append(\"foo\");\n" //
				+ "            variable.append(\"bar\");\n" //
				+ "            variable.append(\"foobar\");\n" //
				+ "            // Keep this comment too\n" //
				+ "            return variable.toString();\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderInElse(boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder conditionalConcatenation = new StringBuilder();\n" //
				+ "\n" //
				+ "        if (isEnabled) {\n" //
				+ "            return \"OK\";\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment also\n" //
				+ "            conditionalConcatenation.append(\"foo\");\n" //
				+ "            conditionalConcatenation.append(\"bar\");\n" //
				+ "            conditionalConcatenation.append(\"foobar\");\n" //
				+ "            // Keep this comment too\n" //
				+ "            return \"Another \" + \"text \" + conditionalConcatenation.toString();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String useStringBuilderWithAdditions() {\n" //
				+ "        // Keep this comment\n" //
				+ "        StringBuilder text = new StringBuilder(\"1 + 2 = \").append(1 + 2);\n" //
				+ "\n" //
				+ "        // Keep this comment also\n" //
				+ "        text.append(\" foo\");\n" //
				+ "        text.append(\"bar \");\n" //
				+ "        text.append(\"3 + 4 = \");\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return text.append(3 + 4).toString();\n" //
				+ "    }\n" //
				+ "}\n";

		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.StringBuilderCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUseStringBuilder() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private String field = \"\";\n" //
				+ "\n" //
				+ "    public static String doNotRefactorWithoutAssignment() {\n" //
				+ "        String concatenation = 123 + \"bar\";\n" //
				+ "        return concatenation + String.valueOf(456);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotRefactorNullString() {\n" //
				+ "        String text = null;\n" //
				+ "        text += \"foo\";\n" //
				+ "        text += \"bar\";\n" //
				+ "        text += \"foobar\";\n" //
				+ "        return text;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotRefactorConcatenationOfOnlyTwoStrings() {\n" //
				+ "        String text= \"foo\";\n" //
				+ "        text += \"bar\";\n" //
				+ "        return text;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotRefactorMultideclaration() {\n" //
				+ "        String serialization= \"\", anotherSerialization= \"\";\n" //
				+ "        serialization += \"foo\";\n" //
				+ "        serialization += \"bar\";\n" //
				+ "        serialization += \"foobar\";\n" //
				+ "        return serialization;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotRefactorStringUsedAsExpression() {\n" //
				+ "        String variable= \"foo\";\n" //
				+ "        variable += \"bar\";\n" //
				+ "        variable += \"foobar\";\n" //
				+ "        if ((variable+= \"bar\").contains(\"i\")) {\n" //
				+ "            return \"foobar\";\n" //
				+ "        }\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotUseStringBuilderWithoutAppending() {\n" //
				+ "        String variable= \"\";\n" //
				+ "        variable = \"foo\" + variable;\n" //
				+ "        variable += \"bar\";\n" //
				+ "        variable += \"foobar\";\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotRefactorWrongAssignmentOperator() {\n" //
				+ "        String variable= \"\";\n" //
				+ "        variable = \"foo\";\n" //
				+ "        variable += \"bar\";\n" //
				+ "        variable += \"foobar\";\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotRefactorBadAssignmentOperator() {\n" //
				+ "        String variable= \"\";\n" //
				+ "        variable += variable + \"foo\";\n" //
				+ "        variable += \"bar\";\n" //
				+ "        variable += \"foobar\";\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotUseStringBuilderWithoutConcatenation() {\n" //
				+ "        String variable = \"\";\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void doNotRefactorStringChangedAfterUse(String text) {\n" //
				+ "        String variable= \"\";\n" //
				+ "        variable += text + \"foo\";\n" //
				+ "        variable += \"bar\";\n" //
				+ "        variable += \"foobar\";\n" //
				+ "        System.out.println(variable);\n" //
				+ "        variable= variable + text + \"bar\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotBuildStringSeveralTimes() {\n" //
				+ "        String variable= \"\";\n" //
				+ "        variable += \"foo\";\n" //
				+ "        variable += \"bar\";\n" //
				+ "        variable += \"foobar\";\n" //
				+ "        variable = variable + \"bar\";\n" //
				+ "        return variable + variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static List<String> doNotStringifySeveralTimes(List<String> texts) {\n" //
				+ "        String variable= \"\";\n" //
				+ "        List<String> output= new ArrayList<String>();\n" //
				+ "\n" //
				+ "        for (String text : texts) {\n" //
				+ "            variable += text;\n" //
				+ "            variable += \"bar\";\n" //
				+ "            variable += \"foobar\";\n" //
				+ "            variable = variable + \",\";\n" //
				+ "            output.add(variable);\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return output;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static void doNotStringifySeveralTimesToo(List<String> words) {\n" //
				+ "        String variable= \"\";\n" //
				+ "        variable += \"foo\";\n" //
				+ "        variable = variable + \"bar\";\n" //
				+ "        variable += \"foobar\";\n" //
				+ "\n" //
				+ "        for (String word : words) {\n" //
				+ "            System.out.println(variable);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotRefactorStringsWithoutConcatenation(boolean isEnabled) {\n" //
				+ "        String variable1 = \"First variable\";\n" //
				+ "        String variable2 = \"Second variable\";\n" //
				+ "\n" //
				+ "        if (isEnabled) {\n" //
				+ "            variable1 += \"foo\";\n" //
				+ "            variable1 = variable2 + \"bar\";\n" //
				+ "        } else {\n" //
				+ "            variable2 += \"foo\";\n" //
				+ "            variable2 = variable1 + \"bar\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return variable1 + variable2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static String doNotUseStringBuilderOnParameter(String variable) {\n" //
				+ "        variable += \"foo\";\n" //
				+ "        variable += \"bar\";\n" //
				+ "        variable += \"foobar\";\n" //
				+ "        return variable;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotUseStringBuilderOnField() {\n" //
				+ "        field = \"Lorem\";\n" //
				+ "        field += \" ipsum\";\n" //
				+ "        field += \" dolor sit amet\";\n" //
				+ "        return field;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.STRINGBUILDER);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testPlainReplacement() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.File;\n" //
				+ "import java.util.regex.Matcher;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final String CONSTANT = \"&\";\n" //
				+ "    private static final String CONSTANT2 = \"+\";\n" //
				+ "\n" //
				+ "    public void refactorUsingString(String text, String placeholder, String value) {\n" //
				+ "        String result1 = text.replaceAll(\"&\", \"&amp;\");\n" //
				+ "        String result2 = text.replaceAll(\",:#\", \"/\");\n" //
				+ "        String result3 = text.replaceAll(\"\\\\^a\", \"b\" + \"c\");\n" //
				+ "        String result4 = text.replaceAll(CONSTANT, \"&amp;\");\n" //
				+ "        String result5 = text.replaceAll(\"\\\\.\", \"\\r\\n\");\n" //
				+ "        String result6 = text.replaceAll(\"-\\\\.-\", \"\\\\$\\\\.\\\\x\");\n" //
				+ "        String result7 = text.replaceAll(\"foo\", \"\\\\\\\\-\\\\$1\\\\s\");\n" //
				+ "        String result8 = text.replaceAll(\"foo\", \"bar\");\n" //
				+ "        String result9 = text.replaceAll(\"\\\\$0\\\\.02\", \"\\\\$0.50\");\n" //
				+ "        String result10 = text.replaceAll(/*Keep this comment*/\"n\"/*o*/, /*Keep this comment too*/\"\\\\$\\\\a\\\\\\\\\"/*y*/);\n" //
				+ "        String result11 = text.replaceAll(\"a\" + \"b\", \"c\\\\$\");\n" //
				+ "        String result12 = text.replaceAll(\"\\\\+\", CONSTANT);\n" //
				+ "        String result13 = text.replaceAll(CONSTANT, \"\\\\$\");\n" //
				+ "        String result14 = text.replaceAll(CONSTANT, CONSTANT2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeQuote(String text) {\n" //
				+ "        String result = text.replaceAll(Pattern.quote(File.separator), \"/\");\n" //
				+ "        String result2 = text.replaceAll(Pattern.quote(File.separator + \"a\"), \"\\\\.\");\n" //
				+ "        String result3 = text.replaceAll(Pattern.quote(placeholder), Matcher.quoteReplacement(value));\n" //
				+ "        String result4 = text.replaceAll(\"\\\\.\", Matcher.quoteReplacement(File.separator));\n" //
				+ "        String result5 = text.replaceAll(\"/\", Matcher.quoteReplacement(File.separator + \"\\n\"));\n" //
				+ "        String result6 = text.replaceAll(\"n\", Matcher.quoteReplacement(System.getProperty(\"java.version\")));\n" //
				+ "        String result7 = text.replaceAll(CONSTANT, Matcher.quoteReplacement(System.getProperty(\"java.version\")));\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorUsingChar(String text) {\n" //
				+ "        String result = text.replaceAll(\"\\\\.\", \"/\");\n" //
				+ "        String result2 = text.replaceAll(\"\\\\.\", \"/\");\n" //
				+ "        String result3 = text.replaceAll(\"/\", \".\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorUselessEscapingInReplacement() {\n" //
				+ "        return \"foo\".replaceAll(\"foo\", \"\\\\.\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorChained() {\n" //
				+ "        System.out.println(\"${p1}...???\".replaceAll(\"\\\\$\", \"\\\\\\\\\\\\$\")\n" //
				+ "            .replaceAll(\"\\\\.\", \"\\\\\\\\.\").replaceAll(\"\\\\?\", \"^\"));\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.File;\n" //
				+ "import java.util.regex.Matcher;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final String CONSTANT = \"&\";\n" //
				+ "    private static final String CONSTANT2 = \"+\";\n" //
				+ "\n" //
				+ "    public void refactorUsingString(String text, String placeholder, String value) {\n" //
				+ "        String result1 = text.replace(\"&\", \"&amp;\");\n" //
				+ "        String result2 = text.replace(\",:#\", \"/\");\n" //
				+ "        String result3 = text.replace(\"^a\", \"b\" + \"c\");\n" //
				+ "        String result4 = text.replace(CONSTANT, \"&amp;\");\n" //
				+ "        String result5 = text.replace(\".\", \"\\r\\n\");\n" //
				+ "        String result6 = text.replace(\"-.-\", \"$.x\");\n" //
				+ "        String result7 = text.replace(\"foo\", \"\\\\-$1s\");\n" //
				+ "        String result8 = text.replace(\"foo\", \"bar\");\n" //
				+ "        String result9 = text.replace(\"$0.02\", \"$0.50\");\n" //
				+ "        String result10 = text.replace(/*Keep this comment*/\"n\"/*o*/, /*Keep this comment too*/\"$a\\\\\"/*y*/);\n" //
				+ "        String result11 = text.replace(\"a\" + \"b\", \"c$\");\n" //
				+ "        String result12 = text.replace(\"+\", CONSTANT);\n" //
				+ "        String result13 = text.replace(CONSTANT, \"$\");\n" //
				+ "        String result14 = text.replace(CONSTANT, CONSTANT2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void removeQuote(String text) {\n" //
				+ "        String result = text.replace(File.separator, \"/\");\n" //
				+ "        String result2 = text.replace(File.separator + \"a\", \".\");\n" //
				+ "        String result3 = text.replace(placeholder, value);\n" //
				+ "        String result4 = text.replace(\".\", File.separator);\n" //
				+ "        String result5 = text.replace(\"/\", File.separator + \"\\n\");\n" //
				+ "        String result6 = text.replace(\"n\", System.getProperty(\"java.version\"));\n" //
				+ "        String result7 = text.replace(CONSTANT, System.getProperty(\"java.version\"));\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorUsingChar(String text) {\n" //
				+ "        String result = text.replace('.', '/');\n" //
				+ "        String result2 = text.replace('.', '/');\n" //
				+ "        String result3 = text.replace('/', '.');\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String refactorUselessEscapingInReplacement() {\n" //
				+ "        return \"foo\".replace(\"foo\", \".\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorChained() {\n" //
				+ "        System.out.println(\"${p1}...???\".replace(\"$\", \"\\\\$\")\n" //
				+ "            .replace(\".\", \"\\\\.\").replace('?', '^'));\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.PLAIN_REPLACEMENT);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.PlainReplacementCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUsePlainReplacement() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.regex.Matcher;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private static final String CONSTANT = \"|\";\n" //
				+ "\n" //
				+ "    public void doNotRefactorEscapableCharacters(String text) {\n" //
				+ "        String result1 = text.replaceAll(\"[ab]\", \"c\");\n" //
				+ "        String result2 = text.replaceAll(\"d.e\", \"foo\");\n" //
				+ "        String result3 = text.replaceAll(\"f?\", \"bar\");\n" //
				+ "        String result4 = text.replaceAll(\"g+\", \"foo\");\n" //
				+ "        String result5 = text.replaceAll(\"h*\", \"foo\");\n" //
				+ "        String result6 = text.replaceAll(\"i{42}\", \"foo\");\n" //
				+ "        String result7 = text.replaceAll(\"j{1,42}\", \"foo\");\n" //
				+ "        String result8 = text.replaceAll(\"(k)\", \"foo\");\n" //
				+ "        String result9 = text.replaceAll(\"^m\", \"foo\");\n" //
				+ "        String result10 = text.replaceAll(\"n$\", \"foo\");\n" //
				+ "        String result11 = text.replaceAll(\"\\\\s\", \"\");\n" //
				+ "        String result12 = text.replaceAll(\"a|b\", \"foo\");\n" //
				+ "        String result13 = text.replaceAll(\"\\r\\n|\\n\", \" \");\n" //
				+ "        String result14 = text.replaceAll(\"\\\\\\\\$\", System.getProperty(\"java.version\"));\n" //
				+ "        String result15 = text.replaceAll(System.getProperty(\"java.version\"), \"\");\n" //
				+ "        String result16 = text.replaceAll(CONSTANT, \"not &amp;\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorReplacementWithCapturedGroup(String text) {\n" //
				+ "        return text.replaceAll(\"foo\", \"$0\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorUnknownPattern(String text, String pattern) {\n" //
				+ "        return text.replaceAll(pattern, \"c\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorOtherMethod(Matcher matcher, String text) {\n" //
				+ "        return matcher.replaceAll(text);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorSurrogates(String text, String unquoted) {\n" //
				+ "        String result1 = text.replaceAll(\"\\ud83c\", \"\");\n" //
				+ "        String result2 = text.replaceAll(\"\\\\ud83c\", \"\");\n" //
				+ "        String result3 = text.replaceAll(\"\\udf09\", \"\\udf10\");\n" //
				+ "        String result4 = text.replaceAll(Pattern.quote(unquoted), \"\\udf10\");\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.PLAIN_REPLACEMENT);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testPlainReplacementPreview() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String previewHeader= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.io.File;\n" //
				+ "import java.util.regex.Matcher;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void preview(String text, String placeholder, String value) {\n";
		String previewFooter= "" //
				+ "    }\n" //
				+ "}\n";
		AbstractCleanUpCore cleanUp= new PlainReplacementCleanUpCore() {
			@Override
			protected boolean isEnabled(String key) {
				return false;
			}
		};
		String given= previewHeader + cleanUp.getPreview() + previewFooter;
		cleanUp= new PlainReplacementCleanUpCore() {
			@Override
			protected boolean isEnabled(String key) {
				return true;
			}
		};
		String expected= previewHeader + cleanUp.getPreview() + previewFooter;

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.PLAIN_REPLACEMENT);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.PlainReplacementCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testControlFlowMerge() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.function.Function;\n" //
				+ "import java.util.function.Predicate;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private Date j = new Date();\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove if statement */\n" //
				+ "    public void ifElseRemoveIfNoBrackets(boolean isValid, int i) {\n" //
				+ "        // Keep this!\n" //
				+ "        if (isValid)\n" //
				+ "            // Keep this comment\n" //
				+ "            i = 1;\n" //
				+ "        else\n" //
				+ "            i = (2 - 1) * 1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove if statement */\n" //
				+ "    public void ifElseRemoveIf(boolean b, int number) {\n" //
				+ "        if (b) {\n" //
				+ "            // Keep this comment\n" //
				+ "            number = 1;\n" //
				+ "        } else {\n" //
				+ "            number = 001;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove then case */\n" //
				+ "    public void ifElseRemoveThen(boolean condition, int i, int j) {\n" //
				+ "        if (condition) {\n" //
				+ "            // Keep this comment\n" //
				+ "            ++i;\n" //
				+ "        } else {\n" //
				+ "            j++;\n" //
				+ "            // Keep this comment\n" //
				+ "            i = i + 1;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove else case */\n" //
				+ "    public void ifElseRemoveElse(boolean b, int i, int j) {\n" //
				+ "        if (b) {\n" //
				+ "            j++;\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove second case */\n" //
				+ "    public void reverseMiddle(boolean isActive, boolean isEnabled, int i, int j) {\n" //
				+ "        if (isActive) {\n" //
				+ "            j++;\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        } else if (isEnabled) {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        } else {\n" //
				+ "            j++;\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove second case */\n" //
				+ "    public void reverseEmptySecond(boolean isActive, boolean isEnabled, int i, int j) {\n" //
				+ "        if (isActive) {\n" //
				+ "            j++;\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        } else if (isEnabled) {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        } else if (i > 0) {\n" //
				+ "            j--;\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        } else {\n" //
				+ "            j++;\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Only common code, Remove if statement */\n" //
				+ "    public void ifElseRemoveIfSeveralStatements(boolean b1, boolean b2, int i, int j) {\n" //
				+ "        if (b1) {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "            if (b2 && true) {\n" //
				+ "                i++;\n" //
				+ "            } else {\n" //
				+ "                j++;\n" //
				+ "            }\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "            if (false || !b2) {\n" //
				+ "                j++;\n" //
				+ "            } else {\n" //
				+ "                i++;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Not all cases covered, Do not remove anything */\n" //
				+ "    public void ifElseIfNoElseDoNotTouch(boolean isValid, int k, int l) {\n" //
				+ "        if (isValid) {\n" //
				+ "            k++;\n" //
				+ "            l++;\n" //
				+ "        } else if (!isValid) {\n" //
				+ "            k++;\n" //
				+ "            l++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Only common code: remove if statement */\n" //
				+ "    public void ifElseIfElseRemoveIf(boolean b, int i, int j) {\n" //
				+ "        if (b) {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "            j++;\n" //
				+ "        } else if (!b) {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "            j++;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "            j++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Specific code: keep some if statement */\n" //
				+ "    public void ifElseIfElseRemoveSomeIf(boolean b1, boolean b2, List<String> modifiableList, int i, int j) {\n" //
				+ "        if (b1) {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "\n" //
				+ "            j++;\n" //
				+ "        } else if (b2) {\n" //
				+ "            i++;\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "\n" //
				+ "            j++;\n" //
				+ "        } else if (modifiableList.remove(\"foo\")) {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "\n" //
				+ "            j++;\n" //
				+ "        } else {\n" //
				+ "            // Keep this comment\n" //
				+ "            i++;\n" //
				+ "\n" //
				+ "            j++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorMethodInvocation(boolean b, Object o) {\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(b);\n" //
				+ "            o.toString();\n" //
				+ "        } else {\n" //
				+ "            o.toString();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.function.Function;\n" //
				+ "import java.util.function.Predicate;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private Date j = new Date();\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove if statement */\n" //
				+ "    public void ifElseRemoveIfNoBrackets(boolean isValid, int i) {\n" //
				+ "        // Keep this!\n" //
				+ "        // Keep this comment\n" //
				+ "        i = 1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove if statement */\n" //
				+ "    public void ifElseRemoveIf(boolean b, int number) {\n" //
				+ "        // Keep this comment\n" //
				+ "        number = 1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove then case */\n" //
				+ "    public void ifElseRemoveThen(boolean condition, int i, int j) {\n" //
				+ "        if (!condition) {\n" //
				+ "            j++;\n" //
				+ "        }\n" //
				+ "        // Keep this comment\n" //
				+ "        ++i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove else case */\n" //
				+ "    public void ifElseRemoveElse(boolean b, int i, int j) {\n" //
				+ "        if (b) {\n" //
				+ "            j++;\n" //
				+ "        }\n" //
				+ "        // Keep this comment\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove second case */\n" //
				+ "    public void reverseMiddle(boolean isActive, boolean isEnabled, int i, int j) {\n" //
				+ "        if (isActive) {\n" //
				+ "            j++;\n" //
				+ "        } else if (!isEnabled) {\n" //
				+ "            j++;\n" //
				+ "        }\n" //
				+ "        // Keep this comment\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Common code: i++, Remove second case */\n" //
				+ "    public void reverseEmptySecond(boolean isActive, boolean isEnabled, int i, int j) {\n" //
				+ "        if (isActive) {\n" //
				+ "            j++;\n" //
				+ "        } else if (isEnabled) {\n" //
				+ "        } else if (i > 0) {\n" //
				+ "            j--;\n" //
				+ "        } else {\n" //
				+ "            j++;\n" //
				+ "        }\n" //
				+ "        // Keep this comment\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Only common code, Remove if statement */\n" //
				+ "    public void ifElseRemoveIfSeveralStatements(boolean b1, boolean b2, int i, int j) {\n" //
				+ "        // Keep this comment\n" //
				+ "        i++;\n" //
				+ "        if (b2 && true) {\n" //
				+ "            i++;\n" //
				+ "        } else {\n" //
				+ "            j++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Not all cases covered, Do not remove anything */\n" //
				+ "    public void ifElseIfNoElseDoNotTouch(boolean isValid, int k, int l) {\n" //
				+ "        if (isValid) {\n" //
				+ "            k++;\n" //
				+ "            l++;\n" //
				+ "        } else if (!isValid) {\n" //
				+ "            k++;\n" //
				+ "            l++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Only common code: remove if statement */\n" //
				+ "    public void ifElseIfElseRemoveIf(boolean b, int i, int j) {\n" //
				+ "        // Keep this comment\n" //
				+ "        i++;\n" //
				+ "        j++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /** Specific code: keep some if statement */\n" //
				+ "    public void ifElseIfElseRemoveSomeIf(boolean b1, boolean b2, List<String> modifiableList, int i, int j) {\n" //
				+ "        if (b1) {\n" //
				+ "        } else if (b2) {\n" //
				+ "            i++;\n" //
				+ "        } else if (modifiableList.remove(\"foo\")) {\n" //
				+ "        }\n" //
				+ "        // Keep this comment\n" //
				+ "        i++;\n" //
				+ "\n" //
				+ "        j++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void refactorMethodInvocation(boolean b, Object o) {\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(b);\n" //
				+ "        }\n" //
				+ "        o.toString();\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.CONTROLFLOW_MERGE);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.ControlFlowMergeCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotControlFlowMerge() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "import java.util.function.Function;\n" //
				+ "import java.util.function.Predicate;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    private Date j = new Date();\n" //
				+ "\n" //
				+ "    /** No common code, Do not remove anything */\n" //
				+ "    public void doNotRemoveNotCommonCode(boolean condition, int number1, int number2) {\n" //
				+ "        if (condition) {\n" //
				+ "            number1++;\n" //
				+ "        } else {\n" //
				+ "            number2++;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorDifferentVariablesInReturn(boolean condition) {\n" //
				+ "        if (condition) {\n" //
				+ "            int i = 1;\n" //
				+ "            return i;\n" //
				+ "        } else {\n" //
				+ "            int i = 2;\n" //
				+ "            return i;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorNoElse(boolean b) {\n" //
				+ "        if (b) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "        return 1;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorWithNameConflict(boolean isActive) {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        if (isActive) {\n" //
				+ "            int j = 1;\n" //
				+ "            i = j + 10;\n" //
				+ "        } else {\n" //
				+ "            int j = 1;\n" //
				+ "            i = j + 10;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        int j = 123;\n" //
				+ "        System.out.println(\"Other number: \" + j);\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorWithNameConflictInBlock(boolean isActive) {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        if (isActive) {\n" //
				+ "            int j = 1;\n" //
				+ "            i = j + 10;\n" //
				+ "        } else {\n" //
				+ "            int j = 1;\n" //
				+ "            i = j + 10;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        if (isActive) {\n" //
				+ "            int j = 123;\n" //
				+ "            System.out.println(\"Other number: \" + j);\n" //
				+ "        }\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotRefactorWithNameConfusion(boolean b) {\n" //
				+ "        int i;\n" //
				+ "\n" //
				+ "        if (b) {\n" //
				+ "            int j = 1;\n" //
				+ "            i = j + 10;\n" //
				+ "        } else {\n" //
				+ "            int j = 1;\n" //
				+ "            i = j + 10;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        System.out.println(\"Today: \" + j);\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotMoveVarOutsideItsScope(boolean b) {\n" //
				+ "        if (b) {\n" //
				+ "            int dontMoveMeIMLocal = 1;\n" //
				+ "            return dontMoveMeIMLocal + 10;\n" //
				+ "        } else {\n" //
				+ "            int dontMoveMeIMLocal = 2;\n" //
				+ "            return dontMoveMeIMLocal + 10;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public static Predicate<String> doNotMergeDifferentLambdaExpression(final boolean caseSensitive, final String... allowedSet) {\n" //
				+ "        if (caseSensitive) {\n" //
				+ "            return x -> Arrays.stream(allowedSet).anyMatch(y -> (x == null && y == null) || (x != null && x.equals(y)));\n" //
				+ "        } else {\n" //
				+ "            Function<String,String> toLower = x -> x == null ? null : x.toLowerCase();\n" //
				+ "            return x -> Arrays.stream(allowedSet).map(toLower).anyMatch(y -> (x == null && y == null) || (x != null && toLower.apply(x).equals(y)));\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotRefactorWithNotFallingThroughCase(boolean isValid, boolean isEnabled, int i, int j) {\n" //
				+ "        if (isValid) {\n" //
				+ "            i++;\n" //
				+ "            if (isEnabled && true) {\n" //
				+ "                i++;\n" //
				+ "            } else {\n" //
				+ "                j++;\n" //
				+ "            }\n" //
				+ "        } else if (i > 0) {\n" //
				+ "            \"Do completely other things\".chars();\n" //
				+ "        } else {\n" //
				+ "            i++;\n" //
				+ "            if (false || !isEnabled) {\n" //
				+ "                j++;\n" //
				+ "            } else {\n" //
				+ "                i++;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return \"Common code\";\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROLFLOW_MERGE);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testRegExPrecompilationInLambda() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "    I1 i1 = () -> {\n" //
				+ "        String p = \"abcd\";\n" //
				+ "        String x = \"abcdef\";\n" //
				+ "        String y = \"bcdefg\";\n" //
				+ "        String[] a = x.split(p);\n" //
				+ "        String[] b = y.split(p);\n" //
				+ "    };\n" //
				+ "\n" //
				+ "    interface I1 {\n" //
				+ "        public void m();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void foo() {\n" //
				+ "        I1 i1= () -> {\n" //
				+ "            String p = \"abcd\";\n" //
				+ "            String x = \"abcdef\";\n" //
				+ "            String y = \"bcdefg\";\n" //
				+ "            String[] a = x.split(p);\n" //
				+ "            String[] b = y.split(p);\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PRECOMPILE_REGEX);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "import java.util.regex.Pattern;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "    private static final Pattern p_pattern = Pattern.compile(\"abcd\");\n" //
				+ "    I1 i1 = () -> {\n" //
				+ "        Pattern p = p_pattern;\n" //
				+ "        String x = \"abcdef\";\n" //
				+ "        String y = \"bcdefg\";\n" //
				+ "        String[] a = p.split(x);\n" //
				+ "        String[] b = p.split(y);\n" //
				+ "    };\n" //
				+ "\n" //
				+ "    interface I1 {\n" //
				+ "        public void m();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static final Pattern p_pattern2 = Pattern.compile(\"abcd\");\n" //
				+ "\n" //
				+ "    public void foo() {\n" //
				+ "        I1 i1= () -> {\n" //
				+ "            Pattern p = p_pattern2;\n" //
				+ "            String x = \"abcdef\";\n" //
				+ "            String y = \"bcdefg\";\n" //
				+ "            String[] a = p.split(x);\n" //
				+ "            String[] b = p.split(y);\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testDoNotRefactorRegExWithPrecompilation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Arrays;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public boolean doNotUsePatternForOneUse(String date) {\n" //
				+ "       String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "       return date.matches(dateValidation);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotUsePatternWithOtherUse(String date1, String date2) {\n" //
				+ "       String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "       System.out.println(\"The pattern is: \" + dateValidation);\n" //
				+ "\n" //
				+ "       return date1.matches(dateValidation) && date2.matches(dateValidation);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotUsePatternWithOtherMethod(String date1, String date2) {\n" //
				+ "       String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "       return date1.matches(dateValidation) && \"\".equals(date2.replace(dateValidation, \"\"));\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotUsePatternInMultiDeclaration(String date1, String date2) {\n" //
				+ "       String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\", foo= \"bar\";\n" //
				+ "\n" //
				+ "       return date1.matches(dateValidation) && date2.matches(dateValidation);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotUsePatternOnMisplacedUse(String date1, String date2) {\n" //
				+ "       String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "       return dateValidation.matches(date1) && dateValidation.matches(date2);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String doNotUsePatternOnMisplacedParameter(String date1, String date2) {\n" //
				+ "       String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "       String dateText1= date1.replaceFirst(\"0000-00-00\", dateValidation);\n" //
				+ "       String dateText2= date2.replaceAll(\"0000-00-00\", dateValidation);\n" //
				+ "\n" //
				+ "       return dateText1 + dateText2;\n" //
				+ "    }\n" //
				+ "    public String doNotUsePatternOnSimpleSplit1(String speech1, String speech2) {\n" //
				+ "       String line= \"a\";\n" //
				+ "\n" //
				+ "       String[] phrases1= speech1.split(line);\n" //
				+ "       String[] phrases2= speech2.split(line, 1);\n" //
				+ "       return phrases1[0] + phrases2[0];\n" //
				+ "    }\n" //
				+ "    public String doNotUsePatternOnSimpleSplit2(String speech1, String speech2) {\n" //
				+ "       String line= \"\\\\;\";\n" //
				+ "\n" //
				+ "       String[] phrases1= speech1.split(line);\n" //
				+ "       String[] phrases2= speech2.split(line, 1);\n" //
				+ "       return phrases1[0] + phrases2[0];\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PRECOMPILE_REGEX);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testRegExPrecompilationWithExistingImport() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import javax.validation.constraints.Pattern;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private String code;\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "\n" //
				+ "   public boolean usePattern(String date1, String date2) {\n" //
				+ "       // Keep this comment\n" //
				+ "       String dateValidation= \"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\";\n" //
				+ "\n" //
				+ "       // Keep this comment too\n" //
				+ "       return date1.matches(dateValidation) && date2.matches(dateValidation);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    @Pattern(regexp=\"\\\\d{4}\",\n" //
				+ "        message=\"The code should contain exactly four numbers.\")\n" //
				+ "    public String getCode() {\n" //
				+ "        return code;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void setCode(String code) {\n" //
				+ "        this.code= code;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.PRECOMPILE_REGEX);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import javax.validation.constraints.Pattern;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private String code;\n" //
				+ "    private String dateValidation= \".*\";\n" //
				+ "    private static final java.util.regex.Pattern dateValidation_pattern = java.util.regex.Pattern\n"
				+ "            .compile(\"\\\\d{4}\\\\-\\\\d{2}\\\\-\\\\d{2}\");\n" //
				+ "\n" //
				+ "    public boolean usePattern(String date1, String date2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        java.util.regex.Pattern dateValidation= dateValidation_pattern;\n" //
				+ "\n" //
				+ "        // Keep this comment too\n" //
				+ "        return dateValidation.matcher(date1).matches() && dateValidation.matcher(date2).matches();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    @Pattern(regexp=\"\\\\d{4}\",\n" //
				+ "            message=\"The code should contain exactly four numbers.\")\n" //
				+ "    public String getCode() {\n" //
				+ "        return code;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void setCode(String code) {\n" //
				+ "        this.code= code;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testEmbeddedIf() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int collapseIfStatements(boolean isActive, boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (isValid) {\n" //
				+ "                // Keep this comment also\n" //
				+ "                return 1;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseInnerLoneIf(boolean isActive, boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive) {\n" //
				+ "            if (isValid)\n" //
				+ "                return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseOutterLoneIf(boolean isActive, boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive)\n" //
				+ "            if (isValid) {\n" //
				+ "                return 1;\n" //
				+ "            }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseWithFourOperands(int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (0 < i1 && i1 < 10) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (0 < i2 && i2 < 10) {\n" //
				+ "                // Keep this comment also\n" //
				+ "                return 1;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseIfStatementsAddParenthesesIfDifferentConditionalOperator(boolean isActive, boolean isValid, boolean isEditMode) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (isValid || isEditMode) {\n" //
				+ "                // Keep this comment also\n" //
				+ "                return 1;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseIfWithOROperator(boolean isActive, boolean isValid, boolean isEditMode) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive) {\n" //
				+ "            // Keep this comment too\n" //
				+ "            if (isValid | isEditMode) {\n" //
				+ "                // Keep this comment also\n" //
				+ "                return 1;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.RAISE_EMBEDDED_IF);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int collapseIfStatements(boolean isActive, boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        // Keep this comment too\n" //
				+ "        if (isActive && isValid) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseInnerLoneIf(boolean isActive, boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive && isValid)\n" //
				+ "            return 1;\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseOutterLoneIf(boolean isActive, boolean isValid) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isActive && isValid) {\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseWithFourOperands(int i1, int i2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        // Keep this comment too\n" //
				+ "        if ((0 < i1 && i1 < 10) && (0 < i2 && i2 < 10)) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseIfStatementsAddParenthesesIfDifferentConditionalOperator(boolean isActive, boolean isValid, boolean isEditMode) {\n" //
				+ "        // Keep this comment\n" //
				+ "        // Keep this comment too\n" //
				+ "        if (isActive && (isValid || isEditMode)) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int collapseIfWithOROperator(boolean isActive, boolean isValid, boolean isEditMode) {\n" //
				+ "        // Keep this comment\n" //
				+ "        // Keep this comment too\n" //
				+ "        if (isActive && (isValid | isEditMode)) {\n" //
				+ "            // Keep this comment also\n" //
				+ "            return 1;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "}\n";
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.EmbeddedIfCleanup_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { sample });
	}

	@Test
	public void testDoNotRaiseEmbeddedIf() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public int doNotCollapseWithFiveOperands(int i1, int i2) {\n" //
				+ "        if (0 < i1 && i1 < 10) {\n" //
				+ "            if (100 < i2 && i2 < 200 || i2 < 0) {\n" //
				+ "                return 1;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotCollapseTwoLoneIfsWithEndOfLineComment(boolean isActive, boolean isValid) {\n" //
				+ "        if (isActive)\n" //
				+ "            if (isValid)\n" //
				+ "                return 1; // This comment makes crash the parser\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotCollapseOuterIfWithElseStatement(boolean isActive, boolean isValid) {\n" //
				+ "        if (isActive) {\n" //
				+ "            if (isValid) {\n" //
				+ "                int i = 0;\n" //
				+ "            }\n" //
				+ "        } else {\n" //
				+ "            int i = 0;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotCollapseIfWithElseStatement2(boolean isActive, boolean isValid) {\n" //
				+ "        if (isActive) {\n" //
				+ "            if (isValid) {\n" //
				+ "                int i = 0;\n" //
				+ "            } else {\n" //
				+ "                int i = 0;\n" //
				+ "            }\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.RAISE_EMBEDDED_IF);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testExtractIncrement() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E extends ArrayList<String> {\n" //
				+ "    private static final long serialVersionUID = -5909621993540999616L;\n" //
				+ "\n" //
				+ "    private int field= 0;\n" //
				+ "\n" //
				+ "    public E(int i) {\n" //
				+ "        super(i++);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public E(int doNotRefactor, boolean isEnabled) {\n" //
				+ "        super(++doNotRefactor);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public E(int i, int j) {\n" //
				+ "        this(i++);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String moveIncrementBeforeIf(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (++i > 0) {\n" //
				+ "            return \"Positive\";\n" //
				+ "        } else {\n" //
				+ "            return \"Negative\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String moveDecrementBeforeIf(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        if (--i > 0) {\n" //
				+ "            return \"Positive\";\n" //
				+ "        } else {\n" //
				+ "            return \"Negative\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveDecrementBeforeThrow(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        throw new NullPointerException(\"++i \" + ++i);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        // Keep this comment\n" //
				+ "        String[] texts= new String[++i];\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement2(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        texts.wait(++i);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement3(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        int j= i++, k= ++z;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement4(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        j= i-- + 123;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement5(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        boolean isString= obj[++i] instanceof String;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement6(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        List<Date> dates= new ArrayList<>(--i);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement7(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        long l= (long)i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement8(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        int m= (i++);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement9(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        boolean isEqual= !(i++ == 10);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement10(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        theClass[i++].field--;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement11(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        int[] integers= {i++, 1, 2, 3};\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveIncrementOutsideStatement12(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        return ++i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean moveIncrementOutsideInfix(int i, boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean isEqual= (i++ == 10) && isEnabled;\n" //
				+ "        return isEqual;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String moveIncrementOutsideSuperMethod(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return super.remove(++i);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean moveIncrementOutsideEagerInfix(int i, boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean isEqual= isEnabled & (i++ == 10);\n" //
				+ "        return isEqual;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveIncrementOutsideTernaryExpression(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        int j= (i++ == 10) ? 10 : 20;\n" //
				+ "        return j * 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveIncrementInIf(int i, boolean isEnabled) {\n" //
				+ "        if (isEnabled)\n" //
				+ "            return ++i;\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveIncrementInSwitch(int i, int discriminant) {\n" //
				+ "        switch (discriminant) {\n" //
				+ "        case 0:\n" //
				+ "                return ++i;\n" //
				+ "        case 1:\n" //
				+ "                return --i;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.Date;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public class E extends ArrayList<String> {\n" //
				+ "    private static final long serialVersionUID = -5909621993540999616L;\n" //
				+ "\n" //
				+ "    private int field= 0;\n" //
				+ "\n" //
				+ "    public E(int i) {\n" //
				+ "        super(i);\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public E(int doNotRefactor, boolean isEnabled) {\n" //
				+ "        super(++doNotRefactor);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public E(int i, int j) {\n" //
				+ "        this(i);\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String moveIncrementBeforeIf(int i) {\n" //
				+ "        i++;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            return \"Positive\";\n" //
				+ "        } else {\n" //
				+ "            return \"Negative\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String moveDecrementBeforeIf(int i) {\n" //
				+ "        i--;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i > 0) {\n" //
				+ "            return \"Positive\";\n" //
				+ "        } else {\n" //
				+ "            return \"Negative\";\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveDecrementBeforeThrow(int i) {\n" //
				+ "        i++;\n" //
				+ "        // Keep this comment\n" //
				+ "        throw new NullPointerException(\"++i \" + i);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        i++;\n" //
				+ "        // Keep this comment\n" //
				+ "        String[] texts= new String[i];\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement2(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        i++;\n" //
				+ "        texts.wait(i);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement3(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        int j= i, k= ++z;\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement4(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        j= i + 123;\n" //
				+ "        i--;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement5(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        i++;\n" //
				+ "        boolean isString= obj[i] instanceof String;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement6(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        i--;\n" //
				+ "        List<Date> dates= new ArrayList<>(i);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement7(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        long l= (long)i;\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement8(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        int m= i;\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement9(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        boolean isEqual= !(i == 10);\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement10(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        theClass[i].field--;\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveIncrementOutsideStatement11(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        int[] integers= {i, 1, 2, 3};\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveIncrementOutsideStatement12(int i, int z, Object[] obj, E[] theClass) throws InterruptedException {\n" //
				+ "        i++;\n" //
				+ "        return i;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean moveIncrementOutsideInfix(int i, boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean isEqual= (i == 10) && isEnabled;\n" //
				+ "        i++;\n" //
				+ "        return isEqual;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String moveIncrementOutsideSuperMethod(int i) {\n" //
				+ "        i++;\n" //
				+ "        // Keep this comment\n" //
				+ "        return super.remove(i);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean moveIncrementOutsideEagerInfix(int i, boolean isEnabled) {\n" //
				+ "        // Keep this comment\n" //
				+ "        boolean isEqual= isEnabled & (i == 10);\n" //
				+ "        i++;\n" //
				+ "        return isEqual;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveIncrementOutsideTernaryExpression(int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        int j= (i == 10) ? 10 : 20;\n" //
				+ "        i++;\n" //
				+ "        return j * 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveIncrementInIf(int i, boolean isEnabled) {\n" //
				+ "        if (isEnabled) {\n" //
				+ "            i++;\n" //
				+ "            return i;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int moveIncrementInSwitch(int i, int discriminant) {\n" //
				+ "        switch (discriminant) {\n" //
				+ "        case 0:\n" //
				+ "                i++;\n" //
				+ "                return i;\n" //
				+ "        case 1:\n" //
				+ "                return --i;\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return 0;\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.EXTRACT_INCREMENT);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.CodeStyleCleanUp_ExtractIncrement_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotExtractIncrement() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public String doNotMoveIncrementAfterIf(int i) {\n" //
				+ "        String result= null;\n" //
				+ "\n" //
				+ "        if (i++ > 0) {\n" //
				+ "            result= \"Positive\";\n" //
				+ "        } else {\n" //
				+ "            result= \"Negative\";\n" //
				+ "        }\n" //
				+ "\n" //
				+ "        return result;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotMoveDecrementAfterReturn(int i) {\n" //
				+ "        return i--;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotMoveDecrementAfterThrow(int i) {\n" //
				+ "        throw new NullPointerException(\"i++ \" + i++);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotMoveIncrementAfterFallThrough(boolean isEnabled, int i) {\n" //
				+ "        if (i-- > 0) {\n" //
				+ "            return i++;\n" //
				+ "        } else {\n" //
				+ "            throw new NullPointerException(\"i++ \" + i++);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotMoveIncrementOutsideConditionalInfix(int i, boolean isEnabled) {\n" //
				+ "        boolean isEqual= isEnabled && (i++ == 10);\n" //
				+ "        return isEqual;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotMoveIncrementOutsideTernaryExpression(int i) {\n" //
				+ "        int j= (i == 10) ? i++ : 20;\n" //
				+ "        return j * 2;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public int doNotMoveIncrementOnReadVariable(int i) {\n" //
				+ "        int j= i++ + i++;\n" //
				+ "        int k= i++ + i;\n" //
				+ "        int l= i + i++;\n" //
				+ "        int m= (i = 0) + i++;\n" //
				+ "        return j + k + l + m;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorIncrementStatement(int i) {\n" //
				+ "        i++;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMoveIncrementOutsideWhile(int i) {\n" //
				+ "        while (i-- > 0) {\n" //
				+ "            System.out.println(\"Must decrement on each loop\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMoveIncrementOutsideDoWhile(int i) {\n" //
				+ "        do {\n" //
				+ "            System.out.println(\"Must decrement on each loop\");\n" //
				+ "        } while (i-- > 0);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMoveIncrementOutsideFor() {\n" //
				+ "        for (int i = 0; i < 10; i++) {\n" //
				+ "            System.out.println(\"Must increment on each loop\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMoveIncrementOutsideElseIf(int i) {\n" //
				+ "        if (i == 0) {\n" //
				+ "            System.out.println(\"I equals zero\");\n" //
				+ "        } else if (i++ == 10) {\n" //
				+ "            System.out.println(\"I has equaled ten\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.EXTRACT_INCREMENT);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testPullUpAssignment() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String input= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Queue;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void moveLeftHandSideAssignmentBeforeIf(Queue<Integer> queue) {\n" //
				+ "        Integer i;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((i = queue.poll()) != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveRightHandSideAssignmentBeforeIf(Queue<Integer> q) {\n" //
				+ "        Integer number;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null != (number = q.poll())) {\n" //
				+ "            System.out.println(\"Value=\" + number);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBeforeIfMultipleParenthesesToRemove(Queue<Integer> q) {\n" //
				+ "        Integer i;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((((i = q.poll()))) != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBeforeIfAndMergeWithDeclaration(Queue<Integer> q) {\n" //
				+ "        Integer i;\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((i = q.poll(/* Keep this comment too */)) != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBelowDeclaration(Queue<Integer> q) {\n" //
				+ "        Integer i = q.poll();\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((i = q.poll()) != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void erasePassiveValue(Queue<Integer> q) {\n" //
				+ "        Integer i = 0;\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((i = q.poll()) != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentWithoutParenthesis(Queue<Boolean> q) {\n" //
				+ "        Boolean b;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (b = q.poll()) {\n" //
				+ "            System.out.println(\"Value=\" + b);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBeforeIfAtConditionOfTernaryExpression(String s, int i) {\n" //
				+ "        final char c;\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((c = s.charAt(i)) == 'A' ? c == 'B' : c == 'C') {\n" //
				+ "            System.out.println(\"A, B or C\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBeforeIfAtStartOfInfixExpression(String s, int i) {\n" //
				+ "        final char c;\n" //
				+ "        // Keep this comment\n" //
				+ "        if ((c = s.charAt(i)) == 'A' || c == 'B' || c == 'C') {\n" //
				+ "            System.out.println(\"A, B or C\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveNotConditionalAssignment(String s, int i, boolean isValid) {\n" //
				+ "        final char c;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isValid | (c = s.charAt(i)) == 'A') {\n" //
				+ "            System.out.println(\"valid or A\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentInComplexExpression(String s, int i, boolean isValid) {\n" //
				+ "        final char c;\n" //
				+ "        // Keep this comment\n" //
				+ "        if (!(isValid | (i == 10 & (c = s.charAt(i)) == 'A'))) {\n" //
				+ "            System.out.println(\"valid or A\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean refactorSingleStatementBlock(int i, int j) {\n" //
				+ "        if (i > 0)\n" //
				+ "            if ((i = j) < 10)\n" //
				+ "                return true;\n" //
				+ "        return false;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveLeftHandSideAssignmentInSwitch(Queue<Integer> q, int discriminant) {\n" //
				+ "        Integer i;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "        switch (discriminant) {\n" //
				+ "        case 0:\n" //
				+ "            // Keep this comment\n" //
				+ "            if ((i = q.poll()) != null) {\n" //
				+ "                System.out.println(\"Value=\" + i);\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Empty\");\n" //
				+ "            }\n" //
				+ "        case 1:\n" //
				+ "            System.out.println(\"Another case\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentAndKeepUsedInitialization(Queue<Boolean> inputQueue) {\n" //
				+ "        Boolean overusedVariable = Boolean.TRUE, doNotForgetMe = overusedVariable;\n" //
				+ "\n" //
				+ "        // Keep this comment\n" //
				+ "        if (overusedVariable = inputQueue.poll()) {\n" //
				+ "            System.out.println(\"Value=\" + doNotForgetMe);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", input, false, null);

		enable(CleanUpConstants.PULL_UP_ASSIGNMENT);

		String output= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Queue;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void moveLeftHandSideAssignmentBeforeIf(Queue<Integer> queue) {\n" //
				+ "        Integer i;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "        i = queue.poll();\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveRightHandSideAssignmentBeforeIf(Queue<Integer> q) {\n" //
				+ "        Integer number;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "        number = q.poll();\n" //
				+ "        // Keep this comment\n" //
				+ "        if (null != number) {\n" //
				+ "            System.out.println(\"Value=\" + number);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBeforeIfMultipleParenthesesToRemove(Queue<Integer> q) {\n" //
				+ "        Integer i;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "        i = q.poll();\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBeforeIfAndMergeWithDeclaration(Queue<Integer> q) {\n" //
				+ "        Integer i = q.poll(/* Keep this comment too */);\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBelowDeclaration(Queue<Integer> q) {\n" //
				+ "        Integer i = q.poll();\n" //
				+ "        i = q.poll();\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void erasePassiveValue(Queue<Integer> q) {\n" //
				+ "        Integer i = q.poll();\n" //
				+ "        // Keep this comment\n" //
				+ "        if (i != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentWithoutParenthesis(Queue<Boolean> q) {\n" //
				+ "        Boolean b = q.poll();\n" //
				+ "        // Keep this comment\n" //
				+ "        if (b) {\n" //
				+ "            System.out.println(\"Value=\" + b);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBeforeIfAtConditionOfTernaryExpression(String s, int i) {\n" //
				+ "        final char c = s.charAt(i);\n" //
				+ "        // Keep this comment\n" //
				+ "        if (c == 'A' ? c == 'B' : c == 'C') {\n" //
				+ "            System.out.println(\"A, B or C\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentBeforeIfAtStartOfInfixExpression(String s, int i) {\n" //
				+ "        final char c = s.charAt(i);\n" //
				+ "        // Keep this comment\n" //
				+ "        if (c == 'A' || c == 'B' || c == 'C') {\n" //
				+ "            System.out.println(\"A, B or C\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveNotConditionalAssignment(String s, int i, boolean isValid) {\n" //
				+ "        final char c = s.charAt(i);\n" //
				+ "        // Keep this comment\n" //
				+ "        if (isValid | c == 'A') {\n" //
				+ "            System.out.println(\"valid or A\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentInComplexExpression(String s, int i, boolean isValid) {\n" //
				+ "        final char c = s.charAt(i);\n" //
				+ "        // Keep this comment\n" //
				+ "        if (!(isValid | (i == 10 & c == 'A'))) {\n" //
				+ "            System.out.println(\"valid or A\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean refactorSingleStatementBlock(int i, int j) {\n" //
				+ "        if (i > 0) {\n" //
				+ "            i = j;\n" //
				+ "            if (i < 10)\n" //
				+ "                return true;\n" //
				+ "        }\n" //
				+ "        return false;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveLeftHandSideAssignmentInSwitch(Queue<Integer> q, int discriminant) {\n" //
				+ "        Integer i;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "        switch (discriminant) {\n" //
				+ "        case 0:\n" //
				+ "                i = q.poll();\n" //
				+ "                // Keep this comment\n" //
				+ "            if (i != null) {\n" //
				+ "                System.out.println(\"Value=\" + i);\n" //
				+ "            } else {\n" //
				+ "                System.out.println(\"Empty\");\n" //
				+ "            }\n" //
				+ "        case 1:\n" //
				+ "            System.out.println(\"Another case\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void moveAssignmentAndKeepUsedInitialization(Queue<Boolean> inputQueue) {\n" //
				+ "        Boolean overusedVariable = Boolean.TRUE, doNotForgetMe = overusedVariable;\n" //
				+ "\n" //
				+ "        overusedVariable = inputQueue.poll();\n" //
				+ "        // Keep this comment\n" //
				+ "        if (overusedVariable) {\n" //
				+ "            System.out.println(\"Value=\" + doNotForgetMe);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.CodeStyleCleanUp_PullUpAssignment_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { output });
	}

	@Test
	public void testDoNotPullUpAssignment() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Queue;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public void doNotRefactor(Queue<Integer> q) {\n" //
				+ "        Integer i;\n" //
				+ "        System.out.println(\"Before polling\");\n" //
				+ "\n" //
				+ "        // Keep this comment\n" //
				+ "        if (q == null) {\n" //
				+ "            System.out.println(\"Null queue\");\n" //
				+ "        } else if ((i = q.poll()) != null) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMoveAssignmentBeforeIfAtLeftOperandOfTernaryExpression(String s, int i, char c) {\n" //
				+ "        if (c == 'A' ? (c = s.charAt(i)) == 'B' : c == 'C') {\n" //
				+ "            System.out.println(\"Found\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not found\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMoveAssignmentBeforeIfAtRightOperandOfTernaryExpression(String s, int i, char c) {\n" //
				+ "        if (c == 'A' ? c == 'B' : (c = s.charAt(i)) == 'C') {\n" //
				+ "            System.out.println(\"Found\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not found\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMoveAssignmentBeforeIfInsideInfixExpression(String s, int i, char c) {\n" //
				+ "        if (c == 'A' || (c = s.charAt(i)) == 'A' || c == 'B' || c == 'C') {\n" //
				+ "            System.out.println(\"A, B or C\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotMoveAssignmentAfterActiveCondition(String s, int i, char c) {\n" //
				+ "        if (i++ == 10 || (c = s.charAt(i)) == 'A' || c == 'B' || c == 'C') {\n" //
				+ "            System.out.println(\"A, B or C\");\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Not A, B or C\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public void doNotRefactorConditionalAnd(Queue<Boolean> q, boolean isValid) {\n" //
				+ "        Boolean i;\n" //
				+ "\n" //
				+ "        if (isValid && (i = q.poll())) {\n" //
				+ "            System.out.println(\"Value=\" + i);\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"Empty\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.PULL_UP_ASSIGNMENT);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testRemoveQualifier02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo() {return 0;}\n" //
				+ "    public int getFoo() {\n" //
				+ "        return this.foo();\n" //
				+ "    }   \n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo() {return 0;}\n" //
				+ "    public int getFoo() {\n" //
				+ "        return foo();\n" //
				+ "    }   \n" //
				+ "}\n";
		String expected1= sample;

		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(FixMessages.CodeStyleFix_removeThis_groupDescription)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveQualifier03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo;\n" //
				+ "    public int bar;\n" //
				+ "    public class E1Inner {\n" //
				+ "        private int bar;\n" //
				+ "        public int getFoo() {\n" //
				+ "            E1.this.bar= this.bar;\n" //
				+ "            return E1.this.foo;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo;\n" //
				+ "    public int bar;\n" //
				+ "    public class E1Inner {\n" //
				+ "        private int bar;\n" //
				+ "        public int getFoo() {\n" //
				+ "            E1.this.bar= bar;\n" //
				+ "            return foo;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveQualifier04() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo() {return 0;}\n" //
				+ "    public int bar() {return 0;}\n" //
				+ "    public class E1Inner {\n" //
				+ "        private int bar() {return 1;}\n" //
				+ "        public int getFoo() {\n" //
				+ "            E1.this.bar(); \n" //
				+ "            this.bar();\n" //
				+ "            return E1.this.foo();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public int foo() {return 0;}\n" //
				+ "    public int bar() {return 0;}\n" //
				+ "    public class E1Inner {\n" //
				+ "        private int bar() {return 1;}\n" //
				+ "        public int getFoo() {\n" //
				+ "            E1.this.bar(); \n" //
				+ "            bar();\n" //
				+ "            return foo();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveQualifierBug134720() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        this.setEnabled(true);\n" //
				+ "    }\n" //
				+ "    private void setEnabled(boolean b) {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        setEnabled(true);\n" //
				+ "    }\n" //
				+ "    private void setEnabled(boolean b) {}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveQualifierBug150481_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public class Inner extends E {\n" //
				+ "        public void test() {\n" //
				+ "            E.this.foo();\n" //
				+ "            this.foo();\n" //
				+ "            foo();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void foo() {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public class Inner extends E {\n" //
				+ "        public void test() {\n" //
				+ "            E.this.foo();\n" //
				+ "            foo();\n" //
				+ "            foo();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void foo() {}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveQualifierBug150481_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public class Inner {\n" //
				+ "        public void test() {\n" //
				+ "            E.this.foo();\n" //
				+ "            foo();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void foo() {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_METHOD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public class Inner {\n" //
				+ "        public void test() {\n" //
				+ "            foo();\n" //
				+ "            foo();\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "    public void foo() {}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveQualifierBug219478() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 extends E2 {\n" //
				+ "    private class E1Inner extends E2 {\n" //
				+ "        public E1Inner() {\n" //
				+ "            i = 2;\n" //
				+ "            System.out.println(i + E1.this.i);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "class E2 {\n" //
				+ "    protected int i = 1;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 extends E2 {\n" //
				+ "    private class E1Inner extends E2 {\n" //
				+ "        public E1Inner() {\n" //
				+ "            i = 2;\n" //
				+ "            System.out.println(i + E1.this.i);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "class E2 {\n" //
				+ "    protected int i = 1;\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveQualifierBug219608() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 extends E2 {\n" //
				+ "    private int i = 1;\n" //
				+ "    private class E1Inner extends E2 {\n" //
				+ "        public E1Inner() {\n" //
				+ "            System.out.println(i + E1.this.i);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "class E2 {\n" //
				+ "    private int i = 1;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_IF_NECESSARY);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 extends E2 {\n" //
				+ "    private int i = 1;\n" //
				+ "    private class E1Inner extends E2 {\n" //
				+ "        public E1Inner() {\n" //
				+ "            System.out.println(i + i);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "class E2 {\n" //
				+ "    private int i = 1;\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testRemoveQualifierBug330754() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class Test {\n" //
				+ "    String label = \"works\";\n" //
				+ "    class Nested extends Test {\n" //
				+ "        Nested() {\n" //
				+ "            label = \"broken\";\n" //
				+ "        }\n" //
				+ "        @Override\n" //
				+ "        public String toString() {\n" //
				+ "            return Test.this.label;\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("Test.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_IF_NECESSARY);

		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testAddFinal01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private int i= 0;\n" //
				+ "    public void foo(int j, int k) {\n" //
				+ "        int h, v;\n" //
				+ "        v= 0;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PARAMETERS);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private final int i= 0;\n" //
				+ "    public void foo(final int j, final int k) {\n" //
				+ "        final int h;\n" //
				+ "        int v;\n" //
				+ "        v= 0;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(FixMessages.VariableDeclarationFix_changeModifierOfUnknownToFinal_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testAddFinal02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private Object obj1= new Object();\n" //
				+ "    protected Object obj2;\n" //
				+ "    Object obj3;\n" //
				+ "    public Object obj4;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private final Object obj1= new Object();\n" //
				+ "    protected Object obj2;\n" //
				+ "    Object obj3;\n" //
				+ "    public Object obj4;\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testAddFinal03() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private int i = 0;\n" //
				+ "    public void foo() throws Exception {\n" //
				+ "    }\n" //
				+ "    public void bar(int j) {\n" //
				+ "        int k;\n" //
				+ "        try {\n" //
				+ "            foo();\n" //
				+ "        } catch (Exception e) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private int i = 0;\n" //
				+ "    public void foo() throws Exception {\n" //
				+ "    }\n" //
				+ "    public void bar(int j) {\n" //
				+ "        final int k;\n" //
				+ "        try {\n" //
				+ "            foo();\n" //
				+ "        } catch (final Exception e) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testAddFinal04() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private int i = 0;\n" //
				+ "    public void foo() throws Exception {\n" //
				+ "    }\n" //
				+ "    public void bar(int j) {\n" //
				+ "        int k;\n" //
				+ "        try {\n" //
				+ "            foo();\n" //
				+ "        } catch (Exception e) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PARAMETERS);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    private int i = 0;\n" //
				+ "    public void foo() throws Exception {\n" //
				+ "    }\n" //
				+ "    public void bar(final int j) {\n" //
				+ "        int k;\n" //
				+ "        try {\n" //
				+ "            foo();\n" //
				+ "        } catch (Exception e) {\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testAddFinal05() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        int i= 0;\n" //
				+ "        if (i > 1 || i == 1 && i > 1)\n" //
				+ "            ;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES);
		enable(CleanUpConstants.EXPRESSIONS_USE_PARENTHESES_ALWAYS);

		sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    public void foo() {\n" //
				+ "        final int i= 0;\n" //
				+ "        if ((i > 1) || ((i == 1) && (i > 1)))\n" //
				+ "            ;\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testAddFinalBug129807() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public abstract class E {\n" //
				+ "    public interface I {\n" //
				+ "        void foo(int i);\n" //
				+ "    }\n" //
				+ "    public class IImpl implements I {\n" //
				+ "        public void foo(int i) {}\n" //
				+ "    }\n" //
				+ "    public abstract void bar(int i, String s);\n" //
				+ "    public void foobar(int i, int j) {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PARAMETERS);

		sample= "" //
				+ "package test;\n" //
				+ "public abstract class E {\n" //
				+ "    public interface I {\n" //
				+ "        void foo(int i);\n" //
				+ "    }\n" //
				+ "    public class IImpl implements I {\n" //
				+ "        public void foo(final int i) {}\n" //
				+ "    }\n" //
				+ "    public abstract void bar(int i, String s);\n" //
				+ "    public void foobar(final int i, final int j) {}\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testAddFinalBug134676_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E<T> { \n" //
				+ "    private String s;\n" //
				+ "    void setS(String s) {\n" //
				+ "        this.s = s;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testAddFinalBug134676_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E<T> { \n" //
				+ "    private String s= \"\";\n" //
				+ "    private T t;\n" //
				+ "    private T t2;\n" //
				+ "    public E(T t) {t2= t;}\n" //
				+ "    void setT(T t) {\n" //
				+ "        this.t = t;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		sample= "" //
				+ "package test;\n" //
				+ "public class E<T> { \n" //
				+ "    private final String s= \"\";\n" //
				+ "    private T t;\n" //
				+ "    private final T t2;\n" //
				+ "    public E(T t) {t2= t;}\n" //
				+ "    void setT(T t) {\n" //
				+ "        this.t = t;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	//Changed test due to https://bugs.eclipse.org/bugs/show_bug.cgi?id=220124
	@Test
	public void testAddFinalBug145028() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private volatile int field= 0;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	//https://bugs.eclipse.org/bugs/show_bug.cgi?id=294768
	@Test
	public void testAddFinalBug294768() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private transient int field= 0;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testAddFinalBug157276_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field;\n" //
				+ "    public E1() {\n" //
				+ "        field= 10;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private final int field;\n" //
				+ "    public E1() {\n" //
				+ "        field= 10;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testAddFinalBug157276_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field;\n" //
				+ "    public E1() {\n" //
				+ "        field= 10;\n" //
				+ "    }\n" //
				+ "    public E1(int f) {\n" //
				+ "        field= f;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private final int field;\n" //
				+ "    public E1() {\n" //
				+ "        field= 10;\n" //
				+ "    }\n" //
				+ "    public E1(int f) {\n" //
				+ "        field= f;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testAddFinalBug157276_3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field;\n" //
				+ "    public E1() {\n" //
				+ "        field= 10;\n" //
				+ "    }\n" //
				+ "    public E1(final int f) {\n" //
				+ "        field= f;\n" //
				+ "        field= 5;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testAddFinalBug157276_4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field;\n" //
				+ "    public E1() {\n" //
				+ "    }\n" //
				+ "    public E1(final int f) {\n" //
				+ "        field= f;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testAddFinalBug157276_5() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field= 0;\n" //
				+ "    public E1() {\n" //
				+ "        field= 5;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testAddFinalBug157276_6() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field;\n" //
				+ "    public E1() {\n" //
				+ "        if (false) field= 5;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testAddFinalBug157276_7() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private static int field;\n" //
				+ "    public E1() {\n" //
				+ "        field= 5;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testAddFinalBug156842() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int f0;\n" //
				+ "    private int f1= 0;\n" //
				+ "    private int f3;\n" //
				+ "    public E1() {\n" //
				+ "        f3= 0;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int f0;\n" //
				+ "    private final int f1= 0;\n" //
				+ "    private final int f3;\n" //
				+ "    public E1() {\n" //
				+ "        f3= 0;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testAddFinalBug158041_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(int[] ints) {\n" //
				+ "        for (int j = 0; j < ints.length; j++) {\n" //
				+ "            System.out.println(ints[j]);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);
		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(int[] ints) {\n" //
				+ "        for (final int i : ints) {\n" //
				+ "            System.out.println(i);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testAddFinalBug158041_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(int[] ints) {\n" //
				+ "        for (int j = 0; j < ints.length; j++) {\n" //
				+ "            int i = ints[j];\n" //
				+ "            System.out.println(i);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);
		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(int[] ints) {\n" //
				+ "        for (final int i : ints) {\n" //
				+ "            System.out.println(i);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testAddFinalBug158041_3() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import java.util.Iterator;\n" //
				+ "import java.util.List;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(List<E1> es) {\n" //
				+ "        for (Iterator<E1> iterator = es.iterator(); iterator.hasNext();) {\n" //
				+ "            System.out.println(iterator.next());\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);
		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);

		sample= "" //
				+ "package test1;\n" //
				+ "import java.util.List;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(List<E1> es) {\n" //
				+ "        for (final E1 e1 : es) {\n" //
				+ "            System.out.println(e1);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testAddFinalBug158041_4() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "import java.util.Iterator;\n" //
				+ "import java.util.List;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(List<E1> es) {\n" //
				+ "        for (Iterator<E1> iterator = es.iterator(); iterator.hasNext();) {\n" //
				+ "            E1 e1 = iterator.next();\n" //
				+ "            System.out.println(e1);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);
		enable(CleanUpConstants.CONTROL_STATEMENTS_CONVERT_FOR_LOOP_TO_ENHANCED);

		sample= "" //
				+ "package test1;\n" //
				+ "import java.util.List;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo(List<E1> es) {\n" //
				+ "        for (final E1 e1 : es) {\n" //
				+ "            System.out.println(e1);\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testAddFinalBug163789() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i;\n" //
				+ "    public E1() {\n" //
				+ "        this(10);\n" //
				+ "        i = 10;\n" //
				+ "    }\n" //
				+ "    public E1(int j) {\n" //
				+ "        i = j;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PARAMETERS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int i;\n" //
				+ "    public E1() {\n" //
				+ "        this(10);\n" //
				+ "        i = 10;\n" //
				+ "    }\n" //
				+ "    public E1(final int j) {\n" //
				+ "        i = j;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testAddFinalBug191862() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E01 {\n" //
				+ "    @SuppressWarnings(\"unused\")\n" //
				+ "    @Deprecated\n" //
				+ "    private int x = 5, y= 10;\n" //
				+ "    \n" //
				+ "    private void foo() {\n" //
				+ "        @SuppressWarnings(\"unused\")\n" //
				+ "        @Deprecated\n" //
				+ "        int i= 10, j;\n" //
				+ "        j= 10;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_LOCAL_VARIABLES);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E01 {\n" //
				+ "    @SuppressWarnings(\"unused\")\n" //
				+ "    @Deprecated\n" //
				+ "    private final int x = 5, y= 10;\n" //
				+ "    \n" //
				+ "    private void foo() {\n" //
				+ "        @SuppressWarnings(\"unused\")\n" //
				+ "        @Deprecated\n" //
				+ "        final\n" //
				+ "        int i= 10;\n" //
				+ "        @SuppressWarnings(\"unused\")\n" //
				+ "        @Deprecated\n" //
				+ "        int j;\n" //
				+ "        j= 10;\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {sample});
	}

	@Test
	public void testAddFinalBug213995() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private Object foo = new Object() {\n" //
				+ "        public boolean equals(Object obj) {\n" //
				+ "            return super.equals(obj);\n" //
				+ "        }\n" //
				+ "    }; \n" //
				+ "    public void foo() {\n" //
				+ "        Object foo = new Object() {\n" //
				+ "            public boolean equals(Object obj) {\n" //
				+ "                return super.equals(obj);\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PARAMETERS);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private Object foo = new Object() {\n" //
				+ "        public boolean equals(final Object obj) {\n" //
				+ "            return super.equals(obj);\n" //
				+ "        }\n" //
				+ "    }; \n" //
				+ "    public void foo() {\n" //
				+ "        Object foo = new Object() {\n" //
				+ "            public boolean equals(final Object obj) {\n" //
				+ "                return super.equals(obj);\n" //
				+ "            }\n" //
				+ "        };\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testAddFinalBug272532() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int field;\n" //
				+ "    public E1() {\n" //
				+ "        if (true)\n" //
				+ "            return;\n" //
				+ "        field= 5;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChange(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testAddFinalBug297566() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    private int x;\n" //
				+ "    public E1() {\n" //
				+ "        this();\n" //
				+ "    }\n" //
				+ "    public E1(int a) {\n" //
				+ "        x = a;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChangeEventWithError(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testAddFinalBug297566_2() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    private int x;\n" //
				+ "\n" //
				+ "    public E1() {\n" //
				+ "        this(10);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public E1(int a) {\n" //
				+ "        this();\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public E1(int f, int y) {\n" //
				+ "        x = a;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL);
		enable(CleanUpConstants.VARIABLE_DECLARATIONS_USE_FINAL_PRIVATE_FIELDS);

		assertRefactoringHasNoChangeEventWithError(new ICompilationUnit[] {cu1});
	}

	@Test
	public void testRemoveStringCreation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public String replaceNewString() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return new String(\"\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String replaceNewStringInMethodInvocation(String s, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return new String(s + i).toLowerCase();\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.NO_STRING_CREATION);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public String replaceNewString() {\n" //
				+ "        // Keep this comment\n" //
				+ "        return \"\";\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public String replaceNewStringInMethodInvocation(String s, int i) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return (s + i).toLowerCase();\n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { sample });
	}

	@Test
	public void testDoNotRemoveStringCreation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    public String doNotReplaceNullableString(String s) {\n" //
				+ "        return new String(s);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.NO_STRING_CREATION);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testCheckSignOfBitwiseOperation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "public class Foo {\n" //
				+ "  private static final int CONSTANT = -1;\n" //
				+ "\n" //
				+ "  public int foo () {\n" //
				+ "    int i = 0;\n" //
				+ "    if (i & (CONSTANT | C2) > 0) {}\n" //
				+ "    if (0 < (i & (CONSTANT | C2))) {}\n" //
				+ "    return (1>>4 & CONSTANT) > 0;\n" //
				+ "  };\n" //
				+ "};\n";
		ICompilationUnit cu= pack1.createCompilationUnit("Foo.java", sample, false, null);
		sample= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "public class Foo {\n" //
				+ "  private static final int CONSTANT = -1;\n" //
				+ "\n" //
				+ "  public int foo () {\n" //
				+ "    int i = 0;\n" //
				+ "    if (i & (CONSTANT | C2) != 0) {}\n" //
				+ "    if (0 != (i & (CONSTANT | C2))) {}\n" //
				+ "    return (1>>4 & CONSTANT) != 0;\n" //
				+ "  };\n" //
				+ "};\n";
		String expected = sample;

		enable(CleanUpConstants.CHECK_SIGN_OF_BITWISE_OPERATION);

		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.CheckSignOfBitwiseOperation_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testKeepCheckSignOfBitwiseOperation() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "public class Foo {\n" //
				+ "  private static final int CONSTANT = -1;\n" //
				+ "\n" //
				+ "  public void bar() {\n" //
				+ "    int i = 0;\n" //
				+ "    if (i > 0) {}\n" //
				+ "    if (i > 0 && (CONSTANT +1) > 0) {}\n" //
				+ "  };\n" //
				+ "};\n";
		String original= sample;
		ICompilationUnit cu= pack1.createCompilationUnit("Foo.java", original, false, null);

		enable(CleanUpConstants.CHECK_SIGN_OF_BITWISE_OPERATION);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testInvertEquals() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static interface Itf {\n" //
				+ "        int primitiveConstant = 1;\n" //
				+ "        String objConstant = \"fkjfkjf\";\n" //
				+ "        String objNullConstant = null;\n" //
				+ "        MyEnum enumConstant = MyEnum.NOT_NULL;\n" //
				+ "        MyEnum enumNullConstant = null;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static enum MyEnum {\n" //
				+ "        NOT_NULL\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean invertEquals(Object obj, String text1, String text2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return obj.equals(\"\")\n" //
				+ "                && obj.equals(Itf.objConstant)\n" //
				+ "                && obj.equals(\"\" + Itf.objConstant)\n" //
				+ "                && obj.equals(MyEnum.NOT_NULL)\n" //
				+ "                && obj.equals(text1 + text2)\n" //
				+ "                && obj.equals(this);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean invertEqualsIgnoreCase(String s) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return s.equalsIgnoreCase(\"\")\n" //
				+ "                && s.equalsIgnoreCase(Itf.objConstant)\n" //
				+ "                && s.equalsIgnoreCase(\"\" + Itf.objConstant);\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static interface Itf {\n" //
				+ "        int primitiveConstant = 1;\n" //
				+ "        String objConstant = \"fkjfkjf\";\n" //
				+ "        String objNullConstant = null;\n" //
				+ "        MyEnum enumConstant = MyEnum.NOT_NULL;\n" //
				+ "        MyEnum enumNullConstant = null;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static enum MyEnum {\n" //
				+ "        NOT_NULL\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean invertEquals(Object obj, String text1, String text2) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return \"\".equals(obj)\n" //
				+ "                && Itf.objConstant.equals(obj)\n" //
				+ "                && (\"\" + Itf.objConstant).equals(obj)\n" //
				+ "                && MyEnum.NOT_NULL.equals(obj)\n" //
				+ "                && (text1 + text2).equals(obj)\n" //
				+ "                && this.equals(obj);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean invertEqualsIgnoreCase(String s) {\n" //
				+ "        // Keep this comment\n" //
				+ "        return \"\".equalsIgnoreCase(s)\n" //
				+ "                && Itf.objConstant.equalsIgnoreCase(s)\n" //
				+ "                && (\"\" + Itf.objConstant).equalsIgnoreCase(s);\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.INVERT_EQUALS);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.InvertEqualsCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotInvertEquals() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public static interface Itf {\n" //
				+ "        int primitiveConstant = 1;\n" //
				+ "        String objConstant = \"fkjfkjf\";\n" //
				+ "        String objNullConstant = null;\n" //
				+ "        MyEnum enumConstant = MyEnum.NOT_NULL;\n" //
				+ "        MyEnum enumNullConstant = null;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private static enum MyEnum {\n" //
				+ "        NOT_NULL\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    private int primitiveField;\n" //
				+ "\n" //
				+ "    public boolean doNotInvertEqualsOnInstance() {\n" //
				+ "        return equals(\"\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotInvertEqualsOnThis() {\n" //
				+ "        return this.equals(\"\");\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotInvertEqualsWhenParameterIsNull(Object obj) {\n" //
				+ "        return obj.equals(Itf.objNullConstant) && obj.equals(Itf.enumNullConstant);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotInvertEqualsWithPrimitiveParameter(Object obj) {\n" //
				+ "        return obj.equals(1)\n" //
				+ "            && obj.equals(Itf.primitiveConstant)\n" //
				+ "            && obj.equals(primitiveField);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotInvertEqualsIgnoreCaseWhenParameterIsNull(String s) {\n" //
				+ "        return s.equalsIgnoreCase(Itf.objNullConstant);\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotInvertEqualsOnOperationThatIsNotConcatenation(Integer number, Integer i1, Integer i2) {\n" //
				+ "        return number.equals(i1 + i2);\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.INVERT_EQUALS);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testStandardComparison() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String given= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Comparator;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public boolean refactorComparableComparingToZero() {\n" //
				+ "        boolean b = true;\n" //
				+ "        final String s = \"\";\n" //
				+ "\n" //
				+ "        b &= s.compareTo(\"smaller\") == -1;\n" //
				+ "        b &= s.compareTo(\"greater\") != -1;\n" //
				+ "        b &= s.compareTo(\"smaller\") != 1;\n" //
				+ "        b &= (s.compareTo(\"greater\")) == 1;\n" //
				+ "        b &= (s.compareToIgnoreCase(\"greater\")) == 1;\n" //
				+ "        b &= -1 == (s.compareTo(\"smaller\"));\n" //
				+ "        b &= -1 != s.compareTo(\"greater\");\n" //
				+ "        b &= 1 != s.compareTo(\"smaller\");\n" //
				+ "        b &= 1 == s.compareTo(\"greater\");\n" //
				+ "        b &= 1 == s.compareToIgnoreCase(\"greater\");\n" //
				+ "\n" //
				+ "        return b;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean refactorComparatorComparingToZero(Comparator<String> comparator) {\n" //
				+ "        boolean b = true;\n" //
				+ "        final String s = \"\";\n" //
				+ "\n" //
				+ "        b &= comparator.compare(s, \"smaller\") == -1;\n" //
				+ "        b &= comparator.compare(s, \"greater\") != -1;\n" //
				+ "        b &= comparator.compare(s, \"smaller\") != 1;\n" //
				+ "        b &= (comparator.compare(s, \"greater\")) == 1;\n" //
				+ "        b &= -1 == (comparator.compare(s, \"smaller\"));\n" //
				+ "        b &= -1 != comparator.compare(s, \"greater\");\n" //
				+ "        b &= 1 != comparator.compare(s, \"smaller\");\n" //
				+ "        b &= 1 == comparator.compare(s, \"greater\");\n" //
				+ "\n" //
				+ "        return b;\n" //
				+ "    }\n" //
				+ "}\n";

		String expected= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Comparator;\n" //
				+ "\n" //
				+ "public class E {\n" //
				+ "    public boolean refactorComparableComparingToZero() {\n" //
				+ "        boolean b = true;\n" //
				+ "        final String s = \"\";\n" //
				+ "\n" //
				+ "        b &= s.compareTo(\"smaller\") < 0;\n" //
				+ "        b &= s.compareTo(\"greater\") >= 0;\n" //
				+ "        b &= s.compareTo(\"smaller\") <= 0;\n" //
				+ "        b &= s.compareTo(\"greater\") > 0;\n" //
				+ "        b &= s.compareToIgnoreCase(\"greater\") > 0;\n" //
				+ "        b &= s.compareTo(\"smaller\") < 0;\n" //
				+ "        b &= s.compareTo(\"greater\") >= 0;\n" //
				+ "        b &= s.compareTo(\"smaller\") <= 0;\n" //
				+ "        b &= s.compareTo(\"greater\") > 0;\n" //
				+ "        b &= s.compareToIgnoreCase(\"greater\") > 0;\n" //
				+ "\n" //
				+ "        return b;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean refactorComparatorComparingToZero(Comparator<String> comparator) {\n" //
				+ "        boolean b = true;\n" //
				+ "        final String s = \"\";\n" //
				+ "\n" //
				+ "        b &= comparator.compare(s, \"smaller\") < 0;\n" //
				+ "        b &= comparator.compare(s, \"greater\") >= 0;\n" //
				+ "        b &= comparator.compare(s, \"smaller\") <= 0;\n" //
				+ "        b &= comparator.compare(s, \"greater\") > 0;\n" //
				+ "        b &= comparator.compare(s, \"smaller\") < 0;\n" //
				+ "        b &= comparator.compare(s, \"greater\") >= 0;\n" //
				+ "        b &= comparator.compare(s, \"smaller\") <= 0;\n" //
				+ "        b &= comparator.compare(s, \"greater\") > 0;\n" //
				+ "\n" //
				+ "        return b;\n" //
				+ "    }\n" //
				+ "}\n";

		// When
		ICompilationUnit cu= pack.createCompilationUnit("E.java", given, false, null);
		enable(CleanUpConstants.STANDARD_COMPARISON);

		// Then
		assertNotEquals("The class must be changed", given, expected);
		assertGroupCategoryUsed(new ICompilationUnit[] { cu }, new HashSet<>(Arrays.asList(MultiFixMessages.StandardComparisonCleanUp_description)));
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu }, new String[] { expected });
	}

	@Test
	public void testDoNotUseStandardComparison() throws Exception {
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.Comparator;\n" //
				+ "\n" //
				+ "public class E implements Comparator<Double> {\n" //
				+ "    public boolean doNotRefactorValidCases() {\n" //
				+ "        boolean b = true;\n" //
				+ "        final String s = \"\";\n" //
				+ "\n" //
				+ "        b &= s.compareTo(\"smaller\") < 0;\n" //
				+ "        b &= s.compareTo(\"smaller\") <= 0;\n" //
				+ "        b &= s.compareTo(\"equal\") == 0;\n" //
				+ "        b &= s.compareTo(\"different\") != 0;\n" //
				+ "        b &= s.compareTo(\"greater\") >= 0;\n" //
				+ "        b &= s.compareTo(\"greater\") > 0;\n" //
				+ "        b &= s.compareToIgnoreCase(\"equal\") == 0;\n" //
				+ "\n" //
				+ "        return b;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotRefactorValidCases(Comparator<String> comparator) {\n" //
				+ "        boolean b = true;\n" //
				+ "        final String s = \"\";\n" //
				+ "\n" //
				+ "        b &= comparator.compare(s, \"smaller\") < 0;\n" //
				+ "        b &= comparator.compare(s, \"smaller\") <= 0;\n" //
				+ "        b &= comparator.compare(s, \"equal\") == 0;\n" //
				+ "        b &= comparator.compare(s, \"different\") != 0;\n" //
				+ "        b &= comparator.compare(s, \"greater\") >= 0;\n" //
				+ "        b &= comparator.compare(s, \"greater\") > 0;\n" //
				+ "\n" //
				+ "        return b;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    public boolean doNotRefactorLocalComparingToZero() {\n" //
				+ "        boolean b = true;\n" //
				+ "        final Double s = 123d;\n" //
				+ "\n" //
				+ "        b &= compare(s, 100d) < 100;\n" //
				+ "        b &= compare(s, 100d) <= 100;\n" //
				+ "        b &= compare(s, 123d) == 100;\n" //
				+ "        b &= compare(s, 321d) != 100;\n" //
				+ "        b &= compare(s, 200d) >= 100;\n" //
				+ "        b &= compare(s, 200d) > 100;\n" //
				+ "\n" //
				+ "        b &= compare(s, 100d) == 99;\n" //
				+ "        b &= compare(s, 200d) != 99;\n" //
				+ "        b &= compare(s, 100d) != 101;\n" //
				+ "        b &= (compare(s, 200d)) == 101;\n" //
				+ "        b &= 99 == (compare(s, 100d));\n" //
				+ "        b &= 99 != compare(s, 200d);\n" //
				+ "        b &= 101 != compare(s, 100d);\n" //
				+ "        b &= 101 == compare(s, 200d);\n" //
				+ "\n" //
				+ "        return b;\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    @Override\n" //
				+ "    public int compare(Double o1, Double o2) {\n" //
				+ "        return Double.compare(o1, o2) + 100;\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu= pack.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.STANDARD_COMPARISON);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testRemoveBlockReturnThrows01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public abstract class E {\n" //
				+ "    public void foo(Object obj) {\n" //
				+ "        if (obj == null) {\n" //
				+ "            throw new IllegalArgumentException();\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        if (obj.hashCode() > 0) {\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        if (obj.hashCode() < 0) {\n" //
				+ "            System.out.println(\"\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        if (obj.toString() != null) {\n" //
				+ "            System.out.println(obj.toString());\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS);
		enable(CleanUpConstants.CONTROL_STATEMENTS_USE_BLOCKS_NO_FOR_RETURN_AND_THROW);

		sample= "" //
				+ "package test;\n" //
				+ "public abstract class E {\n" //
				+ "    public void foo(Object obj) {\n" //
				+ "        if (obj == null)\n" //
				+ "            throw new IllegalArgumentException();\n" //
				+ "        \n" //
				+ "        if (obj.hashCode() > 0)\n" //
				+ "            return;\n" //
				+ "        \n" //
				+ "        if (obj.hashCode() < 0) {\n" //
				+ "            System.out.println(\"\");\n" //
				+ "            return;\n" //
				+ "        }\n" //
				+ "        \n" //
				+ "        if (obj.toString() != null) {\n" //
				+ "            System.out.println(obj.toString());\n" //
				+ "        } else {\n" //
				+ "            System.out.println(\"\");\n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveTrailingWhitespace01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package    test1;     \n" //
				+ "   public class E1 {  \n" //
				+ "                   \n" //
				+ "}                  \n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES);
		enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES_ALL);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package    test1;\n" //
				+ "public class E1 {\n" //
				+ "\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveTrailingWhitespace02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package    test1;     \n" //
				+ "   public class E1 {  \n" //
				+ "                   \n" //
				+ "}                  \n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES);
		enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES_IGNORE_EMPTY);

		sample= "" //
				+ "package    test1;\n" //
				+ "   public class E1 {\n" //
				+ "                   \n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testRemoveTrailingWhitespaceBug173081() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "/**\n" //
				+ " * \n" //
				+ " *     \n" //
				+ " */\n" //
				+ "public class E1 { \n" //
				+ "    /**\n" //
				+ "     * \n" //
				+ "	 *     \n" //
				+ "     */\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES);
		enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES_IGNORE_EMPTY);
		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "package test1;\n" //
				+ "/**\n" //
				+ " * \n" //
				+ " * \n" //
				+ " */\n" //
				+ "public class E1 {\n" //
				+ "    /**\n" //
				+ "     * \n" //
				+ "     * \n" //
				+ "     */\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testSortMembers01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "   public class SM01 {\n" //
				+ "   int b;\n" //
				+ "   int a;\n" //
				+ "   void d() {};\n" //
				+ "   void c() {};\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("SM01.java", sample, false, null);

		enable(CleanUpConstants.SORT_MEMBERS);
		enable(CleanUpConstants.SORT_MEMBERS_ALL);

		sample= "" //
				+ "package test;\n" //
				+ "   public class SM01 {\n" //
				+ "   int a;\n" //
				+ "   int b;\n" //
				+ "   void c() {};\n" //
				+ "   void d() {};\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testSortMembers02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "   public class SM02 {\n" //
				+ "   int b;\n" //
				+ "   int a;\n" //
				+ "   void d() {};\n" //
				+ "   void c() {};\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("SM02.java", sample, false, null);

		enable(CleanUpConstants.SORT_MEMBERS);

		sample= "" //
				+ "package test;\n" //
				+ "   public class SM02 {\n" //
				+ "   int b;\n" //
				+ "   int a;\n" //
				+ "   void c() {};\n" //
				+ "   void d() {};\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testSortMembersBug218542() throws Exception {
		JavaPlugin.getDefault().getPreferenceStore().setValue(PreferenceConstants.APPEARANCE_ENABLE_VISIBILITY_SORT_ORDER, true);
		assertTrue(JavaPlugin.getDefault().getMemberOrderPreferenceCache().isSortByVisibility());

		try {
			IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
			String sample= "" //
					+ "package test;\n" //
					+ "   public class SM02 {\n" //
					+ "   private int b;\n" //
					+ "   public int a;\n" //
					+ "   void d() {};\n" //
					+ "   void c() {};\n" //
					+ "}\n";
			ICompilationUnit cu1= pack1.createCompilationUnit("SM02.java", sample, false, null);

			enable(CleanUpConstants.SORT_MEMBERS);

			sample= "" //
					+ "package test;\n" //
					+ "   public class SM02 {\n" //
					+ "   private int b;\n" //
					+ "   public int a;\n" //
					+ "   void c() {};\n" //
					+ "   void d() {};\n" //
					+ "}\n";
			String expected1= sample;

			assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
		} finally {
			JavaPlugin.getDefault().getPreferenceStore().setValue(PreferenceConstants.APPEARANCE_ENABLE_VISIBILITY_SORT_ORDER, false);
		}
	}

	@Test
	public void testSortMembersBug223997() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class SM02 {\n" //
				+ "    public String s2;\n" //
				+ "    public static String s1;\n" //
				+ "   void d() {};\n" //
				+ "   void c() {};\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("SM02.java", sample, false, null);

		enable(CleanUpConstants.SORT_MEMBERS);

		sample= "" //
				+ "package test;\n" //
				+ "public class SM02 {\n" //
				+ "    public static String s1;\n" //
				+ "    public String s2;\n" //
				+ "   void c() {};\n" //
				+ "   void d() {};\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testSortMembersBug263173() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class SM263173 {\n" //
				+ "    static int someInt;\n" //
				+ "    static {\n" //
				+ "        someInt = 1;\n" //
				+ "    };\n" //
				+ "    static int anotherInt = someInt;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("SM263173.java", sample, false, null);

		enable(CleanUpConstants.SORT_MEMBERS);

		sample= "" //
				+ "package test;\n" //
				+ "public class SM263173 {\n" //
				+ "    static int someInt;\n" //
				+ "    static {\n" //
				+ "        someInt = 1;\n" //
				+ "    };\n" //
				+ "    static int anotherInt = someInt;\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testSortMembersBug434941() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class A {\n" //
				+ "    public static final int CONSTANT = 5;\n" //
				+ "    public static void main(final String[] args) { }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("A.java", sample, false, null);

		enable(CleanUpConstants.SORT_MEMBERS);
		enable(CleanUpConstants.SORT_MEMBERS_ALL);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });

		enable(CleanUpConstants.SORT_MEMBERS);
		disable(CleanUpConstants.SORT_MEMBERS_ALL);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });
	}

	@Test
	public void testSortMembersMixedFields() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class A {\n" //
				+ "    public static final int B = 1;\n" //
				+ "    public final int A = 2;\n" //
				+ "    public static final int C = 3;\n" //
				+ "    public final int D = 4;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("A.java", sample, false, null);

		enable(CleanUpConstants.SORT_MEMBERS);
		disable(CleanUpConstants.SORT_MEMBERS_ALL);

		sample= "" //
				+ "package test;\n" //
				+ "public class A {\n" //
				+ "    public static final int B = 1;\n" //
				+ "    public static final int C = 3;\n" //
				+ "    public final int A = 2;\n" //
				+ "    public final int D = 4;\n" //
				+ "}\n";

		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testSortMembersMixedFieldsInterface() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public interface A {\n" //
				+ "    public static final int B = 1;\n" //
				+ "    public final int A = 2;\n" //
				+ "    public static final int C = 3;\n" //
				+ "    public final int D = 4;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("A.java", sample, false, null);

		enable(CleanUpConstants.SORT_MEMBERS);
		disable(CleanUpConstants.SORT_MEMBERS_ALL);

		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });

		enable(CleanUpConstants.SORT_MEMBERS_ALL);

		sample= "" //
				+ "package test;\n" //
				+ "public interface A {\n" //
				+ "    public final int A = 2;\n" //
				+ "    public static final int B = 1;\n" //
				+ "    public static final int C = 3;\n" //
				+ "    public final int D = 4;\n" //
				+ "}\n";

		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testSortMembersBug407759() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class A {\n" //
				+ "    void foo2() {}\n" //
				+ "    void foo1() {}\n" //
				+ "    static int someInt;\n" //
				+ "    static void fooStatic() {}\n" //
				+ "    static {\n" //
				+ "    	someInt = 1;\n" //
				+ "    }\n" //
				+ "    void foo3() {}\n" //
				+ "    static int anotherInt = someInt;\n" //
				+ "    void foo() {}\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("A.java", sample, false, null);

		enable(CleanUpConstants.SORT_MEMBERS);
		disable(CleanUpConstants.SORT_MEMBERS_ALL);

		sample= "" //
				+ "package test;\n" //
				+ "public class A {\n" //
				+ "    static int someInt;\n" //
				+ "    static {\n" //
				+ "    	someInt = 1;\n" //
				+ "    }\n" //
				+ "    static int anotherInt = someInt;\n" //
				+ "    static void fooStatic() {}\n" //
				+ "    void foo() {}\n" //
				+ "    void foo1() {}\n" //
				+ "    void foo2() {}\n" //
				+ "    void foo3() {}\n" //
				+ "}\n";

		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });

		enable(CleanUpConstants.SORT_MEMBERS);
		enable(CleanUpConstants.SORT_MEMBERS_ALL);

		sample= "" //
				+ "package test;\n" //
				+ "public class A {\n" //
				+ "    static int anotherInt = someInt;\n" //
				+ "    static int someInt;\n" //
				+ "    static {\n" //
				+ "    	someInt = 1;\n" //
				+ "    }\n" //
				+ "    static void fooStatic() {}\n" //
				+ "    void foo() {}\n" //
				+ "    void foo1() {}\n" //
				+ "    void foo2() {}\n" //
				+ "    void foo3() {}\n" //
				+ "}\n";

		expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testSortMembersVisibility() throws Exception {
		JavaPlugin.getDefault().getPreferenceStore().setValue(PreferenceConstants.APPEARANCE_ENABLE_VISIBILITY_SORT_ORDER, true);
		JavaPlugin.getDefault().getPreferenceStore().setToDefault(PreferenceConstants.APPEARANCE_VISIBILITY_SORT_ORDER);

		try {
			IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
			String sample= "" //
					+ "package test;\n" //
					+ "public class A {\n" //
					+ "    public final int B = 1;\n" //
					+ "    private static final int AA = 1;\n" //
					+ "    public static final int BB = 2;\n" //
					+ "    private final int A = 2;\n" //
					+ "    final int C = 3;\n" //
					+ "    protected static final int DD = 3;\n" //
					+ "    final static int CC = 4;\n" //
					+ "    protected final int D = 4;\n" //
					+ "}\n";
			ICompilationUnit cu1= pack1.createCompilationUnit("A.java", sample, false, null);

			enable(CleanUpConstants.SORT_MEMBERS);
			disable(CleanUpConstants.SORT_MEMBERS_ALL);

			sample= "" //
					+ "package test;\n" //
					+ "public class A {\n" //
					+ "    private static final int AA = 1;\n" //
					+ "    public static final int BB = 2;\n" //
					+ "    protected static final int DD = 3;\n" //
					+ "    final static int CC = 4;\n" //
					+ "    public final int B = 1;\n" //
					+ "    private final int A = 2;\n" //
					+ "    final int C = 3;\n" //
					+ "    protected final int D = 4;\n" //
					+ "}\n";

			String expected1= sample;

			assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });

			enable(CleanUpConstants.SORT_MEMBERS);
			enable(CleanUpConstants.SORT_MEMBERS_ALL);

			sample= "" //
					+ "package test;\n" //
					+ "public class A {\n" //
					+ "    public static final int BB = 2;\n" //
					+ "    private static final int AA = 1;\n" //
					+ "    protected static final int DD = 3;\n" //
					+ "    final static int CC = 4;\n" //
					+ "    public final int B = 1;\n" //
					+ "    private final int A = 2;\n" //
					+ "    protected final int D = 4;\n" //
					+ "    final int C = 3;\n" //
					+ "}\n";

			expected1= sample;

			assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
		} finally {
			JavaPlugin.getDefault().getPreferenceStore().setToDefault(PreferenceConstants.APPEARANCE_ENABLE_VISIBILITY_SORT_ORDER);
		}
	}

	@Test
	public void testOrganizeImports01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    A a;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test1", false, null);
		sample= "" //
				+ "package test1;\n" //
				+ "public class A {}\n";
		pack2.createCompilationUnit("A.java", sample, false, null);

		IPackageFragment pack3= fSourceFolder.createPackageFragment("test2", false, null);
		sample= "" //
				+ "package test2;\n" //
				+ "public class A {}\n";
		pack3.createCompilationUnit("A.java", sample, false, null);

		enable(CleanUpConstants.ORGANIZE_IMPORTS);

		RefactoringStatus status= assertRefactoringHasNoChangeEventWithError(new ICompilationUnit[] {cu1});
		RefactoringStatusEntry[] entries= status.getEntries();
		assertEquals(1, entries.length);
		String message= entries[0].getMessage();
		assertTrue(message, entries[0].isInfo());
		assertTrue(message, message.contains("ambiguous"));
	}

	@Test
	public void testOrganizeImports02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public class E {\n" //
				+ "    Vect or v;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.ORGANIZE_IMPORTS);

		RefactoringStatus status= assertRefactoringHasNoChangeEventWithError(new ICompilationUnit[] {cu1});
		RefactoringStatusEntry[] entries= status.getEntries();
		assertEquals(1, entries.length);
		String message= entries[0].getMessage();
		assertTrue(message, entries[0].isInfo());
		assertTrue(message, message.contains("parse"));
	}

	@Test
	public void testOrganizeImportsBug202266() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test2", false, null);
		String sample= "" //
				+ "package test2;\n" //
				+ "public class E2 {\n" //
				+ "}\n";
		pack1.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack2= fSourceFolder.createPackageFragment("test3", false, null);
		sample= "" //
				+ "package test3;\n" //
				+ "public class E2 {\n" //
				+ "}\n";
		pack2.createCompilationUnit("E2.java", sample, false, null);

		IPackageFragment pack3= fSourceFolder.createPackageFragment("test1", false, null);
		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    ArrayList foo;\n" //
				+ "    E2 foo2;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack3.createCompilationUnit("E.java", sample, false, null);

		enable(CleanUpConstants.ORGANIZE_IMPORTS);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "\n" //
				+ "public class E1 {\n" //
				+ "    ArrayList foo;\n" //
				+ "    E2 foo2;\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testOrganizeImportsBug229570() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public interface E1 {\n" //
				+ "  List<IEntity> getChildEntities();\n" //
				+ "  ArrayList<String> test;\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.ORGANIZE_IMPORTS);

		sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "import java.util.ArrayList;\n" //
				+ "import java.util.List;\n" //
				+ "\n" //
				+ "public interface E1 {\n" //
				+ "  List<IEntity> getChildEntities();\n" //
				+ "  ArrayList<String> test;\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCorrectIndetation01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "/**\n" //
				+ "* \n" //
				+ "*/\n" //
				+ "package test1;\n" //
				+ "/**\n" //
				+ " * \n" //
				+ "* \n" //
				+ " */\n" //
				+ "        public class E1 {\n" //
				+ "    /**\n" //
				+ "         * \n" //
				+ " * \n" //
				+ "     */\n" //
				+ "            public void foo() {\n" //
				+ "            //a\n" //
				+ "        //b\n" //
				+ "            }\n" //
				+ "    /*\n" //
				+ "     *\n" //
				+ "           *\n" //
				+ "* \n" //
				+ "     */\n" //
				+ "        }\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);

		sample= "" //
				+ "/**\n" //
				+ " * \n" //
				+ " */\n" //
				+ "package test1;\n" //
				+ "/**\n" //
				+ " * \n" //
				+ " * \n" //
				+ " */\n" //
				+ "public class E1 {\n" //
				+ "    /**\n" //
				+ "     * \n" //
				+ "     * \n" //
				+ "     */\n" //
				+ "    public void foo() {\n" //
				+ "        //a\n" //
				+ "        //b\n" //
				+ "    }\n" //
				+ "    /*\n" //
				+ "     *\n" //
				+ "     *\n" //
				+ "     * \n" //
				+ "     */\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}

	@Test
	public void testCorrectIndetationBug202145_1() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "//  \n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

		enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);
		enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES);
		enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES_ALL);

		sample= "" //
				+ "package test1;\n" //
				+ "public class E1 {\n" //
				+ "    public void foo() {\n" //
				+ "        //\n" //
				+ "    }\n" //
				+ "}\n";
		String expected1= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
	}

	@Test
	public void testCorrectIndetationBug202145_2() throws Exception {
		IJavaProject project= getProject();
		project.setOption(DefaultCodeFormatterConstants.FORMATTER_NEVER_INDENT_LINE_COMMENTS_ON_FIRST_COLUMN, DefaultCodeFormatterConstants.TRUE);
		try {
			IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
			String sample= "" //
					+ "package test1;\n" //
					+ "public class E1 {\n" //
					+ "    public void foo() {\n" //
					+ "//  \n" //
					+ "    }\n" //
					+ "}\n";
			ICompilationUnit cu1= pack1.createCompilationUnit("E1.java", sample, false, null);

			enable(CleanUpConstants.FORMAT_CORRECT_INDENTATION);
			enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES);
			enable(CleanUpConstants.FORMAT_REMOVE_TRAILING_WHITESPACES_ALL);

			sample= "" //
					+ "package test1;\n" //
					+ "public class E1 {\n" //
					+ "    public void foo() {\n" //
					+ "//\n" //
					+ "    }\n" //
					+ "}\n";
			String expected1= sample;

			assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });
		} finally {
			project.setOption(DefaultCodeFormatterConstants.FORMATTER_NEVER_INDENT_LINE_COMMENTS_ON_FIRST_COLUMN, DefaultCodeFormatterConstants.FALSE);
		}
	}

	@Test
	public void testUnimplementedCode01() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public interface IFace {\n" //
				+ "    void foo();\n" //
				+ "    void bar();\n" //
				+ "}\n";
		pack1.createCompilationUnit("IFace.java", sample, false, null);

		sample= "" //
				+ "package test;\n" //
				+ "public class E01 implements IFace {\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E01.java", sample, false, null);

		sample= "" //
				+ "package test;\n" //
				+ "public class E02 implements IFace {\n" //
				+ "    public class Inner implements IFace {\n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E02.java", sample, false, null);

		enable(CleanUpConstants.ADD_MISSING_METHODES);

		String expected1= "" //
				+ "package test;\n" //
				+ "public class E01 implements IFace {\n" //
				+ "\n" //
				+ "    /* comment */\n" //
				+ "    @Override\n" //
				+ "    public void foo() {\n" //
				+ "        //TODO\n" //
				+ "        \n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /* comment */\n" //
				+ "    @Override\n" //
				+ "    public void bar() {\n" //
				+ "        //TODO\n" //
				+ "        \n" //
				+ "    }\n" //
				+ "}\n";

		String expected2= "" //
				+ "package test;\n" //
				+ "public class E02 implements IFace {\n" //
				+ "    public class Inner implements IFace {\n" //
				+ "\n" //
				+ "        /* comment */\n" //
				+ "        @Override\n" //
				+ "        public void foo() {\n" //
				+ "            //TODO\n" //
				+ "            \n" //
				+ "        }\n" //
				+ "\n" //
				+ "        /* comment */\n" //
				+ "        @Override\n" //
				+ "        public void bar() {\n" //
				+ "            //TODO\n" //
				+ "            \n" //
				+ "        }\n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /* comment */\n" //
				+ "    @Override\n" //
				+ "    public void foo() {\n" //
				+ "        //TODO\n" //
				+ "        \n" //
				+ "    }\n" //
				+ "\n" //
				+ "    /* comment */\n" //
				+ "    @Override\n" //
				+ "    public void bar() {\n" //
				+ "        //TODO\n" //
				+ "        \n" //
				+ "    }\n" //
				+ "}\n";

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1, cu2 }, new String[] { expected1, expected2 });
	}

	@Test
	public void testUnimplementedCode02() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //
				+ "package test;\n" //
				+ "public interface IFace {\n" //
				+ "    void foo();\n" //
				+ "}\n";
		pack1.createCompilationUnit("IFace.java", sample, false, null);

		sample= "" //
				+ "package test;\n" //
				+ "public class E01 implements IFace {\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("E01.java", sample, false, null);

		sample= "" //
				+ "package test;\n" //
				+ "public class E02 implements IFace {\n" //
				+ "    \n" //
				+ "    public class Inner implements IFace {\n" //
				+ "        \n" //
				+ "    }\n" //
				+ "}\n";
		ICompilationUnit cu2= pack1.createCompilationUnit("E02.java", sample, false, null);

		enable(UnimplementedCodeCleanUp.MAKE_TYPE_ABSTRACT);

		sample= "" //
				+ "package test;\n" //
				+ "public abstract class E01 implements IFace {\n" //
				+ "}\n";
		String expected1= sample;

		sample= "" //
				+ "package test;\n" //
				+ "public abstract class E02 implements IFace {\n" //
				+ "    \n" //
				+ "    public abstract class Inner implements IFace {\n" //
				+ "        \n" //
				+ "    }\n" //
				+ "}\n";
		String expected2= sample;

		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1, cu2 }, new String[] { expected1, expected2 });
	}

	@Test
	public void testRemoveRedundantModifiers () throws Exception {
		StringBuffer buf;
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);

		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public abstract interface IFoo {\n");
		buf.append("  public static final int MAGIC_NUMBER = 646;\n");
		buf.append("  public abstract int foo ();\n");
		buf.append("  abstract void func ();\n");
		buf.append("  public int bar (int bazz);\n");
		buf.append("}\n");
		ICompilationUnit cu1= pack1.createCompilationUnit("IFoo.java", buf.toString(), false, null);

		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public interface IFoo {\n");
		buf.append("  int MAGIC_NUMBER = 646;\n");
		buf.append("  int foo ();\n");
		buf.append("  void func ();\n");
		buf.append("  int bar (int bazz);\n");
		buf.append("}\n");
		String expected1 = buf.toString();

		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public final class Sealed {\n");
		buf.append("  public final void foo () {};\n");
		buf.append("  \n");
		buf.append("  abstract static interface INested {\n");
		buf.append("  }\n");
		buf.append("}\n");
		ICompilationUnit cu2= pack1.createCompilationUnit("Sealed.java", buf.toString(), false, null);

		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public final class Sealed {\n");
		buf.append("  public void foo () {};\n");
		buf.append("  \n");
		buf.append("  interface INested {\n");
		buf.append("  }\n");
		buf.append("}\n");
		String expected2 = buf.toString();

		// Anonymous class within an interface:
		// public keyword must not be removed (see bug#536612)
		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public interface X {\n");
		buf.append("  void B();\n");
		buf.append("  void A();\n");
		buf.append("  default X y() {\n");
		buf.append("    return new X() {\n");
		buf.append("      @Override public void A() {}\n");
		buf.append("      @Override public void B() {}\n");
		buf.append("    };\n");
		buf.append("  }\n");
		buf.append("}\n");
		String expected3 = buf.toString();
		ICompilationUnit cu3= pack1.createCompilationUnit("AnonymousNestedInInterface.java", buf.toString(), false, null);

		String input4= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "public enum SampleEnum {\n" //
				+ "  VALUE1(\"1\"), VALUE2(\"2\");\n" //
				+ "\n" //
				+ "  private SampleEnum(String string) {}\n" //
				+ "}\n";
		ICompilationUnit cu4= pack1.createCompilationUnit("SampleEnum.java", input4, false, null);

		String expected4= "" //
				+ "package test;\n" //
				+ "\n" //
				+ "public enum SampleEnum {\n" //
				+ "  VALUE1(\"1\"), VALUE2(\"2\");\n" //
				+ "\n" //
				+ "  SampleEnum(String string) {}\n" //
				+ "}\n";

		// public modifier must not be removed from enum methods
		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public interface A {\n");
		buf.append("  public static enum B {\n");
		buf.append("    public static void method () { }\n");
		buf.append("  }\n");
		buf.append("}\n");
		ICompilationUnit cu5= pack1.createCompilationUnit("NestedEnum.java", buf.toString(), false, null);
		// https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.9
		// nested enum type is implicitly static
		// Bug#538459 'public' modified must not be removed from static method in nested enum
		String expected5 = buf.toString().replace("static enum", "enum");

		// Bug#551038: final keyword must not be removed from method with varargs
		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public final class SafeVarargsExample {\n");
		buf.append("  @SafeVarargs\n");
		buf.append("  public final void errorRemoveRedundantModifiers(final String... input) {\n");
		buf.append("  }\n");
		buf.append("}\n");
		String expected6 = buf.toString();
		ICompilationUnit cu6= pack1.createCompilationUnit("SafeVarargsExample.java", buf.toString(), false, null);

		// Bug#553608: modifiers public static final must not be removed from inner enum within interface
		buf= new StringBuffer();
		buf.append("package test;\n");
		buf.append("public interface Foo {\n");
		buf.append("  enum Bar {\n");
		buf.append("    A;\n");
		buf.append("    public static final int B = 0;\n");
		buf.append("  }\n");
		buf.append("}\n");
		String expected7 = buf.toString();
		ICompilationUnit cu7= pack1.createCompilationUnit("NestedEnumExample.java", buf.toString(), false, null);

		enable(CleanUpConstants.REMOVE_REDUNDANT_MODIFIERS);
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1, cu2, cu3, cu4, cu5, cu6, cu7 }, new String[] { expected1, expected2, expected3, expected4, expected5, expected6, expected7 });

	}

	@Test
	public void testDoNotRemoveModifiers() throws Exception {
		// Given
		IPackageFragment pack= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public enum SampleEnum {\n" //$NON-NLS-1$
				+ "  VALUE1, VALUE2;\n" //$NON-NLS-1$
				+ "\n" //
				+ "  private void notAConstructor(String string) {}\n" //$NON-NLS-1$
				+ "}\n"; //$NON-NLS-1$
		ICompilationUnit cu= pack.createCompilationUnit("SampleEnum.java", sample, false, null);

		// When
		enable(CleanUpConstants.REMOVE_REDUNDANT_MODIFIERS);

		// Then
		assertRefactoringHasNoChange(new ICompilationUnit[] { cu });
	}

	@Test
	public void testDoNotTouchCleanedModifiers() throws Exception {
		// Given
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "\n" //
				+ "public interface ICleanInterface {\n" //
				+ "  int MAGIC_NUMBER = 646;\n" //
				+ "  int foo();\n" //
				+ "  void func();\n" //
				+ "  int bar(int bazz);\n" //
				+ "}\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("ICleanInterface.java", sample, false, null);

		// When
		enable(CleanUpConstants.REMOVE_REDUNDANT_MODIFIERS);

		// Then
		assertRefactoringHasNoChange(new ICompilationUnit[] { cu1 });

		// When
		ASTParser parser= ASTParser.newParser(ASTHelper.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(cu1);
		parser.setResolveBindings(true);
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		Map<String, String> options= new HashMap<>();
		options.put(CleanUpConstants.REMOVE_REDUNDANT_MODIFIERS, CleanUpOptionsCore.TRUE);
		NoChangeRedundantModifiersCleanUp cleanup= new NoChangeRedundantModifiersCleanUp(options);
		ICleanUpFix fix= cleanup.createFix(unit);

		// Then
		assertNull("ICleanInterface should not be cleaned up", fix);
	}

	@Test
	public void testRemoveRedundantSemicolons () throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test", false, null);
		String sample= "" //

		// Ensure various extra semi-colons are removed and required ones are left intact.
		// This includes a lambda expression.
				+ "package test; ;\n" //
				+ "enum cars { sedan, coupe };\n" //
				+ "public class Foo {\n" //
				+ "  int add(int a, int b) {return a+b;};\n" //
				+ "  int a= 3;; ;\n" //
				+ "  int b= 7; // leave this ; alone\n" //
				+ "  int c= 10; /* and this ; too */\n" //
				+ "  public int foo () {\n" //
				+ "    ;\n" //
				+ "    Runnable r = () -> {\n" //
				+ "      System.out.println(\"running\");\n" //
				+ "    };;\n" //
				+ "    for (;;)\n" //
				+ "      ;;\n" //
				+ "      ;\n" //
				+ "    while (a++ < 1000) ;\n" //
				+ "  };\n" //
				+ "};\n";
		ICompilationUnit cu1= pack1.createCompilationUnit("Foo.java", sample, false, null);

		// Ensure semi-colon after lambda expression remains intact.
		sample= "" //
				+ "package test;\n" //
				+ "enum cars { sedan, coupe }\n" //
				+ "public class Foo {\n" //
				+ "  int add(int a, int b) {return a+b;}\n" //
				+ "  int a= 3;\n" //
				+ "  int b= 7; // leave this ; alone\n" //
				+ "  int c= 10; /* and this ; too */\n" //
				+ "  public int foo () {\n" //
				+ "    \n" //
				+ "    Runnable r = () -> {\n" //
				+ "      System.out.println(\"running\");\n" //
				+ "    };\n" //
				+ "    for (;;)\n" //
				+ "      ;\n" //
				+ "      \n" //
				+ "    while (a++ < 1000) ;\n" //
				+ "  }\n" //
				+ "}\n";
		String expected1 = sample;

		enable(CleanUpConstants.REMOVE_REDUNDANT_SEMICOLONS);
		assertRefactoringResultAsExpected(new ICompilationUnit[] { cu1 }, new String[] { expected1 });

	}

	@Test
	public void testBug491087() throws Exception {
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		String sample= "" //
				+ "package test1;\n" //
				+ "interface A {\n" //
				+ "    class B {\n" //
				+ "        String field;\n" //
				+ "       B() { field = \"foo\"; }\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "class C {\n" //
				+ "    class D {\n" //
				+ "       String field;\n" //
				+ "       D() { field = \"bar\"; }\n" //
				+ "    }\n" //
				+ "}\n";

		ICompilationUnit cu1= pack1.createCompilationUnit("C.java", sample, false, null);

		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS);
		enable(CleanUpConstants.MEMBER_ACCESSES_NON_STATIC_FIELD_USE_THIS_ALWAYS);

		sample= "" //
				+ "package test1;\n" //
				+ "interface A {\n" //
				+ "    class B {\n" //
				+ "        String field;\n" //
				+ "       B() { this.field = \"foo\"; }\n" //
				+ "    }\n" //
				+ "}\n" //
				+ "class C {\n" //
				+ "    class D {\n" //
				+ "       String field;\n" //
				+ "       D() { this.field = \"bar\"; }\n" //
				+ "    }\n" //
				+ "}\n";

		String expected1= sample;

		assertGroupCategoryUsed(new ICompilationUnit[] { cu1 }, new HashSet<>(Arrays.asList(new String[] {
				Messages.format(FixMessages.CodeStyleFix_QualifyWithThis_description, new Object[] {"field", "this"})
		})));
		assertRefactoringResultAsExpected(new ICompilationUnit[] {cu1}, new String[] {expected1});
	}
}
