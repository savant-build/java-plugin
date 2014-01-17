/*
 * Copyright (c) 2014, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.plugin.java

import static org.savantbuild.dep.DependencyService.ResolveConfiguration.*
import static org.savantbuild.dep.DependencyService.ResolveConfiguration

/**
 * Settings class that defines the settings used by the Java plugin.
 */
class JavaSettings {
  String javaVersion
  String compilerArguments = ""
  ResolveConfiguration mainDependencyResolveConfiguration = new ResolveConfiguration()
      .with("compile", new TypeResolveConfiguration(true, false))
      .with("provided", new TypeResolveConfiguration(true, false))
  ResolveConfiguration testDependencyResolveConfiguration = new ResolveConfiguration()
      .with("compile", new TypeResolveConfiguration(true, false))
      .with("test-compile", new TypeResolveConfiguration(true, false))
      .with("provided", new TypeResolveConfiguration(true, false))
}
