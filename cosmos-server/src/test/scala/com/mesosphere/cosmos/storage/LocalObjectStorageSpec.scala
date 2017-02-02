package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.test.TestUtil
import com.twitter.io.StreamIO
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayInputStream
import java.time.Instant
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

final class LocalObjectStorageSpec extends FreeSpec with PropertyChecks {

  import LocalObjectStorageSpec._
  import ObjectStorageOps.objectStorageOps

  "read() must observe None on a nonexistent object" in {
    forAll(genObjectStorageState, genPath) { (state, path) =>
      whenever(!state.map(_.name).contains(path)) {
        TestUtil.withLocalObjectStorage { storage =>
          val nonexistentRead = Await.result {
            storage.writeAll(state) before
              storage.read(path)
          }
          assertResult(None)(nonexistentRead)
        }
      }
    }
  }

  "read() must observe write() content" in {
    forAll(genNonEmptyObjectStorageState) { state =>
      TestUtil.withLocalObjectStorage { storage =>
        val someItem = state.head
        val Some((_, dataFromRead)) = Await.result {
          storage.writeAll(state) before
            storage.readAsArray(someItem.name)
        }
        assertResult(someItem.content)(dataFromRead)
      }
    }
  }

  "read() must observe applicationOctetStream as mediaType on a write() where no mediaType is given" in {
    forAll(genObjectStorageItem.suchThat(_.mediaType.isEmpty)) { item =>
      TestUtil.withLocalObjectStorage { storage =>
        val Some((mediaTypeFromRead, _)) = Await.result {
          storage.write(item.name, item.content) before
            storage.readAsArray(item.name)
        }
        assertResult(MediaTypes.applicationOctetStream)(mediaTypeFromRead)
      }
    }
  }

  "read() must observe write() mediaType" in {
    forAll(genNonEmptyObjectStorageState) { state =>
      TestUtil.withLocalObjectStorage { storage =>
        val someItem = state.head
        val Some((mediaTypeFromRead, _)) = Await.result {
          storage.writeAll(state) before
            storage.readAsArray(someItem.name)
        }
        val mediaTypeWritten = getMediaTypeWritten(someItem.mediaType)
        assertResult(mediaTypeWritten)(mediaTypeFromRead)
      }
    }
  }

  "read() cannot observe partial write() content" in {
    forAll(genNonEmptyObjectStorageState) { state =>
      TestUtil.withLocalObjectStorage { storage =>
        val someItem = state.head
        val writeOp = storage.writeAll(state)
        val readOp = TestUtil.eventualFuture(() => storage.readAsArray(someItem.name))
        val (_, (_, dataFromRead)) = Await.result(writeOp.join(readOp))
        assertResult(someItem.content)(dataFromRead)
      }
    }
  }

  "read() cannot observe partial write() mediaType" in {
    forAll(genNonEmptyObjectStorageState) { state =>
      TestUtil.withLocalObjectStorage { storage =>
        val someItem = state.head
        val writeOp = storage.writeAll(state)
        val readOp = TestUtil.eventualFuture(() => storage.readAsArray(someItem.name))
        val (_, (mediaTypeFromRead, _)) = Await.result(writeOp.join(readOp))
        val mediaTypeWritten = getMediaTypeWritten(someItem.mediaType)
        assertResult(mediaTypeWritten)(mediaTypeFromRead)
      }
    }
  }

  "read() must observe None on a delete()-ed object" in {
    forAll(genNonEmptyObjectStorageState) { state =>
      TestUtil.withLocalObjectStorage { storage =>
        val someItem = state.head
        val readResult = Await.result {
          storage.writeAll(state).before(
            storage.delete(someItem.name)).before(
            storage.readAsArray(someItem.name))
        }
        assertResult(None)(readResult)
      }
    }
  }

  "read() and write() must be case sensitive" in {
    forAll(genNonEmptyObjectStorageState) { state =>
      val someItem = state.head
      val badName = someItem.name.toLowerCase
      whenever(!state.map(_.name).contains(badName)) {
        TestUtil.withLocalObjectStorage { storage =>
          val readResult = Await.result {
            storage.writeAll(state).before(
              storage.readAsArray(badName))
          }
          assertResult(None)(readResult)
        }
      }
    }
  }

  "delete() removes all empty parent directories" in {
    forAll(genObjectStorageState) { state =>
      TestUtil.withLocalObjectStorage { storage =>
        val objects = Await.result {
          storage.writeAll(state).before(
            storage.deleteAll(state.map(_.name)).before(
              storage.list("")))
        }
        assertResult(List())(objects.directories)
      }
    }
  }

  "delete() does not delete any other object's parents" in {
    forAll(genObjectStorageItemPairAndSharedParents) { case (parents, item1, item2) =>
      TestUtil.withLocalObjectStorage { storage =>
        val objects = Await.result {
          (storage.writeAll(List(item1, item2)) before
            storage.delete(item1.name)) before
            storage.list(parents)
        }

        val condition = objects.directories.length == 1 || objects.objects.length == 1
        assert(condition)
      }
    }
  }

  "list() returns all objects written" in {
    forAll(genObjectStorageState) { state =>
      TestUtil.withLocalObjectStorage { storage =>
        val namesInStorage = Await.result {
          storage.writeAll(state) before
            storage.listAllObjects("")
        }.sorted
        val namesWritten = state.map(_.name).sorted
        assertResult(namesWritten)(namesInStorage)
      }
    }
  }

  "getCreationTime() returns the time the object was created" in {
    forAll(genObjectStorageItem) { item =>
      TestUtil.withLocalObjectStorage { storage =>
        val tolerance = 2L
        val timeBefore = Instant.now().minusSeconds(tolerance)
        val creationTime = Await.result {
          storage.write(item.name, item.content) before
            storage.getCreationTime(item.name)
        }
        val timeAfter = Instant.now().plusSeconds(tolerance)
        assert(
          timeBefore.isBefore(creationTime.get) && timeAfter.isAfter(creationTime.get),
          List(timeBefore, creationTime, timeAfter)
        )
      }
    }
  }

  "getCreationTime() returns None if object does not exist" in {
    forAll(genPath) { name =>
      TestUtil.withLocalObjectStorage { storage =>
        val creationTime = Await.result {
          storage.getCreationTime(name)
        }
        assertResult(None)(creationTime)
      }
    }
  }

}

object LocalObjectStorageSpec {

  case class ObjectStorageItem(name: String, content: Array[Byte], mediaType: Option[MediaType])

  type ObjectStorageState = List[ObjectStorageItem]

  def genPath(maxSegments: Int): Gen[String] = {
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
      !s.startsWith("/") &&
      !s.contains("//") &&
      !s.endsWith("/")

  val genPath: Gen[String] = {
    val maxSegments = 10
    genPath(maxSegments)
  }

  val genPathPairAndSharedParents: Gen[(String, String, String)] = {
    val halfSize = 5
    val generator = for {
      parents <- genPath(halfSize)
      first <- genPath(halfSize)
      second <- genPath(halfSize).suchThat(_ != first)
    } yield (parents, parents + "/" + first, parents + "/" + second)

    generator.suchThat {
      case (parent, first, second) =>
        pathsConflict(List(parent, first)) && pathsConflict(List(parent, second))
    }
  }

  val genMediaType: Gen[MediaType] = {
    val mediaTypes = Array(
      MediaTypes.applicationJson,
      MediaTypes.applicationOctetStream,
      MediaTypes.OperationStatus,
      MediaTypes.RepositoryList
    )
    Gen.oneOf(mediaTypes)
  }

  val genObjectStorageItem: Gen[ObjectStorageItem] = {
    for {
      path <- genPath
      mediaType <- Gen.option(genMediaType)
      data <- arbitrary[Array[Byte]]
    } yield ObjectStorageItem(path, data, mediaType)
  }

  val genObjectStorageItemPairAndSharedParents: Gen[(String, ObjectStorageItem, ObjectStorageItem)] = {
    val generator = for {
      (parents, path1, path2) <- genPathPairAndSharedParents
      data1 <- arbitrary[Array[Byte]]
      data2 <- arbitrary[Array[Byte]]
      mediaType1 <- Gen.option(genMediaType)
      mediaType2 <- Gen.option(genMediaType)
    } yield {
      val item1 = ObjectStorageItem(path1, data1, mediaType1)
      val item2 = ObjectStorageItem(path2, data2, mediaType2)
      (parents, item1, item2)
    }

    generator.suchThat {
      case (parent, first, second) =>
        pathsConflict(List(parent, first.name)) && pathsConflict(List(parent, second.name))
    }
  }

  val genObjectStorageState: Gen[ObjectStorageState] = {
    Gen.listOf(genObjectStorageItem).suchThat { state =>
      !pathsConflict(state.map(_.name))
    }
  }

  val genNonEmptyObjectStorageState: Gen[ObjectStorageState] = {
    genObjectStorageState.suchThat(_.nonEmpty)
  }

  def pathsConflict(paths: List[String]): Boolean = {
    paths.combinations(2).exists { case List(a, b) =>
      val aComponents = a.split("/")
      val bComponents = b.split("/")
      aComponents.startsWith(bComponents) || bComponents.startsWith(aComponents)
    }
  }

  def getMediaTypeWritten(maybeMediaType: Option[MediaType]): MediaType =
    maybeMediaType.getOrElse(MediaTypes.applicationOctetStream)


  implicit class ObjectStorageNoStreams(val objectStorage: LocalObjectStorage) extends AnyVal {

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

    def writeAll(objectStorageItems: List[ObjectStorageItem]): Future[Unit] = {
      Future.join(
        objectStorageItems.map { objectStorageItem =>
          write(objectStorageItem.name, objectStorageItem.content, objectStorageItem.mediaType)
        }
      )
    }

    def deleteAll(names: List[String]): Future[Unit] = {
      Future.join(
        names.map { name =>
          objectStorage.delete(name)
        }
      )
    }
  }

}
