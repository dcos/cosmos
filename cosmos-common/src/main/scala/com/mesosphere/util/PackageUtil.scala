package com.mesosphere.util

import com.mesosphere.universe
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import scala.util.Try

object PackageUtil {

  val MetadataPath: RelativePath = RelativePath("metadata.json")

  def extractMetadata(packageContent: InputStream): Try[universe.v3.model.Metadata] = {
    Try {
      val zipIn = new ZipInputStream(packageContent)

      val hasMetadata = Iterator.continually(Option(zipIn.getNextEntry))
        .takeWhile(_.isDefined)
        .flatten
        .exists(entry => RelativePath(entry.getName) == MetadataPath)

      throw if (hasMetadata) InvalidEntry(MetadataPath) else MissingEntry(MetadataPath)
    }
  }

  def buildPackage(packageContent: OutputStream, metadata: universe.v3.model.Metadata): Unit = ???

  sealed abstract class PackageError(override val getMessage: String) extends RuntimeException

  case class MissingEntry(path: RelativePath)
    extends PackageError(s"Package entry not found: $path")

  case class InvalidEntry(path: RelativePath)
    extends PackageError(s"Package entry was not JSON or did not match the expected schema: $path")

}
