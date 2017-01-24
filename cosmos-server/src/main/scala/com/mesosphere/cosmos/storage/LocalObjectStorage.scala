package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.http.MediaType
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Fields
import com.twitter.finagle.stats.Stat
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.FuturePool
import com.twitter.util.Try
import io.circe.syntax._
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.util.control.NonFatal

/** Implements an object store backed by a local filesystem.
  *
  * @param storageDir actual storage location for the objects
  * @param scratchDir working directory for storage operations (e.g. writes); should be in the same
  *                   filesystem as `storageDir` so that file moves between them are atomic
  */
final class LocalObjectStorage private(storageDir: Path, scratchDir: Path, stats: StatsReceiver)
  extends ObjectStorage {

  private[this] val pool = FuturePool.interruptibleUnboundedPool

  override def write(
    name: String,
    body: InputStream,
    contentLength: Long,
    contentType: MediaType
  ): Future[Unit] = {
    Stat.timeFuture(stats.stat("write")) {
      pool {
        val absolutePath = storageDir.resolve(name)

        // Create all parent directories
        Files.createDirectories(absolutePath.getParent)

        val (_, bodySize) = writeToFile(
          Map(Fields.ContentType -> contentType.show),
          body,
          absolutePath
        )

        if (contentLength != bodySize) {
          // content length doesn't match the size of the input stream. Delete file and notify
          Files.delete(absolutePath)
          throw new IllegalArgumentException(
            s"Content length $contentLength doesn't equal size of stream $bodySize"
          )
        }
      }
    }
  }

  override def read(name: String): Future[Option[(MediaType, InputStream)]] = {
    Stat.timeFuture(stats.stat("read")) {
      pool {
        readFromFile(storageDir.resolve(name)).map { case (metadata, inputStream) =>
          (
            MediaType.parse(metadata(Fields.ContentType)).get,
            inputStream
          )
        }
      }
    }
  }

  override def delete(name: String): Future[Unit] = {
    Stat.timeFuture(stats.stat("delete")) {
      pool {
        val fullName = storageDir.resolve(name)
        if (Files.exists(fullName)) {
          val pathsToDelete =
            Iterator
              .iterate(fullName)(_.getParent)
              .takeWhile(_ != storageDir)
              .toList

          pathsToDelete.foldLeft(Try(())) { (status, path) =>
            status.map{ _ => Files.deleteIfExists(path); ()}
          }.handle {
            case _: DirectoryNotEmptyException => ()
          }.get()
        }
      }
    }
  }

  override def list(directory: String): Future[LocalObjectStorage.ObjectList] = {
    Stat.timeFuture(stats.stat("list")) {
      pool {
        val absolutePath = storageDir.resolve(directory)
        val stream = Files.newDirectoryStream(absolutePath)

        try {
          val (directories, objects) = stream.asScala.partition(path => Files.isDirectory(path))

          LocalObjectStorage.ObjectList(
            objects.map(objectPath => storageDir.relativize(objectPath).toString)(breakOut),
            directories.map(directoryPath => storageDir.relativize(directoryPath).toString)(breakOut)
          )
        } finally {
          stream.close()
        }
      }
    }
  }

  override def listNext(token: ObjectStorage.ListToken): Future[ObjectStorage.ObjectList] = {
    Future.exception(
      new UnsupportedOperationException(s"Local object store doesn't support paged listing")
    )
  }

  override def getUrl(name: String): Option[Uri] = None

  def getCreationTime(name: String): Future[Option[Long]] = {
    pool {
      val absolutePath = storageDir.resolve(name)
      val attr = Try {
        Files.readAttributes(absolutePath, classOf[BasicFileAttributes])
      }
      attr.toOption.map(_.creationTime().toMillis)
    }
  }

  /*
   * This method creates a file and writes the metadata and body to it. It will lay it out as
   * follow:
   *
   * +-----------------------+----------------+
   * | int(sizeof(metadata)) | json(metadata) |
   * +----------------------------------------+
   * |                  body                  |
   * +----------------------------------------+
   *
   */
  private[this] def writeToFile(
    metadata: Map[String, String],
    body: InputStream,
    absolutePath: Path
  ): (Long, Long) = {
    val scratchPath = Files.createTempFile(scratchDir, "", "")

    val outputStream = new DataOutputStream(
      Files.newOutputStream(
        scratchPath,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING
      )
    )

    val metadataBytes = metadata.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)

    val outputs = try {
      outputStream.writeInt(metadataBytes.length)
      outputStream.write(metadataBytes, 0, metadataBytes.length)

      val headerSize = outputStream.size

      StreamIO.copy(body, outputStream)

      (outputStream.size.toLong, (outputStream.size - headerSize).toLong)
    } finally {
      outputStream.close()
    }

    Try(Files.move(scratchPath, absolutePath, StandardCopyOption.ATOMIC_MOVE))
      .onFailure { _ => val _ = Files.deleteIfExists(scratchPath) }
      .get()

    outputs
  }

  // See writeToFile for information on the file format.
  private[this] def readFromFile(
    absolutePath: Path
  ): Option[(Map[String, String], InputStream)] = {
    val maybeInputStream = try {
      Some(
        new DataInputStream(
          Files.newInputStream(
            absolutePath,
            StandardOpenOption.READ
          )
        )
      )
    } catch {
      case _: NoSuchFileException =>
        None
    }

    maybeInputStream.map { inputStream =>
      try {
        val metadataBytesSize = inputStream.readInt()

        val metadata = {
          val metadataBytes = new Array[Byte](metadataBytesSize)
          inputStream.readFully(metadataBytes)
          decode[Map[String, String]](new String(metadataBytes, StandardCharsets.UTF_8))
        }

        (metadata, inputStream)
      } catch {
        case NonFatal(e) =>
          inputStream.close()
          throw e
      }
    }
  }
}

object LocalObjectStorage {

  /** Create an object store under the given path.
    *
    * @param path the root path for all file operations performed by the store
    */
  def apply(path: Path)(implicit statsReceiver: StatsReceiver): LocalObjectStorage = {
    val storageDir = path.resolve("storage")
    val scratchDir = path.resolve("scratch")
    val stats = statsReceiver.scope(s"LocalObjectStorage($path)")

    // Create needed directories before using them
    Files.createDirectories(storageDir)
    Files.createDirectories(scratchDir)

    new LocalObjectStorage(storageDir, scratchDir, stats)
  }

  case class ObjectList(
    objects: List[String],
    directories: List[String]
  ) extends ObjectStorage.ObjectList {
    val listToken = None
  }

}
