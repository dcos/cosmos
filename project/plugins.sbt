resolvers ++= Seq(
  Resolver.sbtPluginRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.1")

addSbtPlugin("com.github.sdb" % "xsbt-filter" % "0.4")

addSbtPlugin("com.mesosphere" %% "sbt-dcos" % "0.2.0-SNAPSHOT")

// This 
//addSbtPlugin("ohnosequences" % "sbt-s3-resolver" % "0.16.0")
