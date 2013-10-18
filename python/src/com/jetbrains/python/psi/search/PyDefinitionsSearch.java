package com.jetbrains.python.psi.search;

import com.intellij.psi.PsiElement;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyDefinitionsSearch implements QueryExecutor<PsiElement, PsiElement> {
  public boolean execute(@NotNull final PsiElement queryParameters, @NotNull final Processor<PsiElement> consumer) {
    if (queryParameters instanceof PyClass) {
      final Query<PyClass> query = PyClassInheritorsSearch.search((PyClass)queryParameters, true);
      return query.forEach(new Processor<PyClass>() {
        public boolean process(final PyClass pyClass) {
          return consumer.process(pyClass);
        }
      });
    }
    else if (queryParameters instanceof PyFunction) {
      final Query<PyFunction> query = PyOverridingMethodsSearch.search((PyFunction) queryParameters, true);
      return query.forEach(new Processor<PyFunction>() {
        public boolean process(final PyFunction pyFunction) {
          return consumer.process(pyFunction);
        }
      });
    }
    else if (queryParameters instanceof PyTargetExpression) {  // PY-237
      final PsiElement parent = queryParameters.getParent();
      if (parent instanceof PyAssignmentStatement) {
        return consumer.process(parent);        
      }
    }
    return true;
  }
}
