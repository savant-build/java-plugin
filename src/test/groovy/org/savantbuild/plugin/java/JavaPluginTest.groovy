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

import org.savantbuild.dep.domain.*
import org.savantbuild.dep.workflow.FetchWorkflow
import org.savantbuild.dep.workflow.PublishWorkflow
import org.savantbuild.dep.workflow.Workflow
import org.savantbuild.dep.workflow.process.CacheProcess
import org.savantbuild.dep.workflow.process.URLProcess
import org.savantbuild.domain.Project
import org.savantbuild.io.FileTools
import org.savantbuild.output.Output
import org.savantbuild.output.SystemOutOutput
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream

import static org.testng.Assert.*

/**
 * Tests the Java plugin.
 *
 * @author Brian Pontarelli
 */
class JavaPluginTest {
  public static Path projectDir

  @BeforeSuite
  public void beforeSuite() {
    println "Setup"
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("LICENSE"))) {
      projectDir = Paths.get("../java-plugin")
    }
  }

  @Test
  public void all() throws Exception {
    println "Start"
    FileTools.prune(projectDir.resolve("build/cache"))

    Output output = new SystemOutOutput(true)
//    output.enableDebug()

    Project project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.savantbuild.test"
    project.name = "test-project"
    project.version = new Version("1.0")
    project.license = License.Apachev2

    Path repositoryPath = Paths.get(System.getProperty("user.home"), "dev/inversoft/repositories/savant")
    project.dependencies = new Dependencies(new DependencyGroup("test-compile", false, new Dependency("org.testng:testng:6.8.7:jar", false)))
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, projectDir.resolve("build/cache").toString()),
            new URLProcess(output, repositoryPath.toUri().toString(), null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, projectDir.resolve("build/cache").toString())
        )
    )

    JavaPlugin plugin = new JavaPlugin(project, output)
    plugin.settings.javaVersion = "1.6"

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
  }

  private static void assertJarContains(Path jarFile, String... entries) {
    JarFile jf = new JarFile(jarFile.toFile())
    entries.each({ entry -> assertNotNull(jf.getEntry(entry), "Jar [${jarFile}] is missing entry [${entry}]") })
    jf.close()
  }

  private static void assertJarFileEquals(Path jarFile, String entry, Path original) throws IOException {
    JarInputStream jis = new JarInputStream(Files.newInputStream(jarFile));
    JarEntry jarEntry = jis.getNextJarEntry();
    while (jarEntry != null && !jarEntry.getName().equals(entry)) {
      jarEntry = jis.getNextJarEntry();
    }

    if (jarEntry == null) {
      fail("Jar [" + jarFile + "] is missing entry [" + entry + "]");
    }

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buf = new byte[1024];
    int length;
    while ((length = jis.read(buf)) != -1) {
      baos.write(buf, 0, length);
    }

    println Files.getLastModifiedTime(original)
    assertEquals(Files.readAllBytes(original), baos.toByteArray());
    assertEquals(jarEntry.getSize(), Files.size(original));
    assertEquals(jarEntry.getCreationTime(), Files.getAttribute(original, "creationTime"));
//    assertEquals(jarEntry.getLastModifiedTime(), Files.getLastModifiedTime(original));
//    assertEquals(jarEntry.getTime(), Files.getLastModifiedTime(original).toMillis());
  }
}
