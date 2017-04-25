/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.plugins.cl;

import com.intellij.diagnostic.PluginException;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * @since 6.03.2003
 */
public class PluginClassLoader extends UrlClassLoader {
  private final ClassLoader[] myParents;
  private final PluginId myPluginId;
  private final String myPluginVersion;
  private final List<String> myLibDirectories;

  public PluginClassLoader(@NotNull List<URL> urls,
                           @NotNull ClassLoader[] parents,
                           PluginId pluginId,
                           String version,
                           File pluginRoot) {
    super(build().urls(urls).allowLock().useCache());
    myParents = parents;
    myPluginId = pluginId;
    myPluginVersion = version;
    myLibDirectories = ContainerUtil.newSmartList();
    File libDir = new File(pluginRoot, "lib");
    if (libDir.exists()) {
      myLibDirectories.add(libDir.getAbsolutePath());
    }
  }

  @Override
  public Class loadClass(@NotNull String name, final boolean resolve) throws ClassNotFoundException {
    Class c = tryLoadingClass(name, resolve, null);
    if (c == null) {
      throw new ClassNotFoundException(name + " " + this);
    }
    return c;
  }

  // Changed sequence in which classes are searched, this is essential if plugin uses library,
  // a different version of which is used in IDEA.
  @Nullable
  private Class tryLoadingClass(@NotNull String name, final boolean resolve, @Nullable Set<ClassLoader> visited) {
    Class c = null;
    if (!mustBeLoadedByPlatform(name)) {
      c = loadClassInsideSelf(name);
    }

    if (c == null) {
      c = loadClassFromParents(name, visited);
    }

    if (c != null) {
      if (resolve) {
        resolveClass(c);
      }
      return c;
    }

    return null;
  }

  private static boolean mustBeLoadedByPlatform(String className) {
    //FunctionX interfaces from kotlin-runtime must be loaded by the platform classloader. Otherwise if a plugin bundles its own version
    // of kotlin-runtime.jar it won't be possible to call platform's methods with Kotlin functional types in signatures from such a plugin.
    //We assume that FunctionX interfaces don't change between Kotlin versions so it's safe to always load them from platform's kotlin-runtime.
    return className.startsWith("kotlin.jvm.functions.");
  }

  @Nullable
  private Class loadClassFromParents(final String name, Set<ClassLoader> visited) {
    for (ClassLoader parent : myParents) {
      if (visited == null) visited = ContainerUtilRt.newHashSet(this);
      if (!visited.add(parent)) {
        continue;
      }

      if (parent instanceof PluginClassLoader) {
        Class c = ((PluginClassLoader)parent).tryLoadingClass(name, false, visited);
        if (c != null) {
          return c;
        }
        continue;
      }

      try {
        return parent.loadClass(name);
      }
      catch (ClassNotFoundException ignoreAndContinue) {
        // Ignore and continue
      }
    }

    return null;
  }

  @Nullable
  private synchronized Class loadClassInsideSelf(@NotNull String name) {
    Class c = findLoadedClass(name);
    if (c != null) {
      return c;
    }

    try {
      c = _findClass(name);
    }
    catch (IncompatibleClassChangeError | UnsupportedClassVersionError e) {
      throw new PluginException("While loading class " + name + ": " + e.getMessage(), e, myPluginId);
    }
    if (c != null) {
      PluginManagerCore.addPluginClass(myPluginId);
    }

    return c;
  }

  @Override
  public URL findResource(final String name) {
    final URL resource = findResourceImpl(name);
    if (resource != null) return resource;

    for (ClassLoader parent : myParents) {
      final URL parentResource = fetchResource(parent, name);
      if (parentResource != null) return parentResource;
    }

    return null;
  }

  @Nullable
  @Override
  public InputStream getResourceAsStream(final String name) {
    final InputStream stream = super.getResourceAsStream(name);
    if (stream != null) return stream;

    for (ClassLoader parent : myParents) {
      final InputStream inputStream = parent.getResourceAsStream(name);
      if (inputStream != null) return inputStream;
    }

    return null;
  }

  @Override
  public Enumeration<URL> findResources(final String name) throws IOException {
    @SuppressWarnings("unchecked") Enumeration<URL>[] resources = new Enumeration[myParents.length + 1];
    resources[0] = super.findResources(name);
    for (int idx = 0; idx < myParents.length; idx++) {
      resources[idx + 1] = fetchResources(myParents[idx], name);
    }
    return new DeepEnumeration(resources);
  }

  @SuppressWarnings("UnusedDeclaration")
  public void addLibDirectories(@NotNull Collection<String> libDirectories) {
    myLibDirectories.addAll(libDirectories);
  }

  @Override
  protected String findLibrary(String libName) {
    if (!myLibDirectories.isEmpty()) {
      String libFileName = System.mapLibraryName(libName);
      ListIterator<String> i = myLibDirectories.listIterator(myLibDirectories.size());
      while (i.hasPrevious()) {
        File libFile = new File(i.previous(), libFileName);
        if (libFile.exists()) {
          return libFile.getAbsolutePath();
        }
      }
    }

    return null;
  }

  private static URL fetchResource(ClassLoader cl, String resourceName) {
    try {
      Method findResource = getFindResourceMethod(cl.getClass(), "findResource");
      return findResource != null ? (URL)findResource.invoke(cl, resourceName) : null;
    }
    catch (Exception e) {
      Logger.getInstance(PluginClassLoader.class).error(e);
      return null;
    }
  }

  private static Enumeration<URL> fetchResources(ClassLoader cl, String resourceName) {
    try {
      Method findResources = getFindResourceMethod(cl.getClass(), "findResources");
      @SuppressWarnings("unchecked") Enumeration<URL> e = findResources == null ? null : (Enumeration)findResources.invoke(cl, resourceName);
      return e;
    }
    catch (Exception e) {
      Logger.getInstance(PluginClassLoader.class).error(e);
      return null;
    }
  }

  private static Method getFindResourceMethod(final Class<?> clClass, final String methodName) {
    try {
      final Method declaredMethod = clClass.getDeclaredMethod(methodName, String.class);
      declaredMethod.setAccessible(true);
      return declaredMethod;
    }
    catch (NoSuchMethodException e) {
      final Class superclass = clClass.getSuperclass();
      if (superclass == null || superclass.equals(Object.class)) {
        return null;
      }
      return getFindResourceMethod(superclass, methodName);
    }
  }

  public PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  public String toString() {
    return "PluginClassLoader[" + myPluginId + ", " + myPluginVersion + "] "+super.toString();
  }

  private static class DeepEnumeration implements Enumeration<URL> {
    private final Enumeration<URL>[] myEnumerations;
    private int myIndex;

    DeepEnumeration(@NotNull Enumeration<URL>[] enumerations) {
      myEnumerations = enumerations;
    }

    @Override
    public boolean hasMoreElements() {
      while (myIndex < myEnumerations.length) {
        Enumeration<URL> e = myEnumerations[myIndex];
        if (e != null && e.hasMoreElements()) return true;
        myIndex++;
      }
      return false;
    }

    @Override
    public URL nextElement() {
      if (!hasMoreElements()) {
        throw new NoSuchElementException();
      }
      return myEnumerations[myIndex].nextElement();
    }
  }
}