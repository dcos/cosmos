package com.mesosphere.cosmos.storage

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.util.control.NonFatal

import com.netaporter.uri.Uri
import com.twitter.io.Reader
import com.twitter.io.StreamIO
import com.twitter.util.Future
import com.twitter.util.FuturePool
import io.circe.jawn.decode
import io.circe.syntax._

import com.mesosphere.cosmos.CirceError
import com.mesosphere.cosmos.http.MediaType


final class LocalObjectStorage(path: Path) extends ObjectStorage {
  private[this] val pool = FuturePool.interruptibleUnboundedPool

  override def write(
    name: String,
    body: InputStream,
    contentLength: Long,
    contentType: Option[MediaType] = None
  ): Future[Unit] = {
    pool {
      val absolutePath = path.resolve(name)

      // Create all parent directories
      Files.createDirectories(absolutePath.getParent)

      val (_, bodySize) = writeToFile(
        contentType.map(mediaType => (LocalObjectStorage.contentTypeKey, mediaType.show)).toMap,
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

  override def read(name: String): Future[(Option[MediaType], Reader)] = {
    pool {
      val (metadata, reader) = readFromFile(path.resolve(name))

      (
        metadata.get(LocalObjectStorage.contentTypeKey).flatMap(
          value => MediaType.parse(value).toOption
        ),
        reader
      )
    }
  }

  override def delete(name: String): Future[Unit] = {
    pool {
      // Drop the boolean. We want to have the same semantic as S3 which succeeds in either case.
      val _ = Files.deleteIfExists(path.resolve(name))
    }
  }

  override def list(directory: String): Future[LocalObjectStorage.ObjectList] = {
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

  override def listNext(token: ObjectStorage.ListToken): Future[ObjectStorage.ObjectList] = {
    Future.exception(
      new IllegalArgumentException(s"Local object store doesn't support paged listing")
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
  ): (Map[String, String], Reader) = {
    val inputStream = new DataInputStream(
      Files.newInputStream(
        absolutePath,
        StandardOpenOption.READ
      )
    )

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

object LocalObjectStorage {
  def apply(path: Path): LocalObjectStorage = {
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
