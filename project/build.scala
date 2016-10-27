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
    val bijection = "0.9.2"
    val circe = "0.5.2"
    val curator = "2.9.1"
    val finch = "0.10.0"
    val finchServer = "0.9.1"
    val guava = "16.0.1"
    val jsonSchema = "2.2.6"
    val logback = "1.1.3"
    val mockito = "1.10.19"
    val mustache = "0.9.1"
    val scalaUri = "0.4.11"
    val scalaTest = "2.2.4"
    val scalaCheck = "1.12.5"
    val twitterUtilCore = "6.30.0"
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
      "io.circe" %% "circe-jawn" % V.circe
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

    val finch = Seq(
      "com.github.finagle" %% "finch-core" % V.finch
    ) ++ Seq(
      "com.github.finagle" %% "finch-circe" % V.finch
    ).map(_.excludeAll(
      ExclusionRule("io.circe", "circe-core"),
      ExclusionRule("io.circe", "circe-jawn"),
      ExclusionRule("io.circe", "circe-jackson")
    ))

    val finchServer = Seq(
      "io.github.benwhitehead.finch" %% "finch-server" % V.finchServer
    ).map(_.excludeAll(
      // mustache is pulled in for the core application, so we exclude the transitive version
      // pulled in my twitter-server
      ExclusionRule("com.github.spullara.mustache.java", "compiler")
    ))

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

    val scalaTest = Seq(
      "org.scalatest"       %% "scalatest"        % V.scalaTest     % "test"
    )

    val scalaUri = Seq(
      "com.netaporter" %% "scala-uri" % V.scalaUri
    )

    val twitterUtilCore = Seq(
      "com.twitter" %% "util-core" % V.twitterUtilCore
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
      "finch-server" at "https://storage.googleapis.com/benwhitehead_me/maven/public",
      "Twitter Maven" at "https://maven.twttr.com"
      // Twitter maven has stability issues make sure it's LAST, it's needed for two transitive dependencies
      // [warn]  ::::::::::::::::::::::::::::::::::::::::::::::
      // [warn]  ::          UNRESOLVED DEPENDENCIES         ::
      // [warn]  ::::::::::::::::::::::::::::::::::::::::::::::
      // [warn]  :: com.twitter.common#metrics;0.0.37: not found
      // [warn]  :: org.apache.thrift#libthrift;0.5.0: not found
      // [warn]  ::::::::::::::::::::::::::::::::::::::::::::::
      // [warn]
      // [warn]  Note: Unresolved dependencies path:
      // [warn]          com.twitter.common:metrics:0.0.37
      // [warn]            +- com.twitter:finagle-stats_2.11:6.31.0
      // [warn]            +- io.github.benwhitehead.finch:finch-server_2.11:0.9.0
      // [warn]            +- com.mesosphere.cosmos:cosmos-server_2.11:0.2.0-SNAPSHOT
      // [warn]          org.apache.thrift:libthrift:0.5.0
      // [warn]            +- com.twitter:finagle-thrift_2.11:6.31.0
      // [warn]            +- com.twitter:finagle-zipkin_2.11:6.31.0
      // [warn]            +- com.twitter:twitter-server_2.11:1.16.0
      // [warn]            +- io.github.benwhitehead.finch:finch-server_2.11:0.9.0
      // [warn]            +- com.mesosphere.cosmos:cosmos-server_2.11:0.2.0-SNAPSHOT
      //
    ),

    libraryDependencies ++= Deps.mockito ++ Deps.scalaTest ++ Deps.scalaCheck,

    javacOptions in Compile ++= Seq(
      "-source", "1.8",
      "-target", "1.8",
      "-Xlint:unchecked",
      "-Xlint:deprecation"
    ),

    scalacOptions ++= Seq(
      "-deprecation", // Emit warning and location for usages of deprecated APIs.
      "-encoding", "UTF-8",
      "-explaintypes",
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-target:jvm-1.8",
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      "-Xfuture",
      "-Xlint", // Enable recommended additional warnings.
      "-Yresolve-term-conflict:package",
      "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver.
      "-Ywarn-dead-code",
      "-Ywarn-inaccessible",
      "-Ywarn-infer-any",
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      "-Ywarn-unused",
      "-Ywarn-unused-import",
      "-Ywarn-value-discard"
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
    .aggregate(http, model, json, jsonschema, bijection, render, finch, server)

  lazy val model = Project("cosmos-model", file("cosmos-model"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.scalaUri
        ++ Deps.circeCore
        ++ Deps.twitterUtilCore
    )

  lazy val json = Project("cosmos-json", file("cosmos-json"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.scalaUri
        ++ Deps.circe
    )
    .dependsOn(model)

  lazy val bijection = Project("cosmos-bijection", file("cosmos-bijection"))
    .settings(sharedSettings)
    .settings(
      libraryDependencies ++=
        Deps.bijection
    )
    .dependsOn(model)

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
      json,
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
      json,
      jsonschema,
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
          ++ Deps.curator
          ++ Deps.finchServer
          ++ Deps.logback
          ++ Deps.mustache
          ++ Deps.scalaUri
          ++ Deps.bijectionUtil
    )
    .dependsOn(
      finch % "compile;test->test",
      render % "compile;test->test"
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
