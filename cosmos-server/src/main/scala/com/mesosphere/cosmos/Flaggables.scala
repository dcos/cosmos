package com.mesosphere.cosmos

import com.mesosphere.cosmos.model.ZooKeeperUri
import com.netaporter.uri.Uri
import com.twitter.app.Flaggable
import com.twitter.conversions.storage._
import com.twitter.util.StorageUnit
import java.nio.file

object Flaggables {
  implicit val flagOfUri: Flaggable[Uri] = Flaggable.mandatory(Uri.parse)
  implicit val flagOfFilePath: Flaggable[file.Path] = Flaggable.mandatory(s => file.Paths.get(s))

  implicit val flagOfZooKeeperUri: Flaggable[ZooKeeperUri] =
    Flaggable.mandatory(s => ZooKeeperUri.parse(s).getOrThrow)

  implicit def flagOfOption[T](implicit flaggable: Flaggable[T]): Flaggable[Option[T]] = {
    Flaggable.mandatory { string =>
      string.trim match {
        case "" => None // Assume that empty string means unset or none
        case value => Some(flaggable.parse(value))
      }
    }
  }

  implicit val flagOfStorageUnit: Flaggable[StorageUnit] = Flaggable.mandatory { string =>
    string.toInt.megabytes
  }
}
