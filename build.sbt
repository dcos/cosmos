import com.mesosphere.cosmos.CosmosBuild._
import com.mesosphere.cosmos.Deps

val common = project.in(file("cosmos-common"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.apacheCommons ++
      Deps.bijection ++
      Deps.circe ++
      Deps.curator ++
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
      Deps.twitterUtil
  )

val server = project.in(file("cosmos-server"))
  .settings(sharedSettings)
  .settings(filterSettings)
  .settings(BuildPlugin.allOneJarSettings("com.mesosphere.cosmos.Cosmos"))
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.bijectionUtil ++
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

val testCommon = project.in(file("cosmos-test-common"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    libraryDependencies ++=
      Deps.curator ++
      Deps.mockito ++
      Deps.scalaCheckShapeless
  )
  .dependsOn(
    server
  )

/**
 * Integration test code. Sources are located in the "main" subdirectory so the JAR can be
 * published to Maven repositories with a standard POM.
 */
val integrationTests = project.in(file("cosmos-integration-tests"))
  .settings(sharedSettings)
  .settings(
    name := baseDirectory.value.name,
    testOptions in IntegrationTest ++= BuildPlugin.itTestOptions(
      javaHomeValue = (javaHome in run).value,
      // The one-JAR to use is produced by cosmos-server
      oneJarValue = (oneJar in server).value,
      // No additional properties needed for these tests
      additionalProperties = Nil,
      streamsValue = (streams in runMain).value
    )
  )
  .dependsOn(
    testCommon
  )

val cosmos = project.in(file("."))
  .settings(sharedSettings)
  .aggregate(
    common,
    integrationTests,
    server,
    testCommon
  )
