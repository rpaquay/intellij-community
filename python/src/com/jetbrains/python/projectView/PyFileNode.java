package com.jetbrains.python.projectView;

import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author yole
 */
public class PyFileNode extends PsiFileNode {
  public PyFileNode(Project project, PsiFile value, ViewSettings viewSettings) {
    super(project, value, viewSettings);
  }

  @Override
  public Collection<AbstractTreeNode> getChildrenImpl() {
    PyFile value = (PyFile) getValue();
    List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    for (PyClass child : value.getTopLevelClasses()) {
      children.add(new PyElementNode(myProject, child, getSettings()));
    }
    for (PyFunction function : value.getTopLevelFunctions()) {
      children.add(new PyElementNode(myProject, function, getSettings()));
    }
    return children;
  }

  @Override
  public boolean expandOnDoubleClick() {
    return false;
  }
}
