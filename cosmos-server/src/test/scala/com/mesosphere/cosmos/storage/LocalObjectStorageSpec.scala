package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.util.AbsolutePath
import com.mesosphere.util.RelativePath
import com.twitter.io.StreamIO
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayInputStream
import java.time.Instant
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks
import scala.Ordering.Implicits.seqDerivedOrdering

final class LocalObjectStorageSpec extends FreeSpec with PropertyChecks {

  import LocalObjectStorageSpec._

  "read() must observe None on a nonexistent object" in {
    forAll(genObjectStorageState, genPath) { (state, path) =>
      whenever(!state.map(_.path).contains(path)) {
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
            storage.readAsArray(someItem.path)
        }
        assertResult(someItem.content)(dataFromRead)
      }
    }
  }

  "read() must observe applicationOctetStream as mediaType on a write() where no mediaType is given" in {
    forAll(genObjectStorageItem.suchThat(_.mediaType.isEmpty)) { item =>
      TestUtil.withLocalObjectStorage { storage =>
        val Some((mediaTypeFromRead, _)) = Await.result {
          storage.write(item.path, item.content) before
            storage.readAsArray(item.path)
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
            storage.readAsArray(someItem.path)
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
        val readOp = TestUtil.eventualFuture(() => storage.readAsArray(someItem.path))
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
        val readOp = TestUtil.eventualFuture(() => storage.readAsArray(someItem.path))
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
            storage.delete(someItem.path)).before(
            storage.readAsArray(someItem.path))
        }
        assertResult(None)(readResult)
      }
    }
  }

  "read() and write() must be case sensitive" in {
    forAll(genNonEmptyObjectStorageState) { state =>
      val someItem = state.head
      val badPath = AbsolutePath(someItem.path.toString.toLowerCase).right.get
      whenever(!state.map(_.path).contains(badPath)) {
        TestUtil.withLocalObjectStorage { storage =>
          val readResult = Await.result {
            storage.writeAll(state).before(
              storage.readAsArray(badPath))
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
            storage.deleteAll(state.map(_.path)).before(
              storage.list(AbsolutePath.Root)))
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
            storage.delete(item1.path)) before
            storage.list(parents)
        }

        // Need redundant `.toInt` calls to avoid an "implicit numeric widening" compiler warning
        // TODO Update to ScalaTest 3.0.x; see https://github.com/scalatest/scalatest/issues/445
        assert(objects.directories.length.toInt == 1 || objects.objects.length.toInt == 1)
      }
    }
  }

  "list() returns all objects written" in {
    forAll(genObjectStorageState) { state =>
      TestUtil.withLocalObjectStorage { storage =>
        val pathsInStorage = Await.result {
          storage.writeAll(state) before
            storage.listAll(AbsolutePath.Root)
        }.sortBy(_.elements)
        val pathsWritten = state.map(_.path).sortBy(_.elements)
        assertResult(pathsWritten)(pathsInStorage)
      }
    }
  }

  "getCreationTime() returns the time the object was created" in {
    forAll(genObjectStorageItem) { item =>
      TestUtil.withLocalObjectStorage { storage =>
        val tolerance = 2L
        val timeBefore = Instant.now().minusSeconds(tolerance)
        val creationTime = Await.result {
          storage.write(item.path, item.content) before
            storage.getCreationTime(item.path)
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

  case class ObjectStorageItem(
    path: AbsolutePath,
    content: Array[Byte],
    mediaType: Option[MediaType]
  )

  type ObjectStorageState = List[ObjectStorageItem]

  def genRelativePath(maxSegments: Int): Gen[RelativePath] = {
    val maxSegmentLength = 10
    val genSegment = Gen.choose(1, maxSegmentLength)
      .flatMap(Gen.buildableOfN[String, Char](_, Gen.alphaNumChar))

    for {
      numSegments <- Gen.choose(1, maxSegments)
      segments <- Gen.listOfN(numSegments, genSegment)
      relativePath <- RelativePath(segments.mkString("/")).fold(_ => Gen.fail, Gen.const)
    } yield relativePath
  }

  val genPath: Gen[AbsolutePath] = {
    val maxSegments = 10
    genRelativePath(maxSegments).map(AbsolutePath.Root / _)
  }

  val genPathPairAndSharedParents: Gen[(AbsolutePath, AbsolutePath, AbsolutePath)] = {
    val halfSize = 5
    val generator = for {
      parents <- genRelativePath(halfSize).map(AbsolutePath.Root / _)
      first <- genRelativePath(halfSize)
      second <- genRelativePath(halfSize).suchThat(_ != first)
    } yield (parents, parents / first, parents / second)

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

  val genObjectStorageItemPairAndSharedParents:
    Gen[(AbsolutePath, ObjectStorageItem, ObjectStorageItem)] = {
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
        pathsConflict(List(parent, first.path)) && pathsConflict(List(parent, second.path))
    }
  }

  val genObjectStorageState: Gen[ObjectStorageState] = {
    Gen.listOf(genObjectStorageItem).suchThat { state =>
      !pathsConflict(state.map(_.path))
    }
  }

  val genNonEmptyObjectStorageState: Gen[ObjectStorageState] = {
    genObjectStorageState.suchThat(_.nonEmpty)
  }

  def pathsConflict(paths: List[AbsolutePath]): Boolean = {
    paths.combinations(2).exists { case List(a, b) =>
      val aComponents = a.elements
      val bComponents = b.elements
      aComponents.startsWith(bComponents) || bComponents.startsWith(aComponents)
    }
  }

  def getMediaTypeWritten(maybeMediaType: Option[MediaType]): MediaType =
    maybeMediaType.getOrElse(MediaTypes.applicationOctetStream)


  implicit class ObjectStorageNoStreams(val objectStorage: LocalObjectStorage) extends AnyVal {

    def write(
      path: AbsolutePath,
      content: Array[Byte],
      mediaType: Option[MediaType] = None
    ): Future[Unit] = {
      val body = new ByteArrayInputStream(content)
      val contentLength = content.length.toLong
      objectStorage.write(path, body, contentLength, mediaType)
    }

    def readAsArray(path: AbsolutePath): Future[Option[(MediaType, Array[Byte])]] = {
      objectStorage.read(path).map(_.map { case (mediaType, readStream) =>
        (mediaType, StreamIO.buffer(readStream).toByteArray)
      })
    }

    def listAll(base: AbsolutePath): Future[List[AbsolutePath]] = {
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
      Future.join(
        objectStorageItems.map { objectStorageItem =>
          write(objectStorageItem.path, objectStorageItem.content, objectStorageItem.mediaType)
        }
      )
    }

    def deleteAll(paths: List[AbsolutePath]): Future[Unit] = {
      Future.join(
        paths.map { path =>
          objectStorage.delete(path)
        }
      )
    }
  }

}
