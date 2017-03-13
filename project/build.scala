package com.mesosphere.cosmos

import com.github.retronym.SbtOneJar._
import com.mesosphere.sbt.BuildPlugin
import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys._

object CosmosBuild {

  val teamcityVersion = sys.env.get("TEAMCITY_VERSION")

  val extraSettings = BuildPlugin.publishSettings ++ Defaults.coreDefaultSettings

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

    publishArtifact in Test := false,

    parallelExecution in ThisBuild := false,

    parallelExecution in Test := false,

    fork := false,

    cancelable in Global := true,

    coverageOutputTeamCity := teamcityVersion.isDefined,

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
