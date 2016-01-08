package com.mesosphere.cosmos


import java.nio.file

import com.twitter.app.GlobalFlag
import com.netaporter.uri.Uri

object dcosHost extends GlobalFlag[Uri](
  Uri.parse("http://master.mesos"),
  "The URI where the DCOS Admin Router"
)(Flaggables.flagOfUri)

// TODO: Make this application state instead of config parameter
object universeBundleUri extends GlobalFlag[Uri](
  Uri.parse("https://github.com/mesosphere/universe/archive/version-2.x.zip"),
  "uri of universe bundle"
)(Flaggables.flagOfUri)

// TODO: Make this application state instead of config parameter
object universeCacheDir extends GlobalFlag[file.Path](
  file.Paths.get(System.getProperty("java.io.tmpdir")),
  help = "directory for local universe cache"
)(Flaggables.flagOfFilePath)
