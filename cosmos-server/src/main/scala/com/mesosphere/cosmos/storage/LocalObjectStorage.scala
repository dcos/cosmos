package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.http.MediaType
import com.netaporter.uri.Uri
import com.twitter.finagle.stats.Stat
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Reader
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.FuturePool
import io.circe.jawn.decode
import io.circe.syntax._
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.util.control.NonFatal


final class LocalObjectStorage(
  path: Path
)(
  implicit statsReceiver: StatsReceiver
) extends ObjectStorage {
  private[this] val pool = FuturePool.interruptibleUnboundedPool

  private[this] val stats = statsReceiver.scope(s"LocalObjectStorage($path)")

  override def write(
    name: String,
    body: InputStream,
    contentLength: Long,
    contentType: MediaType
  ): Future[Unit] = {
    Stat.timeFuture(stats.stat("write")) {
      pool {
        val absolutePath = path.resolve(name)

        // Create all parent directories
        Files.createDirectories(absolutePath.getParent)

        val (_, bodySize) = writeToFile(
          Map(LocalObjectStorage.contentTypeKey -> contentType.show),
          body,
          absolutePath
        )

        if (contentLength != bodySize) {
          // content length doesn't match the size of the input stream. Delete file and notify
          Files.delete(absolutePath)
          throw new IllegalArgumentException(s"Content length $contentLength doesn't equal size of stream $bodySize")
        }
      }
    }
  }

  override def read(name: String): Future[Option[(MediaType, Reader)]] = {
    Stat.timeFuture(stats.stat("read")) {
      pool {
        readFromFile(path.resolve(name)).map { case (metadata, reader) =>
          (
            MediaType.parse(metadata(LocalObjectStorage.contentTypeKey)).get,
            reader
          )
        }
      }
    }
  }

  override def delete(name: String): Future[Unit] = {
    Stat.timeFuture(stats.stat("delete")) {
      pool {
        // Drop the boolean. We want to have the same semantic as S3 which succeeds in either case.
        val _ = Files.deleteIfExists(path.resolve(name))

        // TODO: To have the same semantic as S3 we need to delete all empty parent directories
      }
    }
  }

  override def list(directory: String): Future[LocalObjectStorage.ObjectList] = {
    Stat.timeFuture(stats.stat("list")) {
      pool {
        val absolutePath = path.resolve(directory)
        val stream = Files.newDirectoryStream(absolutePath)

        try {
          val (directories, objects) = stream.asScala.partition(path => Files.isDirectory(path))

          LocalObjectStorage.ObjectList(
            objects.map(objectPath => path.relativize(objectPath).toString)(breakOut),
            directories.map(directoryPath => path.relativize(directoryPath).toString)(breakOut)
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

    val metadataBytes = metadata.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)

    val outputStream = new DataOutputStream(
      Files.newOutputStream(
        absolutePath,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING
      )
    )

    try {
      outputStream.writeInt(metadataBytes.length)
      outputStream.write(metadataBytes, 0, metadataBytes.length)

      val headerSize = outputStream.size

      StreamIO.copy(body, outputStream)

      (outputStream.size.toLong, (outputStream.size - headerSize).toLong)
    } finally {
      outputStream.close()
    }
  }

  // See writeToFile for information on the file format.
  private[this] def readFromFile(
    absolutePath: Path
  ): Option[(Map[String, String], Reader)] = {
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
      case e: NoSuchFileException =>
        None
    }

    maybeInputStream.map { inputStream =>
      try {
        val metadataBytesSize = inputStream.readInt()

        val metadata = {
          val metadataBytes = new Array[Byte](metadataBytesSize)
          inputStream.readFully(metadataBytes)
          decode[Map[String, String]](
            new String(metadataBytes, StandardCharsets.UTF_8)
          ).valueOr { err =>
            throw CirceError(err)
          }
        }

        (metadata, Reader.fromStream(inputStream))
      } catch {
        case NonFatal(e) =>
          inputStream.close()
          throw e
      }
    }
  }
}

object LocalObjectStorage {
  def apply(path: Path)(implicit statsReceiver: StatsReceiver): LocalObjectStorage = {
    new LocalObjectStorage(path)
  }

  case class ObjectList(
    objects: List[String],
    directories: List[String]
  ) extends ObjectStorage.ObjectList {
    val listToken = None
  }

  private val contentTypeKey = "Content-Type"
}
