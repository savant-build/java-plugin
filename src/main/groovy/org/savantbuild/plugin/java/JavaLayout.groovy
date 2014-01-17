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

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Layout class that defines the directories used by the Java plugin.
 */
class JavaLayout {
  Path buildDirectory = Paths.get("build")
  Path jarOutputDirectory = buildDirectory.resolve("jars")
  Path mainSourceDirectory = Paths.get("src/main/java")
  Path mainResourceDirectory = Paths.get("src/main/resources")
  Path mainBuildDirectory = buildDirectory.resolve("classes/main")
  Path testSourceDirectory = Paths.get("src/test/java")
  Path testResourceDirectory = Paths.get("src/test/resources")
  Path testBuildDirectory = buildDirectory.resolve("classes/test")
}
