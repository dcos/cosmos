import com.mesosphere.cosmos.CosmosBuild._
import com.mesosphere.cosmos.Deps

lazy val cosmos = project.in(file("."))
  .settings(sharedSettings)
  .aggregate(http, model, json, common, jsonschema, bijection, render, finch, server)

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
        ++ Deps.circeCore
        ++ Deps.circe
        ++ Deps.twitterUtilCore
        ++ Deps.fastparse
        ++ Deps.scalaCheckShapeless
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
  .settings(itSettings)
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
