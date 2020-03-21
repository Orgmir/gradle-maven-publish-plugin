package com.vanniktech.maven.publish

import org.assertj.core.api.Java6Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.util.zip.ZipFile

@RunWith(Parameterized::class)
class MavenPublishPluginIntegrationTest(
  private val uploadArchivesTargetTaskName: String,
  private val mavenPublishTargetTaskName: String,
  private val useLegacyMode: Boolean
) {
  companion object {
    const val FIXTURES = "src/integrationTest/fixtures"
    const val EXPECTED_DIR = "expected"

    const val TEST_GROUP = "com.example"
    const val TEST_VERSION_NAME = "1.0.0"
    const val TEST_POM_ARTIFACT_ID = "test-artifact"

    @JvmStatic
    @Parameters(name = "{0} with legacyMode={2}")
    fun mavenPublishTargetsToTest() = listOf(
      arrayOf("installArchives", "publishMavenPublicationToLocalRepository", false),
      arrayOf("uploadArchives", "publishMavenPublicationToMavenRepository", false),
      arrayOf("installArchives", "publishMavenPublicationToLocalRepository", true),
      arrayOf("uploadArchives", "publishMavenPublicationToMavenRepository", true)
    )
  }

  @get:Rule val testProjectDir: TemporaryFolder = TemporaryFolder()

  private lateinit var repoFolder: File

  @Before fun setUp() {
    repoFolder = testProjectDir.newFolder("repo")

    File("$FIXTURES/common").listFiles()?.forEach { it.copyRecursively(testProjectDir.root.resolve(it.name)) }
  }

  @Test fun generatesArtifactsAndDocumentationOnJavaProject() {
    setupFixture("passing_java_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertExpectedCommonArtifactsGenerated()
    assertPomContentMatches()
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/TestClass.java", "src/main/java")
  }

  @Test fun generatesArtifactsAndDocumentationOnJavaWithKotlinProject() {
    setupFixture("passing_java_with_kotlin_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertThat(result.task(":dokka")?.outcome).isEqualTo(SUCCESS)
    assertExpectedCommonArtifactsGenerated()
    assertPomContentMatches()
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/TestClass.kt", "src/main/java")
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/JavaTestClass.java", "src/main/java")
  }

  @Test fun generatesArtifactsAndDocumentationOnJavaLibraryProject() {
    setupFixture("passing_java_library_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertExpectedCommonArtifactsGenerated()
    assertPomContentMatches()
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/TestClass.java", "src/main/java")
  }

  @Test fun generatesArtifactsAndDocumentationOnJavaLibraryWithKotlinProject() {
    setupFixture("passing_java_library_with_kotlin_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertThat(result.task(":dokka")?.outcome).isEqualTo(SUCCESS)
    assertExpectedCommonArtifactsGenerated()
    assertPomContentMatches()
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/TestClass.kt", "src/main/java")
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/JavaTestClass.java", "src/main/java")
  }

  @Test fun generatesArtifactsAndDocumentationOnJavaLibraryWithGroovyProject() {
    setupFixture("passing_java_library_with_groovy_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertExpectedCommonArtifactsGenerated()
    assertArtifactGenerated(artifactFileNameWithExtension = "$TEST_POM_ARTIFACT_ID-$TEST_VERSION_NAME-groovydoc.jar")
    assertPomContentMatches()
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/TestClass.groovy", "src/main/groovy")
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/TestClass.java", "src/main/java")
  }

  @Test fun generatesArtifactsAndDocumentationOnAndroidProject() {
    setupFixture("passing_android_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertExpectedCommonArtifactsGenerated(artifactExtension = "aar")
    assertPomContentMatches()
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/TestActivity.java", "src/main/java")
  }

  @Test fun generatesArtifactsAndDocumentationOnAndroidWithKotlinProject() {
    setupFixture("passing_android_with_kotlin_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertThat(result.task(":dokka")?.outcome).isEqualTo(SUCCESS)
    assertExpectedCommonArtifactsGenerated(artifactExtension = "aar")
    assertPomContentMatches()
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/TestActivity.kt", "src/main/java")
    assertSourceJarContainsFile("com/vanniktech/maven/publish/test/JavaTestActivity.java", "src/main/java")
  }

  @Test fun generatesArtifactsAndDocumentationOnKotlinMppProject() {
    assumeFalse(useLegacyMode)

    setupFixture("passing_kotlin_mpp_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info", "--stacktrace")

    assertThat(result.task(":$uploadArchivesTargetTaskName")?.outcome).isEqualTo(SUCCESS)
    assertThat(result.task(":dokka")?.outcome).isEqualTo(SUCCESS)

    assertExpectedCommonArtifactsGenerated(artifactExtension = "module")
    assertPomContentMatches()

    val metadataArtifactId = "$TEST_POM_ARTIFACT_ID-metadata"
    assertExpectedCommonArtifactsGenerated("module", metadataArtifactId)
    assertArtifactGenerated("$metadataArtifactId-$TEST_VERSION_NAME.jar", metadataArtifactId)
    assertPomContentMatches(metadataArtifactId)

    val jvmArtifactId = "$TEST_POM_ARTIFACT_ID-jvm"
    assertExpectedCommonArtifactsGenerated("module", jvmArtifactId)
    assertPomContentMatches(jvmArtifactId)

    val nodejsArtifactId = "$TEST_POM_ARTIFACT_ID-nodejs"
    assertExpectedCommonArtifactsGenerated("module", nodejsArtifactId)
    assertPomContentMatches(nodejsArtifactId)

    val linuxArtifactId = "$TEST_POM_ARTIFACT_ID-linux"
    assertExpectedCommonArtifactsGenerated("module", linuxArtifactId)
    assertArtifactGenerated("$linuxArtifactId-$TEST_VERSION_NAME.klib", linuxArtifactId)
    assertPomContentMatches(linuxArtifactId)
  }

  @Test fun generatesArtifactsAndDocumentationOnGradlePluginProject() {
    setupFixture("passing_java_gradle_plugin_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info", "--stacktrace")

    assertThat(result.task(":$uploadArchivesTargetTaskName")?.outcome).isEqualTo(SUCCESS)
    assertExpectedCommonArtifactsGenerated()
    assertPomContentMatches()

    if (!useLegacyMode) {
      val pluginId = "com.example.test-plugin"
      val artifactId = "$pluginId.gradle.plugin"
      assertExpectedCommonArtifactsGenerated("pom", artifactId, pluginId)
      assertPomContentMatches(artifactId, pluginId)
    }
  }

  @Test fun generatesArtifactsAndDocumentationOnMinimalPomProject() {
    setupFixture("minimal_pom_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertExpectedCommonArtifactsGenerated()
    assertPomContentMatches()
  }

  @Test fun generatesArtifactsAndDocumentationOnOverrideVersionGroupProject() {
    setupFixture("override_version_group_project")

    val result = executeGradleCommands(uploadArchivesTargetTaskName, "--info")

    assertExpectedTasksRanSuccessfully(result)
    assertExpectedCommonArtifactsGenerated(groupId = "com.example2", version = "2.0.0")
    assertPomContentMatches(groupId = "com.example2", version = "2.0.0")
  }

  /**
   * Copies test fixture into temp directory under test.
   */
  private fun setupFixture(fixtureName: String) {
    File("$FIXTURES/$fixtureName").copyRecursively(testProjectDir.root, overwrite = true)
  }

  private fun assertExpectedTasksRanSuccessfully(result: BuildResult) {
    assertThat(result.task(":$uploadArchivesTargetTaskName")?.outcome).isEqualTo(SUCCESS)
    if (useLegacyMode) {
      assertThat(result.task(":$mavenPublishTargetTaskName")).isNull()
    } else {
      assertThat(result.task(":$mavenPublishTargetTaskName")?.outcome).isEqualTo(SUCCESS)
    }
  }

  /**
   * Makes sure common artifacts are generated (POM, javadoc, sources, etc.),
   * no matter what project type is and which plugins are applied.
   */
  private fun assertExpectedCommonArtifactsGenerated(
    artifactExtension: String = "jar",
    artifactId: String = TEST_POM_ARTIFACT_ID,
    groupId: String = TEST_GROUP,
    version: String = TEST_VERSION_NAME
  ) {
    val artifactJar = "$artifactId-$version.$artifactExtension"
    val pomFile = "$artifactId-$version.pom"
    val javadocJar = "$artifactId-$version-javadoc.jar"
    val sourcesJar = "$artifactId-$version-sources.jar"
    assertArtifactGenerated(artifactJar, artifactId, groupId, version)
    assertArtifactGenerated(pomFile, artifactId, groupId, version)
    assertArtifactGenerated(javadocJar, artifactId, groupId, version)
    assertArtifactGenerated(sourcesJar, artifactId, groupId, version)
  }

  private fun assertArtifactGenerated(
    artifactFileNameWithExtension: String,
    artifactId: String = TEST_POM_ARTIFACT_ID,
    groupId: String = TEST_GROUP,
    version: String = TEST_VERSION_NAME
  ) {
    val artifactFolder = artifactFolder(artifactId, groupId, version)

    assertThat(artifactFolder.resolve(artifactFileNameWithExtension)).exists()
    assertThat(artifactFolder.resolve("$artifactFileNameWithExtension.asc")).exists()
  }

  private fun assertPomContentMatches(
    artifactId: String = TEST_POM_ARTIFACT_ID,
    groupId: String = TEST_GROUP,
    version: String = TEST_VERSION_NAME
  ) {
    val artifactFolder = artifactFolder(artifactId, groupId, version)
    val pomFileName = "$artifactId-$version.pom"

    val resolvedPomFile = artifactFolder.resolve(pomFileName)
    // in legacyMode for Android the packaging is written, for all other modes it's currently not written
    // https://github.com/vanniktech/gradle-maven-publish-plugin/issues/82
    val lines = resolvedPomFile.readLines()
    if (lines.contains("  <packaging>aar</packaging>")) {
      resolvedPomFile.writeText("")
      lines.forEach { line ->
        if (line != "  <packaging>aar</packaging>") {
          resolvedPomFile.appendText(line)
          resolvedPomFile.appendText("\n")
        }
      }
    }
    val actualLines = resolvedPomFile.readLines()

    val expectedLines = testProjectDir.root.resolve(EXPECTED_DIR).resolve(pomFileName).readLines()
    assertThat(actualLines).isEqualTo(expectedLines)
  }

  private fun assertSourceJarContainsFile(
    file: String,
    srcRoot: String,
    artifactId: String = TEST_POM_ARTIFACT_ID,
    groupId: String = TEST_GROUP,
    version: String = TEST_VERSION_NAME
  ) {
    val artifactFolder = artifactFolder(artifactId, groupId, version)
    val sourcesJar = ZipFile(artifactFolder.resolve("$artifactId-$version-sources.jar"))
    val entry = sourcesJar.getEntry(file)
    val content = sourcesJar.getInputStream(entry)?.reader()?.buffered()?.readText()

    val expected = testProjectDir.root.resolve(srcRoot).resolve(file)
    val expectedContent = expected.readText()

    assertThat(content).isNotBlank()
    assertThat(content).isEqualTo(expectedContent)
  }

  private fun artifactFolder(artifactId: String, groupId: String, version: String): File {
    val group = groupId.replace(".", "/")
    return repoFolder.resolve(group).resolve(artifactId).resolve(version)
  }

  private fun executeGradleCommands(vararg commands: String) = GradleRunner.create()
      .withProjectDir(testProjectDir.root)
      .withArguments(*commands, "-Ptest.releaseRepository=$repoFolder", "-Ptest.useLegacyMode=$useLegacyMode")
      .withPluginClasspath()
      .build()
}
