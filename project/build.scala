package com.mesosphere.cosmos

import com.github.retronym.SbtOneJar._
import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys._

object CosmosBuild {

  val teamcityVersion = sys.env.get("TEAMCITY_VERSION")

  val extraSettings = Defaults.coreDefaultSettings

  val sharedSettings = extraSettings ++ Seq(
    organization := "com.mesosphere.cosmos",
    scalaVersion := V.projectScalaVersion,
    version := V.projectVersion,

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

    scalacOptions in (Compile, doc) += "-no-link-warnings",

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
    (test in IntegrationTest).set((test in IntegrationTest).dependsOn(oneJar), NoPosition),
    (testOnly in IntegrationTest).set((testOnly in IntegrationTest).dependsOn(oneJar), NoPosition),
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

  //////////////////////////////////////////////////////////////////////////////
  // BUILD TASKS
  //////////////////////////////////////////////////////////////////////////////

  teamcityVersion.foreach { _ =>
      // add some info into the teamcity build context so that they can be used
      // by later steps
      reportParameter("SCALA_VERSION", V.projectScalaVersion)
      reportParameter("PROJECT_VERSION", V.projectVersion)
  }

  def reportParameter(key: String, value: String): Unit = {
    println(s"##teamcity[setParameter name='env.SBT_$key' value='$value']")
    println(s"##teamcity[setParameter name='system.sbt.$key' value='$value']")
  }
}
