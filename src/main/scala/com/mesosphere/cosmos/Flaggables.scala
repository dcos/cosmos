package com.mesosphere.cosmos

import java.nio.file
import com.netaporter.uri.Uri
import com.twitter.app.Flaggable

object Flaggables {
  implicit val flagOfUri: Flaggable[Uri] = Flaggable.mandatory(Uri.parse)
  implicit val flagOfFilePath: Flaggable[file.Path] = Flaggable.mandatory(s => file.Paths.get(s))
}
