package com.mesosphere.cosmos

import java.nio.file.Path

import cats.data.Xor
import com.twitter.io.Charsets
import io.circe.Json
import io.circe.parse._

trait PackageMetadataStore {

  def readFile(path: Path): Option[Array[Byte]]

}

object PackageMetadataStore {

  implicit final class PackageMetadataStoreOps(val ms: PackageMetadataStore) extends AnyVal {

    def readUtf8File(path: Path): Option[String] = {
      ms.readFile(path).map(new String(_, Charsets.Utf8))
    }

    def parseJsonFile(path: Path): Option[Json] = {
      readUtf8File(path).map { content =>
        parse(content) match {
          case Xor.Left(err) => throw PackageFileNotJson(path.getFileName.toString, err.message)
          case Xor.Right(json) => json
        }
      }
    }

  }

}
