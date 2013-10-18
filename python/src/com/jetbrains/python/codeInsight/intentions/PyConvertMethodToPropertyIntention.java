package com.jetbrains.python.codeInsight.intentions;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User: ktisha
 */
public class PyConvertMethodToPropertyIntention extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.convert.method.to.property");
  }

  @NotNull
  public String getText() {
    return PyBundle.message("INTN.convert.method.to.property");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }
    if (!LanguageLevel.forElement(file).isAtLeast(LanguageLevel.PYTHON26)) return false;
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function == null) return false;
    final PyClass containingClass = function.getContainingClass();
    if (containingClass == null) return false;
    if (function.getParameterList().getParameters().length > 1) return false;

    final PyDecoratorList decoratorList = function.getDecoratorList();
    if (decoratorList != null) return false;

    final boolean[] available = {false};
    function.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReturnStatement(PyReturnStatement node) {
        if (node.getExpression() != null)
          available[0] = true;
      }

      @Override
      public void visitPyYieldExpression(PyYieldExpression node) {
        available[0] = true;
      }
    });

    return available[0];
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    PyFunction problemFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (problemFunction == null) return;
    final PyClass containingClass = problemFunction.getContainingClass();
    if (containingClass == null) return;
    final List<UsageInfo> usages = PyRefactoringUtil.findUsages(problemFunction, false);

    final PyDecoratorList problemDecoratorList = problemFunction.getDecoratorList();
    List<String> decoTexts = new ArrayList<String>();
    decoTexts.add("@property");
    if (problemDecoratorList != null) {
      final PyDecorator[] decorators = problemDecoratorList.getDecorators();
      for (PyDecorator deco : decorators) {
        decoTexts.add(deco.getText());
      }
    }

    PyElementGenerator generator = PyElementGenerator.getInstance(project);
    final PyDecoratorList decoratorList = generator.createDecoratorList(decoTexts.toArray(new String[decoTexts.size()]));

    if (problemDecoratorList != null) {
      problemDecoratorList.replace(decoratorList);
    }
    else {
      problemFunction.addBefore(decoratorList, problemFunction.getFirstChild());
    }

    for (UsageInfo usage : usages) {
      final PsiElement usageElement = usage.getElement();
      if (usageElement instanceof PyReferenceExpression) {
        final PsiElement parent = usageElement.getParent();
        if (parent instanceof PyCallExpression) {
          final PyArgumentList argumentList = ((PyCallExpression)parent).getArgumentList();
          if (argumentList != null) argumentList.delete();
        }
      }
    }
  }
}
