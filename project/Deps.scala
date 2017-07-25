package com.mesosphere.cosmos

import sbt._

object Deps {

  // APLv2.0
  val bijection = Seq(
    "com.twitter" %% "bijection-core" % V.bijection
  )

  // APLv2.0
  val bijectionUtil = Seq(
    "com.twitter" %% "bijection-util" % V.bijection
  )

  // APLv2.0
  val circe = Seq(
    "io.circe" %% "circe-core" % V.circe,
    "io.circe" %% "circe-testing" % V.circe,
    "io.circe" %% "circe-generic" % V.circe,
    "io.circe" %% "circe-jawn" % V.circe
  )

  // APLv2.0
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

  // MIT
  val fastparse = Seq(
    "com.lihaoyi" %% "fastparse" % V.fastparse
  )

  // APLv2.0
  val twitterServer = Seq(
    "com.twitter" %% "twitter-server" % V.twitterServer
  )

  // APLv2.0
  val finch = Seq(
    "com.github.finagle" %% "finch-core" % V.finch,
    "com.github.finagle" %% "finch-circe" % V.finch
  )

  // LGPLv3.0
  val findbugs = Seq(
    "com.google.code.findbugs" % "jsr305" % "3.0.1"
  )

  // APLv2.0
  val guava = Seq(
    "com.google.guava" % "guava" % V.guava
  )

  // APLv2.0 / LGPLv3.0
  val jsonSchema = Seq(
    "com.github.fge" % "json-schema-validator" % V.jsonSchema
  )

  // EPLv1.0 / LGPLv2.1
  val logback = Seq(
    "ch.qos.logback" % "logback-classic" % V.logback
  )

  // MIT
  val mockito = Seq(
    "org.mockito" % "mockito-core" % V.mockito % "test"
  )

  // APLv2.0
  val mustache = Seq(
    "com.github.spullara.mustache.java" % "compiler" % V.mustache
  )

  // BSD 3-clause
  val scalaCheck = Seq(
    "org.scalacheck" %% "scalacheck" % V.scalaCheck % "test"
  )

  // APLv2.0
  val scalaCheckShapeless = Seq(
    "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % V.scalaCheckShapeless
  )

  // BSD 3-clause
  val scalaReflect = Seq(
    "org.scala-lang" % "scala-reflect" % V.projectScalaVersion
  )

  // APLv2.0
  val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % V.scalaTest % "test"
  )

  // APLv2.0
  val scalaUri = Seq(
    "com.netaporter" %% "scala-uri" % V.scalaUri
  )

  // APLv2.0
  val twitterUtil = Seq(
    "com.twitter" %% "util-core" % V.twitterUtil,
    "com.twitter" %% "finagle-stats" % "6.40.0"
  )

  // APLv2.0
  val aws = Seq(
    "com.amazonaws" % "aws-java-sdk-s3" % V.aws
  ).map(_.excludeAll(
    // Exclude commons-logging; we are using logback
    ExclusionRule("commons-logging", "commons-logging"),
    // Temporary solution for now; the long term solution is to upgrade twitter-server
    ExclusionRule("com.fasterxml.jackson.core")
  ))

  // MIT
  val slf4j = Seq(
    "org.slf4j" % "slf4j-api" % V.slf4j,
    "org.slf4j" % "jul-to-slf4j" % V.slf4j,
    "org.slf4j" % "jcl-over-slf4j" % V.slf4j,
    "org.slf4j" % "log4j-over-slf4j" % V.slf4j
  )

  // APLv2.0
  val twitterCommons = Seq(
    "com.twitter.common" % "util-system-mocks" % "0.0.27",
    "com.twitter.common" % "quantity" % "0.0.31"
  )

}

object V {
  val projectScalaVersion = "2.11.7"
  val projectVersion = "0.4.0"

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
  val twitterUtil = "6.39.0"
  val zookeeper = "3.4.6"
}
