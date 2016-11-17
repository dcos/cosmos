package com.mesosphere.cosmos

import java.nio.file
import com.mesosphere.cosmos.model.ZooKeeperUri
import com.netaporter.uri.Uri
import com.twitter.app.Flaggable

object Flaggables {
  implicit val flagOfUri: Flaggable[Uri] = Flaggable.mandatory(Uri.parse)
  implicit val flagOfFilePath: Flaggable[file.Path] = Flaggable.mandatory(s => file.Paths.get(s))

  implicit val flagOfZooKeeperUri: Flaggable[ZooKeeperUri] =
    Flaggable.mandatory(s => ZooKeeperUri.parse(s).get())

  implicit val flagOfOptionObjectStorageUri: Flaggable[Option[ObjectStorageUri]] =
    Flaggable.mandatory(s => Some(ObjectStorageUri.parse(s).get()))
}
