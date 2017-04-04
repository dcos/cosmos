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
      Resolver.mavenLocal,                          // For locally-published dependencies
      "Twitter Maven" at "https://maven.twttr.com"  // For some Twitter dependencies
    ),

    libraryDependencies ++= Deps.mockito ++ Deps.scalaTest ++ Deps.scalaCheck,

    test in (This, Global, This) := (test in Test).value,

    publishArtifact in Test := true,
    publishArtifact in IntegrationTest := true,

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
  ) ++ packageSettings

  val itSettings: Seq[Def.Setting[_]] = BuildPlugin.itSettings("com.mesosphere.cosmos.Cosmos")

  // Adapted from `artifactSetting` in SBT's Defaults.scala
  // Enables publishing of integration test artifacts with the correct classifiers
  def customArtifactSetting(
    a: Artifact,
    classifier: Option[String],
    cOpt: Option[Configuration]
  ): Artifact = {
    val cPart = cOpt flatMap {
      case Compile => None
      case Test    => Some(Artifact.TestsClassifier)
      case c       => Some(c.name)
    }
    val combined = cPart.toList ++ classifier.toList
    if (combined.isEmpty) a.copy(classifier = None, configurations = cOpt.toList) else {
      val classifierString = combined mkString "-"
      val confs = cOpt.toList flatMap { c => Defaults.artifactConfigurations(a, c, classifier) }

      // Begin updated section
      val testsPrefix = Artifact.TestsClassifier + "-"
      val itPrefix = "it-"
      val strippedClassifier =
        if (classifierString.startsWith(testsPrefix)) classifierString.stripPrefix(testsPrefix)
        else if (classifierString.startsWith(itPrefix)) classifierString.stripPrefix(itPrefix)
        else classifierString

      val classifierName = Some(classifierString)
      val classifierType =
        Artifact.classifierTypeMap.getOrElse(strippedClassifier, Artifact.DefaultType)
      a.copy(classifier = classifierName, `type` = classifierType, configurations = confs)
      // End updated section
    }
  }

  def packageSettings: Seq[Def.Setting[_]] = {
    val artifactSetting = artifact := customArtifactSetting(
      artifact.value,
      artifactClassifier.value,
      configuration.?.value
    )

    val artifactSettings = for {
      conf <- Seq(Compile, Test, IntegrationTest)
      task <- Classpaths.defaultPackageKeys
      setting <- inConfig(conf)(inTask(task)(Seq(artifactSetting)))
    } yield setting

    artifactSettings ++ Classpaths.defaultPackageKeys.flatMap { packageTask =>
      addArtifact(artifact in (IntegrationTest, packageTask), packageTask in IntegrationTest)
    }
  }

}
