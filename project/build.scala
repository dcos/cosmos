package com.mesosphere.cosmos

import com.github.retronym.SbtOneJar._
import com.mesosphere.sbt.BuildPlugin
import sbt.Keys._
import sbt._

object CosmosBuild {

  val sharedSettings = BuildPlugin.publishSettings ++ Seq(
    organization := "com.mesosphere.cosmos",
    scalaVersion := V.projectScalaVersion,
    version := V.projectVersion,

    // Required by One-JAR for multi-project builds: https://github.com/sbt/sbt-onejar#requirements
    exportJars := true,

    externalResolvers := Seq(
      Resolver.mavenLocal,
      DefaultMavenRepository,
      "Twitter Maven" at "https://maven.twttr.com"
    ),

    libraryDependencies ++= Deps.mockito ++ Deps.scalaTest ++ Deps.scalaCheck,

    publishArtifact in Test := false,

    // Parallel changes to a shared cluster cause some tests to fail
    parallelExecution in IntegrationTest := false,

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

  BuildPlugin.teamCityReport(scalaVersion = V.projectScalaVersion, version = V.projectVersion)

}
