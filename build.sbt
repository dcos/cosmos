import com.mesosphere.cosmos.CosmosBuild._
import com.mesosphere.cosmos.CosmosIntegrationTestServer
import com.mesosphere.cosmos.Deps

lazy val cosmos = project.in(file("."))
  .settings(sharedSettings)
  .aggregate(
    bijection,
    common,
    finch,
    http,
    integrationTests,
    json,
    jsonschema,
    model,
    render,
    server,
    testCommon
  )

lazy val common = project.in(file("cosmos-common"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.scalaReflect
        ++ Deps.scalaTest
        ++ Deps.scalaCheck
  )
  .dependsOn(
    json % "compile;test->test"
  )

lazy val model = project.in(file("cosmos-model"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.scalaUri
        ++ Deps.circe
        ++ Deps.twitterUtilCore
        ++ Deps.fastparse
  )

lazy val json = project.in(file("cosmos-json"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.scalaUri
        ++ Deps.circe
  )
  .dependsOn(
    model % "compile;test->test"
  )

lazy val bijection = project.in(file("cosmos-bijection"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.bijection
  )
  .dependsOn(
    model % "compile;test->test"
  )

lazy val http = project.in(file("cosmos-http"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.findbugs
        ++ Deps.guava
        ++ Deps.twitterUtilCore
  )

lazy val finch = project.in(file("cosmos-finch"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.finch
  )
  .dependsOn(
    json % "compile;test->test",
    http % "compile;test->test"
  )

lazy val jsonschema = project.in(file("cosmos-jsonschema"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.circe
        ++ Deps.jsonSchema
  )

lazy val render = project.in(file("cosmos-render"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.mustache
  )
  .dependsOn(
    json % "compile;test->test",
    jsonschema % "compile;test->test",
    bijection % "compile;test->test"
  )

lazy val server = project.in(file("cosmos-server"))
  .settings(sharedSettings)
  .settings(filterSettings)
  // The sbt-dcos plugin provides these one-JAR settings, but they are combined with the
  // integration test settings; since we need to customize the latter, we have to handle these too
  .settings(oneJarSettings)
  .settings(mainClass in oneJar := Some("com.mesosphere.cosmos.Cosmos"))
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.circe
        ++ Deps.twitterServer
        ++ Deps.curator
        ++ Deps.logback
        ++ Deps.mustache
        ++ Deps.scalaUri
        ++ Deps.bijectionUtil
        ++ Deps.aws
        ++ Deps.slf4j
        ++ Deps.twitterCommons
  )
  .dependsOn(
    finch % "compile;test->test",
    render % "compile;test->test",
    common % "compile;test->test"
  )

lazy val testCommon = project.in(file("cosmos-test-common"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.curator
        ++ Deps.mockito
        ++ Deps.scalaCheck
        ++ Deps.scalaCheckShapeless
        ++ Deps.scalaTest
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
    // The sbt-dcos plugin provides this, but we need to customize it
    testOptions in IntegrationTest ++= {
      lazy val itServer = new CosmosIntegrationTestServer(
        (javaHome in run).value.map(_.getCanonicalPath),
        // The resources we need are in src/main/resources, not src/it/resources
        (resourceDirectories in Compile).value,
        // The one-JAR to use is produced by cosmos-server
        (oneJar in server).value
      )

      Seq(
        Tests.Setup(() => itServer.setup((streams in runMain).value.log)),
        Tests.Cleanup(() => itServer.cleanup())
      )
    },
    // Uses (compile in Compile) instead of (compile in IntegrationTest), the default
    definedTests in IntegrationTest := {
      val frameworkMap = (loadedTestFrameworks in IntegrationTest).value
      val analysis = (compile in Compile).value
      val s = (streams in IntegrationTest).value
      Tests.discover(frameworkMap.values.toList, analysis, s.log)._1
    }
  )
  .dependsOn(
    testCommon
  )
