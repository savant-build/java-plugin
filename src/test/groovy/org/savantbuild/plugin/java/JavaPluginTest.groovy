/*
 * Copyright (c) 2014-2018, Inversoft Inc., All Rights Reserved
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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream

import org.savantbuild.dep.domain.Artifact
import org.savantbuild.dep.domain.Dependencies
import org.savantbuild.dep.domain.DependencyGroup
import org.savantbuild.dep.domain.License

import org.savantbuild.dep.workflow.FetchWorkflow
import org.savantbuild.dep.workflow.PublishWorkflow
import org.savantbuild.dep.workflow.Workflow
import org.savantbuild.dep.workflow.process.CacheProcess
import org.savantbuild.dep.workflow.process.URLProcess
import org.savantbuild.domain.Project
import org.savantbuild.domain.Version
import org.savantbuild.io.FileTools
import org.savantbuild.output.Output
import org.savantbuild.output.SystemOutOutput
import org.savantbuild.runtime.RuntimeConfiguration
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import static org.testng.Assert.assertEquals
import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertNotNull
import static org.testng.Assert.assertTrue
import static org.testng.Assert.fail

/**
 * Tests the Java plugin.
 *
 * @author Brian Pontarelli
 */
class JavaPluginTest {
  public static Path projectDir

  @BeforeSuite
  void beforeSuite() {
    println "Setup"
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("LICENSE"))) {
      projectDir = Paths.get("../java-plugin")
    }
  }

  @Test
  void all() throws Exception {
    println "Start"

    def cacheDir = projectDir.resolve("build/cache")
    FileTools.prune(cacheDir)

    Output output = new SystemOutOutput(true)
    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.savantbuild.test"
    project.name = "test-project"
    project.version = new Version("1.0")
    project.licenses.add(License.parse("ApacheV2_0", null))

    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Artifact("org.testng:testng:6.8.7:jar")))
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, cacheDir.toString(), cacheDir.toString()),
            new URLProcess(output, "https://repository.savantbuild.org", null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, cacheDir.toString(), cacheDir.toString())
        ),
        output
    )

    JavaPlugin plugin = new JavaPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.javaVersion = "14"
    plugin.settings.libraryDirectories.add("lib")

    plugin.clean()
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build")))

    plugin.compileMain()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/main/org/savantbuild/test/MyClass.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/main/main.txt")))

    plugin.compileTest()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/test/org/savantbuild/test/MyClassTest.class")))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/classes/test/test.txt")))

    plugin.jar()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"), "org/savantbuild/test/MyClass.class", "main.txt")
    assertJarFileEquals(projectDir.resolve("test-project/build/jars/test-project-1.0.0.jar"), "org/savantbuild/test/MyClass.class", projectDir.resolve("test-project/build/classes/main/org/savantbuild/test/MyClass.class"))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-1.0.0-src.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-1.0.0-src.jar"), "org/savantbuild/test/MyClass.java", "main.txt")
    assertJarFileEquals(projectDir.resolve("test-project/build/jars/test-project-1.0.0-src.jar"), "org/savantbuild/test/MyClass.java", projectDir.resolve("test-project/src/main/java/org/savantbuild/test/MyClass.java"))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0.jar"), "org/savantbuild/test/MyClassTest.class", "test.txt")
    assertJarFileEquals(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0.jar"), "org/savantbuild/test/MyClassTest.class", projectDir.resolve("test-project/build/classes/test/org/savantbuild/test/MyClassTest.class"))
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0-src.jar")))
    assertJarContains(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0-src.jar"), "org/savantbuild/test/MyClassTest.java", "test.txt")
    assertJarFileEquals(projectDir.resolve("test-project/build/jars/test-project-test-1.0.0-src.jar"), "org/savantbuild/test/MyClassTest.java", projectDir.resolve("test-project/src/test/java/org/savantbuild/test/MyClassTest.java"))

    plugin.document()
    assertTrue(Files.isRegularFile(projectDir.resolve("test-project/build/doc/index.html")))

    // Smokescreen (Calls getMainClasspath)
    plugin.printJDKModuleDeps()
  }

  private static void assertJarContains(Path jarFile, String... entries) {
    JarFile jf = new JarFile(jarFile.toFile())
    entries.each({ entry -> assertNotNull(jf.getEntry(entry), "Jar [${jarFile}] is missing entry [${entry}]") })
    jf.close()
  }

  private static void assertJarFileEquals(Path jarFile, String entry, Path original) throws IOException {
    JarInputStream jis = new JarInputStream(Files.newInputStream(jarFile))
    JarEntry jarEntry = jis.getNextJarEntry()
    while (jarEntry != null && jarEntry.getName() != entry) {
      jarEntry = jis.getNextJarEntry()
    }

    if (jarEntry == null) {
      fail("Jar [" + jarFile + "] is missing entry [" + entry + "]")
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream()
    byte[] buf = new byte[1024]
    int length
    while ((length = jis.read(buf)) != -1) {
      baos.write(buf, 0, length)
    }

    println Files.getLastModifiedTime(original)
    assertEquals(Files.readAllBytes(original), baos.toByteArray())
    assertEquals(jarEntry.getSize(), Files.size(original))
    assertEquals(jarEntry.getCreationTime(), Files.getAttribute(original, "creationTime"))
//    assertEquals(jarEntry.getLastModifiedTime(), Files.getLastModifiedTime(original));
//    assertEquals(jarEntry.getTime(), Files.getLastModifiedTime(original).toMillis());
  }
}
