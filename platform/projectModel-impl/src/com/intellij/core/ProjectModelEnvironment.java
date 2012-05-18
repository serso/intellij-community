/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.impl.ProjectPathMacroManager;
import com.intellij.openapi.module.ModuleManager;

/**
 * @author yole
 */
public class ProjectModelEnvironment {
  public static void register(CoreEnvironment env) {
    PathMacrosImpl pathMacros = new PathMacrosImpl();
    env.registerApplicationComponent(PathMacros.class, pathMacros);
    env.registerProjectComponent(ModuleManager.class, new CoreModuleManager(env.getProject(), env.getParentDisposable()));
    env.registerProjectComponent(PathMacroManager.class, new ProjectPathMacroManager(pathMacros, env.getProject()));
  }
}