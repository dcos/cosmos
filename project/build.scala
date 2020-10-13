package com.mesosphere.cosmos

import com.mesosphere.sbt.BuildPlugin
import com.mesosphere.sbt.Scalastyle
import sbt.Keys._
import sbt._

object CosmosBuild {

  val sharedSettings: Seq[Def.Setting[_]] = BuildPlugin.publishSettings ++ Seq(
    organization := "com.mesosphere.cosmos",
    scalaVersion := V.projectScalaVersion,
    version := V.projectVersion,

    Scalastyle.scalastyleConfig in Global :=
      Some((baseDirectory in ThisBuild).value / "scalastyle-config.xml"),

    // Required by One-JAR for multi-project builds: https://github.com/sbt/sbt-onejar#requirements
    exportJars := true,

    resolvers ++= Seq(
      "Twitter Maven" at "https://maven.twttr.com"  // For some Twitter dependencies
    ),

    test in (This, Global, This) := (test in Test).value,

    // Parallel changes to a shared cluster cause some tests to fail
    parallelExecution in IntegrationTest := false,

    publishMavenStyle := true,

    //Mesosphere users, ensure MAWS credentials are set to [default] in ~/.aws/config
    publishTo := {
      //val repo = "s3://downloads.mesosphere.io"
      //TODO@kjoshi switch this to production before release!
      val repo = "s3://kjoshi-dev.s3.us-east-1.amazonaws.com"
      if (version.value.endsWith("-SNAPSHOT"))
          Some("Mesosphere Public Snapshot Repo (S3)" at s"${repo}/maven-snapshots")
      else
          Some("Mesosphere Public Snapshot Repo (S3)" at s"${repo}/maven")
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
  )
}
