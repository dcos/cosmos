organization := "mesosphere"

name := "cosmos"

version := "0.1"

scalaVersion := "2.11.7"

val finchVersion = "0.9.2"

libraryDependencies ++= Seq(
  "com.github.finagle" %% "finch-core" % finchVersion,
  "com.github.finagle" %% "finch-circe" % finchVersion,
  "com.twitter" %% "util-collection" % "6.27.0",
  "com.github.finagle" %% "finch-test" % finchVersion % "test",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)
