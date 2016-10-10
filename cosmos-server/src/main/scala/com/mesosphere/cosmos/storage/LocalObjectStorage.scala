package com.mesosphere.cosmos.storage

import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import scala.collection.JavaConverters._
import scala.collection.breakOut

import com.netaporter.uri.Uri
import com.twitter.io.Reader
import com.twitter.util.Future
import com.twitter.util.FuturePool

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

      // TODO: do come content type encoding
      val size = Files.copy(
        body,
        absolutePath,
        StandardCopyOption.REPLACE_EXISTING
      )

      if (contentLength != size) {
        // TODO: Remove the file created
        throw new IllegalArgumentException(s"Content length $contentLength doesn't equal size of stream $size")
      }
    }
  }

  override def read(name: String): Future[Reader] = {
    Future {
      val absolutePath = path.resolve(name)
      Reader.fromFile(absolutePath.toFile)
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
    Future(throw new IllegalArgumentException(s"Local object store doesn't support paged listing"))
  }

  override def getUrl(name: String): Option[Uri] = None
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

  import com.twitter.io.Buf
  import com.twitter.util.Await
  import java.io.ByteArrayInputStream
  import java.nio.file.FileSystems

  // TODO: remove this method
  def test(): Unit = {
    val storage = LocalObjectStorage(
      FileSystems.getDefault().getPath("/tmp/cosmos/object-storage")
    )

    val bytes = "Hello World!".getBytes()

    val _ = Await.result(
      storage.write(
        "folder/hello",
        new ByteArrayInputStream(bytes),
        bytes.length.toLong,
        None // TODO: save content type
      )
    )

    val objectList = Await.result(storage.list("folder"))
    println(s"object names = ${objectList.objects}")
    println(s"directory names = ${objectList.directories}")
    println(s"token = ${objectList.listToken}")

    val contents = Await.result {
      Future.collect {
        objectList.objects.map { name =>
          storage.read(name).flatMap { reader =>
            Reader.readAll(reader).map { buffer =>
              val Some(string) = Buf.Utf8.unapply(buffer)
              string
            }
          }
        }
      }
    }

    println(contents)
  }
}
