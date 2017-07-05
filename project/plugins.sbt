resolvers ++= Seq(
  Resolver.sbtPluginRepo("releases"),
  Resolver.sonatypeRepo("snapshots"),
  Resolver.url(
    "bintray-sbt-plugin-releases",
    url("https://dl.bintray.com/content/sbt/sbt-plugin-releases")
  )(Resolver.ivyStylePatterns)
)

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.1")

addSbtPlugin("com.github.sdb" % "xsbt-filter" % "0.4")

addSbtPlugin("com.mesosphere" %% "sbt-dcos" % "0.1.0")

addSbtPlugin("org.jetbrains.teamcity.plugins" % "sbt-teamcity-logger" % "0.4.0")
