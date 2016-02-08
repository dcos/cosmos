package com.mesosphere.cosmos

import java.nio.file

import com.mesosphere.cosmos.Flaggables._
import com.twitter.app.GlobalFlag
import com.netaporter.uri.Uri

object dcosUri extends GlobalFlag[Uri](
  s"The URI where the DCOS Admin Router is located. If this flag is set, " +
    s"${mesosMasterUri.name} and ${marathonUri.name} will be ignored"
)

object marathonUri extends GlobalFlag[Uri](
  Uri.parse("http://master.mesos:8080"),
  "The URI where marathon can be found"
)

object mesosMasterUri extends GlobalFlag[Uri](
  Uri.parse("http://leader.mesos:5050"),
  "The URI where the leading Mesos master can be found"
)

// TODO: Make this application state instead of config parameter
object universeBundleUri extends GlobalFlag[Uri](
  Uri.parse("https://github.com/mesosphere/universe/archive/version-2.x.zip"),
  "uri of universe bundle"
)

object dataDir extends GlobalFlag[file.Path](
  file.Paths.get("/var/lib/cosmos"),
  help = "Root directory for all cosmos runtime "
)
