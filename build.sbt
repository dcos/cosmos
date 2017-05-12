import com.mesosphere.cosmos.CosmosBuild._
import com.mesosphere.cosmos.Deps

lazy val cosmos = project.in(file("."))
  .settings(sharedSettings)
  .aggregate(
    common,
    integrationTests,
    server,
    testCommon
  )

lazy val common = project.in(file("cosmos-common"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.bijection ++
      Deps.circe ++
      Deps.fastparse ++
      Deps.finch ++
      Deps.findbugs ++
      Deps.guava ++
      Deps.jsonSchema ++
      Deps.mustache ++
      Deps.scalaCheck ++
      Deps.scalaReflect ++
      Deps.scalaTest ++
      Deps.scalaUri ++
      Deps.twitterUtilCore
  )

lazy val server = project.in(file("cosmos-server"))
  .settings(sharedSettings)
  .settings(filterSettings)
  .settings(BuildPlugin.allOneJarSettings("com.mesosphere.cosmos.Cosmos"))
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.aws ++
      Deps.bijectionUtil ++
      Deps.circe ++
      Deps.curator ++
      Deps.logback ++
      Deps.mustache ++
      Deps.scalaUri ++
      Deps.slf4j ++
      Deps.twitterCommons ++
      Deps.twitterServer
  )
  .dependsOn(
    common
  )

lazy val testCommon = project.in(file("cosmos-test-common"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.curator ++
      Deps.mockito ++
      Deps.scalaCheck ++
      Deps.scalaCheckShapeless ++
      Deps.scalaTest
  )
  .dependsOn(
    server
  )

/**
 * Integration test code. Sources are located in the "main" subdirectory so the JAR can be
 * published to Maven repositories with a standard POM.
 */
lazy val integrationTests = project.in(file("cosmos-integration-tests"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    testOptions in IntegrationTest ++= BuildPlugin.itTestOptions(
      (javaHome in run).value,
      // The resources we need are in src/main/resources
      (resourceDirectories in Compile).value,
      // The one-JAR to use is produced by cosmos-server
      (oneJar in server).value,
      (streams in runMain).value
    ),
    // Uses (compile in Compile) instead of (compile in IntegrationTest), the default
    definedTests in IntegrationTest := {
      val frameworkMap = (loadedTestFrameworks in IntegrationTest).value
      val analysis = (compile in Compile).value
      val s = (streams in IntegrationTest).value
      Tests.discover(frameworkMap.values.toList, analysis, s.log)._1
    },
    testGrouping in IntegrationTest := {
      List(
        Tests.Group(
          "Integration tests",
          (definedTests in IntegrationTest).value,
          Tests.InProcess
        )
      )
    }
  )
  .dependsOn(
    testCommon
  )
