package com.mesosphere.cosmos

import com.github.retronym.SbtOneJar._
import scoverage.ScoverageKeys._
import sbtfilter.Plugin._
import sbt.Keys._
import sbt._

object CosmosBuild extends Build {

  lazy val projectScalaVersion = "2.11.7"
  lazy val projectVersion = "0.3.0-SNAPSHOT"

  object V {
    val aws = "1.11.63"
    val bijection = "0.9.4"
    val circe = "0.6.1"
    val curator = "2.11.1"
    val fastparse = "0.4.1"
    val finch = "0.11.1"
    val guava = "16.0.1"
    val jsonSchema = "2.2.6"
    val logback = "1.1.3"
    val mockito = "1.10.19"
    val mustache = "0.9.1"
    val scalaCheck = "1.13.4"
    val scalaCheckShapeless = "1.1.3"
    val scalaTest = "3.0.1"
    val scalaUri = "0.4.11"
    val slf4j = "1.7.10"
    val twitterServer = "1.25.0"
    val twitterUtilCore = "6.39.0"
    val zookeeper = "3.4.6"
  }

  object Deps {

    val bijection = Seq(
      "com.twitter" %% "bijection-core" % V.bijection
    )

    val bijectionUtil = Seq(
      "com.twitter" %% "bijection-util" % V.bijection
    )

    val circeCore = Seq(
      "io.circe" %% "circe-core" % V.circe
    )

    val circe = circeCore ++ Seq(
      "io.circe" %% "circe-generic" % V.circe,
      "io.circe" %% "circe-jawn" % V.circe,
      "io.circe" %% "circe-testing" % V.circe
    )

    val curator = Seq(
      "org.apache.curator" % "curator-recipes" % V.curator,
      "org.apache.curator" % "curator-test" % V.curator % "test"
    ).map(_.excludeAll(
      // Exclude log4j and slf4j-log4j12 because we're using logback as our logging backend.
      // exclude jmx items since we're only using the curator client, not it's server
      // exclude jline from zk since we're not using it's console
      ExclusionRule("log4j", "log4j"),
      ExclusionRule("org.slf4j", "slf4j-log4j12"),
      ExclusionRule("com.sun.jdmk", "jmxtools"),
      ExclusionRule("com.sun.jmx", "jmxri"),
      ExclusionRule("javax.jms", "jms"),
      ExclusionRule("jline", "jline")
    ))

    val fastparse = Seq(
      "com.lihaoyi" %% "fastparse" % V.fastparse
    )

    val twitterServer = Seq(
      "com.twitter" %% "twitter-server" % V.twitterServer
    )

    val finch = Seq(
      "com.github.finagle" %% "finch-core" % V.finch,
      "com.github.finagle" %% "finch-circe" % V.finch
    )

    val guava = Seq(
      "com.google.guava" % "guava" % V.guava,
      "com.google.code.findbugs" % "jsr305" % "3.0.1"
    )

    val jsonSchema = Seq(
      "com.github.fge" % "json-schema-validator" % V.jsonSchema
    )

    val logback = Seq(
      "ch.qos.logback" % "logback-classic" % V.logback
    )

    val mockito = Seq(
      "org.mockito" % "mockito-core" % V.mockito % "test"
    )

    val mustache = Seq(
      "com.github.spullara.mustache.java" % "compiler" % V.mustache
    )

    val scalaCheck = Seq(
      "org.scalacheck" %% "scalacheck" % V.scalaCheck % "test"
    )

    val scalaCheckShapeless = Seq(
      "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % V.scalaCheckShapeless % "test"
    )

    val scalaReflect = Seq(
      "org.scala-lang" % "scala-reflect" % projectScalaVersion
    )

    val scalaTest = Seq(
      "org.scalatest" %% "scalatest" % V.scalaTest % "test"
    )

    val scalaUri = Seq(
      "com.netaporter" %% "scala-uri" % V.scalaUri
    )

    val twitterUtilCore = Seq(
      "com.twitter" %% "util-core" % V.twitterUtilCore
    )

    val aws = Seq(
      "com.amazonaws" % "aws-java-sdk-s3" % V.aws
    ).map(_.excludeAll(
      // Exclude commons-logging; we are using logback
      ExclusionRule("commons-logging", "commons-logging"),
      // Temporary solution for now; the long term solution is to upgrade twitter-server
      ExclusionRule("com.fasterxml.jackson.core")
    ))

    val slf4j = Seq(
      "org.slf4j" % "slf4j-api" % V.slf4j,
      "org.slf4j" % "jul-to-slf4j" % V.slf4j,
      "org.slf4j" % "jcl-over-slf4j" % V.slf4j,
      "org.slf4j" % "log4j-over-slf4j" % V.slf4j
    )

    val twitterCommons = Seq(
      "com.twitter.common" % "util-system-mocks" % "0.0.27",
      "com.twitter.common" % "quantity" % "0.0.31"
    )

  }

  val teamcityVersion = sys.env.get("TEAMCITY_VERSION")

  val extraSettings = Defaults.coreDefaultSettings

  val sharedSettings = extraSettings ++ Seq(
    organization := "com.mesosphere.cosmos",
    scalaVersion := projectScalaVersion,
    version := projectVersion,

    exportJars := true,

    externalResolvers := Seq(
      Resolver.mavenLocal,
      DefaultMavenRepository,
      "Twitter Maven" at "https://maven.twttr.com"
    ),

    libraryDependencies ++= Deps.mockito ++ Deps.scalaTest ++ Deps.scalaCheck,

    javacOptions in Compile ++= Seq(
      "-source", "1.8",
      "-target", "1.8",
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    ),

    scalacOptions ++= Seq(
      "-deprecation",            // Emit warning and location for usages of deprecated APIs.
      "-encoding", "UTF-8",      // Specify character encoding used by source files.
      "-explaintypes",           // Explain type errors in more detail.
      "-feature",                // Emit warning for usages of features that should be imported explicitly.
      "-target:jvm-1.8",         // Target platform for object files.
      "-unchecked",              // Enable additional warnings where generated code depends on assumptions.
      "-Xfatal-warnings",        // Fail the compilation if there are any warnings.
      "-Xfuture",                // Turn on future language features.
      "-Xlint",                  // Enable or disable specific warnings
      "-Ywarn-adapted-args",     // Warn if an argument list is modified to match the receiver.
      "-Ywarn-dead-code",        // Warn when dead code is identified.
      "-Ywarn-inaccessible",     // Warn about inaccessible types in method signatures.
      "-Ywarn-infer-any",        // Warn when a type argument is inferred to be `Any`.
      "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
      "-Ywarn-nullary-unit",     // Warn when nullary methods return Unit.
      "-Ywarn-numeric-widen",    // Warn when numerics are widened.
      "-Ywarn-unused",           // Warn when local and private vals, vars, defs, and types are unused.
      "-Ywarn-unused-import",    // Warn when imports are unused.
      "-Ywarn-value-discard"     // Warn when non-Unit expression results are unused.
    ),

    scalacOptions in (Compile, console) ~= (_ filterNot (_ == "-Ywarn-unused-import")),

    scalacOptions in (Test, console) ~= (_ filterNot (_ == "-Ywarn-unused-import")),

    // Publishing options:
    publishMavenStyle := true,

    pomIncludeRepository := { x => false },

    publishArtifact in Test := false,

    parallelExecution in ThisBuild := false,

    parallelExecution in Test := false,

    fork := false,

    cancelable in Global := true,

    coverageOutputTeamCity := teamcityVersion.isDefined,

    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },

    pomExtra :=
        <url>https://dcos.io</url>
        <licenses>
          <license>
            <name>Apache License Version 2.0</name>
            <url>https://github.com/dcos/cosmos/blob/master/LICENSE.txt</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>https://github.com/dcos/cosmos.git</url>
          <connection>scm:git:https://github.com/dcos/cosmos.git</connection>
        </scm>
        <developers>
          <developer>
            <name>Ben Whitehead</name>
          </developer>
          <developer>
            <name>Charles Ruhland</name>
          </developer>
          <developer>
            <name>José Armando García Sancio</name>
          </developer>
          <developer>
            <name>Tamar Ben-Shachar</name>
          </developer>
        </developers>
  ) ++ org.scalastyle.sbt.ScalastylePlugin.projectSettings

  val scalastyleItSettings = {
    import org.scalastyle.sbt.ScalastylePlugin._

    Seq(
      (scalastyleConfig in IntegrationTest) := (scalastyleConfig in scalastyle).value,
      (scalastyleConfigUrl in IntegrationTest) := None,
      (scalastyleConfigUrlCacheFile in IntegrationTest) := "scalastyle-it-config.xml",
      (scalastyleConfigRefreshHours in IntegrationTest) := (scalastyleConfigRefreshHours in scalastyle).value,
      (scalastyleTarget in IntegrationTest) := target.value / "scalastyle-it-result.xml",
      (scalastyleFailOnError in IntegrationTest) := (scalastyleFailOnError in scalastyle).value,
      (scalastyleSources in IntegrationTest) := Seq((scalaSource in IntegrationTest).value)
    ) ++ Project.inConfig(IntegrationTest)(rawScalastyleSettings())
  }

  private lazy val cosmosIntegrationTestServer = settingKey[CosmosIntegrationTestServer]("cosmos-it-server")

  val itSettings = Defaults.itSettings ++ Seq(
    test in IntegrationTest <<= (test in IntegrationTest).dependsOn(oneJar),
    testOnly in IntegrationTest <<= (testOnly in IntegrationTest).dependsOn(oneJar),
    cosmosIntegrationTestServer in IntegrationTest := new CosmosIntegrationTestServer(
      (javaHome in run).value.map(_.getCanonicalPath),
      (resourceDirectories in IntegrationTest).value,
      (artifactPath in oneJar).value
    ),
    testOptions in IntegrationTest += Tests.Setup(() =>
      (cosmosIntegrationTestServer in IntegrationTest).value.setup((streams in runMain).value.log)
    ),
    testOptions in IntegrationTest += Tests.Cleanup(() =>
      (cosmosIntegrationTestServer in IntegrationTest).value.cleanup()
    )
  ) ++ scalastyleItSettings

  lazy val cosmos = Project("cosmos", file("."))
    .settings(sharedSettings)
    .aggregate(http, model, json, common, jsonschema, bijection, render, finch, server)

  lazy val common = Project("cosmos-common", file("cosmos-common"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.scalaReflect
        ++ Deps.scalaTest
        ++ Deps.scalaCheck
    )
    .dependsOn(
      json % "compile;test->test"
    )

  lazy val model = Project("cosmos-model", file("cosmos-model"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.scalaUri
        ++ Deps.circeCore
        ++ Deps.twitterUtilCore
        ++ Deps.fastparse
        ++ Deps.scalaCheckShapeless
    )

  lazy val json = Project("cosmos-json", file("cosmos-json"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.scalaUri
        ++ Deps.circe
    )
    .dependsOn(
      model % "compile;test->test"
    )

  lazy val bijection = Project("cosmos-bijection", file("cosmos-bijection"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.bijection
    )
    .dependsOn(
      model % "compile;test->test"
    )

  lazy val http = Project("cosmos-http", file("cosmos-http"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.guava
          ++ Deps.twitterUtilCore
    )

  lazy val finch = Project("cosmos-finch", file("cosmos-finch"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.finch
    )
    .dependsOn(
      json % "compile;test->test",
      http % "compile;test->test"
    )

  lazy val jsonschema = Project("cosmos-jsonschema", file("cosmos-jsonschema"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.circe
          ++ Deps.jsonSchema
    )

  lazy val render = Project("cosmos-render", file("cosmos-render"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.mustache
    )
    .dependsOn(
      json % "compile;test->test",
      jsonschema % "compile;test->test",
      bijection % "compile;test->test"
    )

  lazy val server = Project("cosmos-server", file("cosmos-server"))
    .configs(IntegrationTest extend Test)
    .settings(itSettings)
    .settings(sharedSettings)
    .settings(oneJarSettings)
    .settings(mainClass in oneJar := Some("com.mesosphere.cosmos.Cosmos"))
    .settings(filterSettings)
    .settings(
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

  //////////////////////////////////////////////////////////////////////////////
  // BUILD TASKS
  //////////////////////////////////////////////////////////////////////////////

  teamcityVersion.foreach { _ =>
      // add some info into the teamcity build context so that they can be used
      // by later steps
      reportParameter("SCALA_VERSION", projectScalaVersion)
      reportParameter("PROJECT_VERSION", projectVersion)
  }

  def reportParameter(key: String, value: String): Unit = {
    println(s"##teamcity[setParameter name='env.SBT_$key' value='$value']")
    println(s"##teamcity[setParameter name='system.sbt.$key' value='$value']")
  }
}
