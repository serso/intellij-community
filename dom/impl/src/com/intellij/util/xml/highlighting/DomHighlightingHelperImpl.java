/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.*;
import com.intellij.util.xml.impl.GenericValueReferenceProvider;
import com.intellij.util.xml.reflect.DomChildrenDescription;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import com.intellij.util.xml.reflect.DomGenericInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class DomHighlightingHelperImpl extends DomHighlightingHelper {
  private final GenericValueReferenceProvider myProvider = new GenericValueReferenceProvider();
  private final DomElementAnnotationsManagerImpl myAnnotationsManager;

  public DomHighlightingHelperImpl(final DomElementAnnotationsManagerImpl annotationsManager) {
    myAnnotationsManager = annotationsManager;
  }

  public void runAnnotators(DomElement element, DomElementAnnotationHolder holder, Class<? extends DomElement> rootClass) {
    myAnnotationsManager.annotate(element, holder, rootClass);
  }

  @NotNull
  public List<DomElementProblemDescriptor> checkRequired(DomElement element, final DomElementAnnotationHolder holder) {
    final Required required = element.getAnnotation(Required.class);
    if (required != null) {
      final XmlElement xmlElement = element.getXmlElement();
      if (required.value()) {
        if (xmlElement == null) {
          if (element instanceof GenericAttributeValue) {
            return Arrays.asList(holder.createProblem(element, IdeBundle.message("attribute.0.should.be.defined", element.getXmlElementName())));
          }
          return Arrays.asList(holder.createProblem(element, IdeBundle.message("child.tag.0.should.be.defined", element.getXmlElementName())));
        }
        if (element instanceof GenericDomValue) {
          return ContainerUtil.createMaybeSingletonList(checkRequiredGenericValue((GenericDomValue)element, required, holder));
        }
      }
    }

    final SmartList<DomElementProblemDescriptor> list = new SmartList<DomElementProblemDescriptor>();
    final DomGenericInfo info = element.getGenericInfo();
    for (final DomChildrenDescription description : info.getChildrenDescriptions()) {
      if (description instanceof DomCollectionChildDescription && description.getValues(element).isEmpty()) {
        final DomCollectionChildDescription childDescription = (DomCollectionChildDescription)description;
        final Required annotation = description.getAnnotation(Required.class);
        if (annotation != null && annotation.value()) {
          list.add(holder.createProblem(element, childDescription,
                                        IdeBundle.message("child.tag.0.should.be.defined", description.getXmlElementName())));
        }
      }
    }
    return list;
  }

  @NotNull
  public List<DomElementProblemDescriptor> checkExtendClass(GenericDomValue element, final DomElementAnnotationHolder holder) {
    final Class genericValueParameter = DomUtil.getGenericValueParameter(element.getDomElementType());
    if (genericValueParameter == null || !PsiClass.class.isAssignableFrom(genericValueParameter)) {
      return Collections.emptyList();
    }

    PsiClass value = (PsiClass)element.getValue();
    if (value != null) {
      ExtendClass extend = element.getAnnotation(ExtendClass.class);
      if (extend != null) {
        Project project = element.getManager().getProject();
        final String name = extend.value();
        PsiClass extendClass = PsiManager.getInstance(project).findClass(name, GlobalSearchScope.allScope(project));
        if (extendClass != null) {
          final SmartList<DomElementProblemDescriptor> list = new SmartList<DomElementProblemDescriptor>();
          if (!name.equals(value.getQualifiedName()) && !value.isInheritor(extendClass, true)) {
            String message = IdeBundle.message("class.is.not.a.subclass", value.getQualifiedName(), extendClass.getQualifiedName());
            list.add(holder.createProblem(element, message));
          } else if (extend.instantiatable()) {
            if (value.hasModifierProperty(PsiModifier.ABSTRACT)) {
              list.add(holder.createProblem(element, IdeBundle.message("class.is.not.concrete", value.getQualifiedName())));
            } else if (!value.hasModifierProperty(PsiModifier.PUBLIC)) {
              list.add(holder.createProblem(element, IdeBundle.message("class.is.not.public", value.getQualifiedName())));
            } else if (!hasDefaultConstructor(value)) {
              if (extend.canBeDecorator()) {
                boolean hasConstructor = false;

                for (PsiMethod method : value.getConstructors()) {
                  final PsiParameterList psiParameterList = method.getParameterList();
                  if (psiParameterList.getParametersCount() != 1) continue;
                  final PsiType psiType = psiParameterList.getParameters()[0].getTypeElement().getType();
                  if (psiType instanceof PsiClassType) {
                    final PsiClass psiClass = ((PsiClassType)psiType).resolve();
                    if (psiClass != null && InheritanceUtil.isInheritorOrSelf(psiClass, extendClass, true)) {
                      hasConstructor = true;
                      break;
                    }
                  }
                } if (!hasConstructor) {
                list.add(holder.createProblem(element, IdeBundle.message("class.decorator.or.has.default.constructor", value.getQualifiedName())));
              }
              } else {
                list.add(holder.createProblem(element, IdeBundle.message("class.has.no.default.constructor", value.getQualifiedName())));
              }
            }
          }
          return list;
        }
      }
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<DomElementProblemDescriptor> checkResolveProblems(GenericDomValue element, final DomElementAnnotationHolder holder) {
    final XmlElement valueElement = DomUtil.getValueElement(element);
    if (valueElement != null && !isSoftReference(element)) {
      final SmartList<DomElementProblemDescriptor> list = new SmartList<DomElementProblemDescriptor>();
      for (final PsiReference reference : myProvider.getReferencesByElement(valueElement)) {
        if (hasBadResolve(element, reference)) {
          list.add(holder.createResolveProblem(element, reference));
        }
      }
      return list;
    }
    return Collections.emptyList();
  }

  @NotNull
  public List<DomElementProblemDescriptor> checkNameIdentity(DomElement element, final DomElementAnnotationHolder holder) {
    final String elementName = element.getGenericInfo().getElementName(element);
    if (StringUtil.isNotEmpty(elementName)) {
      final DomElement domElement = DomUtil.findDuplicateNamedValue(element, elementName);
      if (domElement != null) {
        final String typeName = ElementPresentationManager.getTypeNameForObject(element);
        final GenericDomValue genericDomValue = domElement.getGenericInfo().getNameDomElement(element);
        return Arrays.asList(holder.createProblem(genericDomValue, domElement.getRoot().equals(element.getRoot())
                                                                   ? IdeBundle.message("model.highlighting.identity", typeName)
                                                                   : IdeBundle.message("model.highlighting.identity.in.other.file", typeName,
                                                                                       domElement.getXmlTag().getContainingFile().getName())));
      }
    }
    return Collections.emptyList();
  }

  private static boolean hasBadResolve(GenericDomValue value, PsiReference reference) {
    if (XmlHighlightVisitor.hasBadResolve(reference)) {
      final Converter converter = value.getConverter();
      if (converter instanceof ResolvingConverter) {
        final ResolvingConverter resolvingConverter = (ResolvingConverter)converter;
        final Set additionalVariants = resolvingConverter.getAdditionalVariants();
        if (additionalVariants.contains(value.getStringValue())) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

  private static boolean isSoftReference(GenericDomValue value) {
    final Resolve resolve = value.getAnnotation(Resolve.class);
    if (resolve != null && resolve.soft()) return true;

    final Convert convert = value.getAnnotation(Convert.class);
    if (convert != null && convert.soft()) return true;

    return false;
  }

  @Nullable
  private static DomElementProblemDescriptor checkRequiredGenericValue(final GenericDomValue child, final Required required,
                                                                       final DomElementAnnotationHolder annotator) {
    assert child.getXmlTag() != null;

    final String stringValue = child.getStringValue();
    assert stringValue != null;
    if (required.nonEmpty() && stringValue.trim().length() == 0) {
      return annotator.createProblem(child, IdeBundle.message("value.must.not.be.empty"));
    }
    if (required.identifier() && !PsiManager.getInstance(child.getManager().getProject()).getNameHelper().isIdentifier(stringValue)) {
      return annotator.createProblem(child, IdeBundle.message("value.must.be.identifier"));
    }
    return null;
  }


  private static boolean hasDefaultConstructor(PsiClass clazz) {
    final PsiMethod[] constructors = clazz.getConstructors();
    if (constructors.length > 0) {
      for (PsiMethod cls: constructors) {
        if ((cls.hasModifierProperty(PsiModifier.PUBLIC) || cls.hasModifierProperty(PsiModifier.PROTECTED)) && cls.getParameterList().getParametersCount() == 0) {
          return true;
        }
      }
    } else {
      final PsiClass superClass = clazz.getSuperClass();
      return superClass == null || hasDefaultConstructor(superClass);
    }
    return false;
  }

}
