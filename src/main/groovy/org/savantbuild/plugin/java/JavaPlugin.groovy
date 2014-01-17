/*
 * Copyright (c) 2013, Inversoft Inc., All Rights Reserved
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

import org.savantbuild.dep.DependencyService.ResolveConfiguration
import org.savantbuild.dep.domain.ArtifactID
import org.savantbuild.domain.Project
import org.savantbuild.io.FileTools
import org.savantbuild.lang.Classpath
import org.savantbuild.output.Output
import org.savantbuild.plugin.dep.DependencyPlugin
import org.savantbuild.plugin.file.FilePlugin
import org.savantbuild.plugin.groovy.BaseGroovyPlugin

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function
import java.util.function.Predicate

/**
 * The Java plugin. The public methods on this class define the features of the plugin.
 */
class JavaPlugin extends BaseGroovyPlugin {
  public static final String ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.savantbuild.plugin.java.properties] " +
      "that contains the system configuration for the Java system. This file should include the location of the JDK " +
      "(java and javac) by version. These properties look like this:\n\n" +
      "  1.6=/Library/Java/JavaVirtualMachines/1.6.0_65-b14-462.jdk/Contents/Home\n" +
      "  1.7=/Library/Java/JavaVirtualMachines/jdk1.7.0_10.jdk/Contents/Home\n" +
      "  1.8=/Library/Java/JavaVirtualMachines/jdk1.8.0.jdk/Contents/Home\n"
  JavaLayout layout = new JavaLayout()
  JavaSettings settings = new JavaSettings()
  Properties properties
  Path javacPath
  FilePlugin filePlugin
  DependencyPlugin dependencyPlugin

  JavaPlugin(Project project, Output output) {
    super(project, output)
    filePlugin = new FilePlugin(project, output)
    dependencyPlugin = new DependencyPlugin(project, output)
    properties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "java", "java", "jar"), ERROR_MESSAGE)
  }

  /**
   * Cleans the build directory by completely deleting it.
   */
  void clean() {
    Path buildDir = project.directory.resolve(layout.buildDirectory)
    output.info "Cleaning [${buildDir}]"
    FileTools.prune(buildDir)
  }

  /**
   * Compiles the main Java files (src/main/java by default).
   */
  void compileMain() {
    initialize()
    compile(layout.mainSourceDirectory, layout.mainBuildDirectory, settings.mainDependencyResolveConfiguration)
    copyResources(layout.mainResourceDirectory, layout.mainBuildDirectory)
  }

  /**
   * Compiles the test Javafiles (src/test/java by default).
   */
  void compileTest() {
    initialize()
    compile(layout.testSourceDirectory, layout.testBuildDirectory, settings.testDependencyResolveConfiguration, layout.mainBuildDirectory)
    copyResources(layout.testResourceDirectory, layout.testBuildDirectory)
  }

  /**
   * Compiles an arbitrary source directory to an arbitrary build directory.
   *
   * @param sourceDirectory The source directory that contains the Java source files.
   * @param buildDirectory The build directory to compile the Java files to.
   * @param resolveConfiguration The ResolveConfiguration for building the classpath from the project's depenedencies.
   */
  void compile(Path sourceDirectory, Path buildDirectory, ResolveConfiguration resolveConfiguration, Path... additionalClasspath) {
    Path resolvedSourceDir = project.directory.resolve(sourceDirectory)
    Path resolvedBuildDir = project.directory.resolve(buildDirectory)

    output.debug "Looking for modified files to compile in [${resolvedSourceDir}] compared with [${resolvedBuildDir}]"

    Predicate<Path> filter = FileTools.extensionFilter(".java")
    Function<Path, Path> mapper = FileTools.extensionMapper(".java", ".class")
    List<Path> filesToCompile = FileTools.modifiedFiles(resolvedSourceDir, resolvedBuildDir, filter, mapper)
                                         .collect({ path -> sourceDirectory.resolve(path) })
    if (filesToCompile.isEmpty()) {
      output.info("Skipping compile for source directory [${sourceDirectory}]. No files need compiling")
      return
    }

    output.info "Compiling [${filesToCompile.size()}] Java classes from [${sourceDirectory}] to [${buildDirectory}]"

    String command = "${javacPath} ${settings.compilerArguments} ${classpath(resolveConfiguration, additionalClasspath)} -sourcepath ${sourceDirectory} -d ${buildDirectory} ${filesToCompile.join(" ")}"
    Files.createDirectories(resolvedBuildDir)
    Process process = command.execute(null, project.directory.toFile())
    process.consumeProcessOutput((Appendable) System.out, System.err)
    process.waitFor()

    int exitCode = process.exitValue()
    if (exitCode != 0) {
      fail("Compilation failed")
    }
  }

  /**
   * Copies the resource files from the source directory to the build directory. This copies all of the files
   * recursively to the build directory.
   *
   * @param sourceDirectory The source directory that contains the files to copy.
   * @param buildDirectory The build directory to copy the files to.
   */
  void copyResources(Path sourceDirectory, Path buildDirectory) {
    filePlugin.copy {
      to(buildDirectory)
      fileSet(sourceDirectory)
    }
  }

  void jar() {
    initialize()

    jar(project.toArtifact().getArtifactFile(), layout.mainBuildDirectory)
    jar(project.toArtifact().getArtifactSourceFile(), layout.mainSourceDirectory, layout.mainResourceDirectory)
    jar(project.toArtifact().getArtifactTestFile(), layout.testBuildDirectory)
    jar(project.toArtifact().getArtifactTestSourceFile(), layout.testSourceDirectory, layout.testResourceDirectory)
  }

  void jar(String jarFile, Path... directories) {
    Path jarFilePath = layout.jarOutputDirectory.resolve(jarFile)

    output.info "Creating JAR [${jarFile}]"

    filePlugin.jar(jarFilePath) {
      directories.each { dir ->
        output.debug("Inspecting directory [${dir}] for JAR file")
        if (Files.isDirectory(project.directory.resolve(dir))) {
          fileSet(dir)
        }
      }
    }
  }

  private String classpath(ResolveConfiguration resolveConfiguration, Path... additionalPaths) {
    Classpath classpath = dependencyPlugin.classpath(resolveConfiguration) {
      paths(additionalPaths)
    }
    return classpath.toString("-classpath ")
  }

  private void initialize() {
    if (!settings.javaVersion) {
      fail("You must configure the Java version to use with the settings object. It will look something like this:\n\n" +
          "  groovy.settings.javaVersion=\"1.7\"")
    }

    String javaHome = properties.getProperty(settings.javaVersion)
    if (!javaHome) {
      fail("No JDK is configured for version [${settings.javaVersion}].\n\n" + ERROR_MESSAGE)
    }

    javacPath = Paths.get(javaHome, "bin/javac")
    if (!Files.isRegularFile(javacPath)) {
      fail("The javac compiler [${javacPath.toAbsolutePath()}] does not exist.")
    }
    if (!Files.isExecutable(javacPath)) {
      fail("The javac compiler [${javacPath.toAbsolutePath()}] is not executable.")
    }
  }
}
