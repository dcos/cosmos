package com.mesosphere.util

import com.mesosphere.universe
import java.io.InputStream
import java.io.OutputStream
import scala.util.Failure
import scala.util.Try

object PackageUtil {

  def extractMetadata(packageContent: InputStream): Try[universe.v3.model.Metadata] = {
    Failure(EntryNotFound(AbsolutePath.Root / "metadata.json"))
  }

  def buildPackage(packageContent: OutputStream, metadata: universe.v3.model.Metadata): Unit = ???

  sealed abstract class PackageError(override val getMessage: String) extends RuntimeException
  case class EntryNotFound(path: AbsolutePath) extends PackageError(s"Missing package entry: $path")

}
