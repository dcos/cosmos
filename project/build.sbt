val curatorVersion = "2.9.1"
libraryDependencies ++= Seq(
  "org.apache.curator" % "curator-recipes" % curatorVersion,
  "org.apache.curator" % "curator-test" % curatorVersion
).map(_.excludeAll(
  ExclusionRule("log4j", "log4j"),
  ExclusionRule("org.slf4j", "slf4j-log4j12"),
  ExclusionRule("jline", "jline")
))
