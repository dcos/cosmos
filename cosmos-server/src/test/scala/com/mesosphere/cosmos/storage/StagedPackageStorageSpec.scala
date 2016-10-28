package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.universe.MediaTypes
import com.twitter.io.StreamIO
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.mockito.Matchers.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks
import scala.collection.mutable

final class StagedPackageStorageSpec extends FreeSpec with MockitoSugar with PropertyChecks {

  import StagedPackageStorageSpec._

  "writes packages to an object store" in {
    val (objectStorage, stagedStorage) = storageWithWriteResult(Future.value(()))

    forAll { (packageData: InputStream, packageSize: Long) =>
      val packageId = testPut(stagedStorage, packageData, packageSize)

      val _ = verify(objectStorage)
        .write(packageId.toString, packageData, packageSize, MediaTypes.PackageZip)
    }
  }

  "generates a unique ID for each package" in {
    val (_, stagedStorage) = storageWithWriteResult(Future.value(()))
    var ids = List.empty[UUID]

    forAll { (packageData: InputStream, packageSize: Long) =>
      ids ::= testPut(stagedStorage, packageData, packageSize)
    }

    assertResult(ids.distinct)(ids)
  }

  "propagates write errors" in {
    forAll { (packageData: InputStream, packageSize: Long, errorMessage: String) =>
      val writeResult = Future.exception(new IOException(errorMessage))
      val (_, stagedStorage) = storageWithWriteResult(writeResult)

      val exception = intercept[IOException](testPut(stagedStorage, packageData, packageSize))

      assertResult(errorMessage)(exception.getMessage)
    }
  }

  // TODO package-add: More tests for None case and case where MediaType is incorrect
  "reads packages from an object store" in {
    val objectStorage = mock[ObjectStorage]
    val stagedStorage = StagedPackageStorage(objectStorage)

    forAll { (packageId: UUID, packageContents: Map[String, mutable.WrappedArray[Byte]]) =>
      val bytesOut = new ByteArrayOutputStream()
      val packageOut = new ZipOutputStream(bytesOut, StandardCharsets.UTF_8)
      packageContents.foreach { case (path, data) =>
          packageOut.putNextEntry(new ZipEntry(path))
          packageOut.write(data.array)
          packageOut.closeEntry()
      }
      packageOut.close()

      val packageData = new ByteArrayInputStream(bytesOut.toByteArray)
      when(objectStorage.read(packageId.toString))
        .thenReturn(Future.value(Some((MediaTypes.PackageZip, packageData))))

      val (_, inputStream) = Await.result(stagedStorage.get(packageId))
      val zipIn = new ZipInputStream(inputStream)
      val packageIn = Stream.continually(Option(zipIn.getNextEntry()))
        .takeWhile(_.isDefined)
        .flatten
        .map { zipEntry =>
          zipEntry.getName -> new mutable.WrappedArray.ofByte(StreamIO.buffer(zipIn).toByteArray)
        }
        .toMap

      assertResult(packageContents)(packageIn)
    }

  }

  private[this] def storageWithWriteResult(
    returnValue: Future[Unit]
  ): (ObjectStorage, StagedPackageStorage) = {
    val objectStorage = mock[ObjectStorage]
    when(objectStorage.write(any[String], any[InputStream], any[Long], any[MediaType]))
      .thenReturn(returnValue)

    (objectStorage, StagedPackageStorage(objectStorage))
  }

}

object StagedPackageStorageSpec {

  def testPut(storage: StagedPackageStorage, packageData: InputStream, packageSize: Long): UUID = {
    Await.result(storage.put(packageData, packageSize, MediaTypes.PackageZip))
  }

  implicit val arbitraryInputStream: Arbitrary[InputStream] = {
    Arbitrary(implicitly[Arbitrary[Array[Byte]]].arbitrary.map(new ByteArrayInputStream(_)))
  }

  implicit val arbitraryUUID: Arbitrary[UUID] = Arbitrary(Gen.uuid)

}
