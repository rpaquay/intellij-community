package com.jetbrains.python.patterns;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PythonPatterns extends PlatformPatterns {
  public static PyElementPattern.Capture<PyLiteralExpression> pyLiteralExpression() {
    return new PyElementPattern.Capture<PyLiteralExpression>(new InitialPatternCondition<PyLiteralExpression>(PyLiteralExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof PyLiteralExpression;
      }
    });
  }

  public static PyElementPattern.Capture<PyExpression> pyArgument(final String functionName, final int index) {
    return new PyElementPattern.Capture<PyExpression>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return isCallArgument(o, functionName, index);
      }
    });
  }

  public static PyElementPattern.Capture<PyExpression> pyModuleFunctionArgument(final String functionName, final int index, final String moduleName) {
    return new PyElementPattern.Capture<PyExpression>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        Callable function = resolveCalledFunction(o, functionName, index);
        if (!(function instanceof PyFunction)) {
          return false;
        }
        ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class);
        if (!(scopeOwner instanceof PyFile)) {
          return false;
        }
        return moduleName.equals(FileUtil.getNameWithoutExtension(scopeOwner.getName()));
      }
    });
  }

  public static PyElementPattern.Capture<PyExpression> pyMethodArgument(final String functionName, final int index, final String classQualifiedName) {
    return new PyElementPattern.Capture<PyExpression>(new InitialPatternCondition<PyExpression>(PyExpression.class) {
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        Callable function = resolveCalledFunction(o, functionName, index);
        if (!(function instanceof PyFunction)) {
          return false;
        }
        ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class);
        if (!(scopeOwner instanceof PyClass)) {
          return false;
        }
        return classQualifiedName.equals(((PyClass)scopeOwner).getQualifiedName());
      }
    });
  }

  private static Callable resolveCalledFunction(Object o, String functionName, int index) {
    if (!isCallArgument(o, functionName, index)) {
      return null;
    }
    PyExpression expression = (PyExpression) o;
    PyCallExpression call = (PyCallExpression) expression.getParent().getParent();

    // TODO is it better or worse to allow implicits here?
    PyResolveContext context = PyResolveContext.noImplicits()
      .withTypeEvalContext(TypeEvalContext.codeAnalysis(expression.getContainingFile()));

    PyCallExpression.PyMarkedCallee callee = call.resolveCallee(context);
    return callee != null ? callee.getCallable() : null;
  }

  private static boolean isCallArgument(Object o, String functionName, int index) {
    if (!(o instanceof PyExpression)) {
      return false;
    }
    PsiElement parent = ((PyExpression)o).getParent();
    if (!(parent instanceof PyArgumentList)) {
      return false;
    }
    PsiElement parent1 = parent.getParent();
    if (!(parent1 instanceof PyCallExpression)) {
      return false;
    }
    PyExpression methodExpression = ((PyCallExpression)parent1).getCallee();
    if (!(methodExpression instanceof PyReferenceExpression)) {
      return false;
    }
    String referencedName = ((PyReferenceExpression)methodExpression).getReferencedName();
    if (referencedName == null || !referencedName.equals(functionName)) {
      return false;
    }
    int i = 0;
    for (PsiElement child : parent.getChildren()) {
      if (i == index) {
        return child == o;
      }
      i++;
    }
    return false;
  }
}
