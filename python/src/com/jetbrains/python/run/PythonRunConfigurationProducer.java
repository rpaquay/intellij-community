package com.jetbrains.python.run;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class PythonRunConfigurationProducer extends RunConfigurationProducer<PythonRunConfiguration> {

  public PythonRunConfigurationProducer() {
    super(PythonConfigurationType.getInstance().getFactory());
  }

  @Override
  protected boolean setupConfigurationFromContext(PythonRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {

    final Location location = context.getLocation();
    if (location == null) return false;
    final PsiFile script = location.getPsiElement().getContainingFile();
    if (!isAvailable(location, script)) return false;

    final VirtualFile vFile = script.getVirtualFile();
    if (vFile == null) return false;
    configuration.setScriptName(vFile.getPath());
    final VirtualFile parent = vFile.getParent();
    if (parent != null) {
      configuration.setWorkingDirectory(parent.getPath());
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(script);
    if (module != null) {
      configuration.setUseModuleSdk(true);
      configuration.setModule(module);
    }
    configuration.setName(configuration.suggestedName());
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(PythonRunConfiguration configuration, ConfigurationContext context) {
    final Location location = context.getLocation();
    if (location == null) return false;
    final PsiFile script = location.getPsiElement().getContainingFile();
    if (!isAvailable(location, script)) return false;
    final VirtualFile virtualFile = script.getVirtualFile();
    if (virtualFile == null) return false;
    final String workingDirectory = configuration.getWorkingDirectory();
    final String scriptName = configuration.getScriptName();
    final String path = virtualFile.getPath();
    return scriptName.equals(path) || path.equals(new File(workingDirectory, scriptName).getAbsolutePath());
  }

  private static boolean isAvailable(@NotNull final Location location, @Nullable final PsiFile script) {
    if (script == null || script.getFileType() != PythonFileType.INSTANCE) {
      return false;
    }
    final Module module = ModuleUtilCore.findModuleForPsiElement(script);
    if (module != null) {
      for (RunnableScriptFilter f : Extensions.getExtensions(RunnableScriptFilter.EP_NAME)) {
        if (f.isRunnableScript(script, module, location)) {
          return false;
        }
      }
    }
    return true;
  }
}
