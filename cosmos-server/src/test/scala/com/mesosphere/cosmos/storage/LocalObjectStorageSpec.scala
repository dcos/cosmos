package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.test.TestUtil
import com.twitter.io.StreamIO
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayInputStream
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

final class LocalObjectStorageSpec extends FreeSpec with PropertyChecks {

  import LocalObjectStorageSpec._

  "read() on a nonexistent file returns Future(None)" in {
    forAll(genPath) { path =>
      TestUtil.withLocalObjectStorage { localStorage =>
        assertResult(None) {
          Await.result(localStorage.read(path))
        }
      }
    }
  }

  "read() cannot observe partial writes to a file" in {
    forAll(genPath, arbitrary[Array[Byte]]) { (path, dataToWrite) =>
      TestUtil.withLocalObjectStorage { localStorage =>
        val writeOp = localStorage.write(path, dataToWrite)
        val readOp = TestUtil.eventualFuture(() => localStorage.readAsArray(path))
        val (_, (_, dataFromRead)) = Await.result(writeOp.join(readOp))
        assertResult(dataToWrite)(dataFromRead)
      }
    }
  }

  "read() on a deleted file returns Future(None)" in {
    forAll(genPath, arbitrary[Array[Byte]]) { (path, dataToWrite) =>
      TestUtil.withLocalObjectStorage { localStorage =>
        Await.result {
          for {
            _ <- localStorage.write(path, dataToWrite)
            Some((_, dataFromRead)) <- localStorage.readAsArray(path)
            _ <- Future(assertResult(dataToWrite)(dataFromRead))
            _ <- localStorage.delete(path)
            failedRead <- localStorage.readAsArray(path)
            _ <- Future(assertResult(None)(failedRead))
          } yield ()
        }
      }
    }
  }

  "read() returns the previously written MediaType" in {
    forAll(genPath, arbitrary[Array[Byte]], genMediaType) { (path, dataToWrite, mediaTypeToWrite) =>
      TestUtil.withLocalObjectStorage { localStorage =>
        Await.result {
          for {
            _ <- localStorage.write(path, dataToWrite, Some(mediaTypeToWrite))
            Some((mediaTypeFromRead, _)) <- localStorage.readAsArray(path)
            _ <- Future(assertResult(mediaTypeToWrite)(mediaTypeFromRead))
          } yield ()
        }
      }
    }
  }

  "write() stores applicationOctetStream as default MediaType" in {
    forAll(genPath, arbitrary[Array[Byte]]) { (path, dataToWrite) =>
      TestUtil.withLocalObjectStorage { localStorage =>
        Await.result {
          for {
            _ <- localStorage.write(path, dataToWrite)
            Some((mediaType, _)) <- localStorage.readAsArray(path)
            _ <- Future(assertResult(MediaTypes.applicationOctetStream)(mediaType))
          } yield ()
        }
      }
    }
  }

  "delete() removes all empty parent directories" in {
    forAll(genObjectStorageState) { objectStorageState =>
      TestUtil.withLocalObjectStorage { localStorage =>
        Await.result {
          for {
            _ <- localStorage.writeAll(objectStorageState)
            _ <- localStorage.deleteAll(objectStorageState.map(_.name))
            objects <- localStorage.list("")
            _ <- Future(assertResult(List())(objects.directories))
          } yield ()
        }
      }
    }
  }

  "write() and read() must be case sensitive" in {
    val name1 = "B/nR/xyharPhq"
    val badName1 = "b/nR/xyharphq"
    TestUtil.withLocalObjectStorage { localStorage =>
      Await.result {
        for {
          _ <- localStorage.write(name1, Array[Byte]())
          itemAtBadName <- localStorage.readAsArray(badName1)
          _ <- Future(assertResult(None)(itemAtBadName))
        } yield ()
      }
    }
  }

  "list() returns all objects written" in {
    forAll(genObjectStorageState) { objectStorageState =>
      TestUtil.withLocalObjectStorage { localStorage =>
        Await.result {
          val namesToWrite = objectStorageState.map(_.name)
          for {
            _ <- localStorage.writeAll(objectStorageState)
            namesInStorage <- localStorage.listAll("")
            _ <- Future(assertResult(namesToWrite.sorted)(namesInStorage.sorted))
            _ <- localStorage.deleteAll(objectStorageState.map(_.name))
          } yield ()
        }
      }
    }
  }

  "getUrl() return None for all objects" in {
    forAll(genPath, arbitrary[Array[Byte]]) { (path, dataToWrite) =>
      TestUtil.withLocalObjectStorage { localStorage =>
        Await.result {
          for {
            _ <- localStorage.write(path, dataToWrite)
            url <- Future(localStorage.getUrl(path))
            _ <- Future(assertResult(None)(url))
          } yield ()
        }
      }
    }
  }

  "getCreationTime returns the time the object was created" in {
    forAll(genPath, arbitrary[Array[Byte]]) { (path, dataToWrite) =>
      TestUtil.withLocalObjectStorage { localStorage =>
        Await.result {
          val twoSeconds = 2000
          for {
            timeBefore <- Future(System.currentTimeMillis() - twoSeconds)
            _ <- localStorage.write(path, dataToWrite)
            creationTime <- localStorage.getCreationTime(path)
            timeAfter <- Future(System.currentTimeMillis() + twoSeconds)
            _ <- Future(assert(
              timeBefore < creationTime.get && creationTime.get < timeAfter,
              List(timeBefore, creationTime, timeAfter)
            ))
          } yield ()
        }
      }
    }
  }

}

object LocalObjectStorageSpec {

  case class ObjectStorageItem(name: String, content: Array[Byte], mediaType: Option[MediaType])

  type ObjectStorageState = List[ObjectStorageItem]

  val genPath: Gen[String] = {
    val maxSegments = 10
    val maxSegmentLength = 10
    val genSegment = Gen.choose(1, maxSegmentLength)
      .flatMap(Gen.buildableOfN[String, Char](_, Gen.alphaNumChar))

    (for {
      numSegments <- Gen.choose(1, maxSegments)
      segments <- Gen.listOfN(numSegments, genSegment)
      path = segments.mkString("/")
    } yield path).suchThat(isValidPath)
  }

  def isValidPath(s: String): Boolean =
    s.nonEmpty &&
      !s.forall(_ == '/') &&
      !s.startsWith("/") &&
      !s.contains("//") &&
      !s.endsWith("/")

  val genMediaType: Gen[MediaType] = {
    val mediaTypes = Array(
      MediaTypes.applicationJson,
      MediaTypes.applicationOctetStream,
      MediaTypes.OperationStatus,
      MediaTypes.RepositoryList
    )
    Gen.choose(0, mediaTypes.length - 1).map(mediaTypes)
  }

  val genObjectStorageItem: Gen[ObjectStorageItem] = {
    for {
      path <- genPath
      mediaType <- Gen.option(genMediaType)
      data <- arbitrary[Array[Byte]]
    } yield ObjectStorageItem(path, data, mediaType)
  }

  val genObjectStorageState: Gen[ObjectStorageState] = {
    Gen.listOf(genObjectStorageItem).suchThat { state =>
      !pathsConflict(state.map(_.name))
    }
  }

  def pathsConflict(paths: List[String]): Boolean = {
    paths.combinations(2).exists { case List(a, b) =>
      a.startsWith(b) || b.startsWith(a)
    }
  }

  implicit class ObjectStorageNoStreams(objectStorage: LocalObjectStorage) {

    def write(name: String, content: Array[Byte], mediaType: Option[MediaType] = None): Future[Unit] = {
      val body = new ByteArrayInputStream(content)
      val contentLength = content.length.toLong
      objectStorage.write(name, body, contentLength, mediaType)
    }

    def readAsArray(name: String): Future[Option[(MediaType, Array[Byte])]] = {
      objectStorage.read(name).map(_.map { case (mediaType, readStream) =>
        (mediaType, StreamIO.buffer(readStream).toByteArray)
      })
    }

    def listAll(base: String): Future[List[String]] = {
      objectStorage.list(base).flatMap { objectList =>
        Future.collect(
          objectList.directories
            .map(listAll)
        ).map { rest =>
          (objectList.objects :: rest.toList).flatten
        }
      }
    }

    def writeAll(objectStorageItems: List[ObjectStorageItem]): Future[Unit] = {
      Future.collect(
        objectStorageItems.map { objectStorageItem =>
          write(objectStorageItem.name, objectStorageItem.content, objectStorageItem.mediaType)
        }
      ).map(_ => ())
    }

    def deleteAll(names: List[String]): Future[Unit] = {
      Future.collect(
        names.map { name =>
          objectStorage.delete(name)
        }
      ).map(_ => ())
    }
  }

}
