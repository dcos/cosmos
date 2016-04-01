package com.mesosphere.cosmos

import java.nio.file.{Files, NoSuchFileException, Path}

import com.twitter.util.Try

object FilesystemPackageMetadataStore extends PackageMetadataStore {

  def readFile(path: Path): Option[Array[Byte]] = {
    Try(Some(Files.readAllBytes(path)))
      .handle {
        case e: NoSuchFileException => None
        // TODO: This is not the correct error. We return None if the file doesn't exists.
        case e => throw new PackageFileMissing(path.toString, e)
      }
      .get()
  }

}
