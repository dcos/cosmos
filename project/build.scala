package com.mesosphere.cosmos

import com.mesosphere.sbt.BuildPlugin
import com.mesosphere.sbt.Scalastyle
import sbt.Keys._
import sbt._

object CosmosBuild {

  val sharedSettings = BuildPlugin.publishSettings ++ Seq(
    organization := "com.mesosphere.cosmos",
    scalaVersion := V.projectScalaVersion,
    version := V.projectVersion,

    Scalastyle.scalastyleConfig in Global :=
      Some((baseDirectory in ThisBuild).value / "scalastyle-config.xml"),

    // Required by One-JAR for multi-project builds: https://github.com/sbt/sbt-onejar#requirements
    exportJars := true,

    externalResolvers := Seq(
      Resolver.mavenLocal,
      DefaultMavenRepository,
      "Twitter Maven" at "https://maven.twttr.com"
    ),

    libraryDependencies ++= Deps.mockito ++ Deps.scalaTest ++ Deps.scalaCheck,

    test in (This, Global, This) := (test in Test).value,

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
  )

  val itSettings = BuildPlugin.itSettings("com.mesosphere.cosmos.Cosmos")

}
