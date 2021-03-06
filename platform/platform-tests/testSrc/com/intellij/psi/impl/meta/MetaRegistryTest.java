/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.meta;

import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NonNls;

/**
* @author peter
*/
public class MetaRegistryTest extends LightPlatformTestCase {

  public void testChangingMetaData() throws Throwable {
    final boolean[] flag = new boolean[]{false};
    MetaRegistry.addMetadataBinding(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return flag[0];
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }, MyTrueMetaData.class, myTestRootDisposable);
    MetaRegistry.addMetadataBinding(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return !flag[0];
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    }, MyFalseMetaData.class, myTestRootDisposable);

    final XmlTag tag = ((XmlFile)LightPlatformTestCase.createFile("a.xml", "<a/>")).getDocument().getRootTag();
    UsefulTestCase.assertInstanceOf(tag.getMetaData(), MyFalseMetaData.class);
    flag[0] = true;
    new WriteCommandAction(LightPlatformTestCase.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        tag.setName("b");
      }
    }.execute();
    UsefulTestCase.assertInstanceOf(tag.getMetaData(), MyTrueMetaData.class);
  }

  public static class MyAbstractMetaData implements PsiMetaData {
    private PsiElement myDeclaration;

    @Override
    public PsiElement getDeclaration() {
      return myDeclaration;
    }

    @Override
    public Object[] getDependences() {
      return new Object[]{myDeclaration};
    }

    @Override
    @NonNls
    public String getName() {
      return null;
    }

    @Override
    @NonNls
    public String getName(PsiElement context) {
      return null;
    }

    @Override
    public void init(PsiElement element) {
      myDeclaration = element;
    }

  }

  public static class MyTrueMetaData extends MyAbstractMetaData {}
  public static class MyFalseMetaData extends MyAbstractMetaData {}

}
