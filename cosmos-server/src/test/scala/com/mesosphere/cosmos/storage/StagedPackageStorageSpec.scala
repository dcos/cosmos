package com.mesosphere.cosmos.storage

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.universe.MediaTypes
import com.mesosphere.util.AbsolutePath
import com.twitter.io.StreamIO
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import org.mockito.Matchers.any
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.PropertyChecks

final class StagedPackageStorageSpec extends FreeSpec with MockitoSugar with PropertyChecks {

  import StagedPackageStorageSpec._

  "writes packages to an object store" in {
    val (objectStorage, stagedStorage) = storageWithWriteResult(Future.value(()))

    forAll { (packageData: InputStream, packageSize: Long) =>
      val packageId = testPut(stagedStorage, packageData, packageSize)
      val packagePath = StagedPackageStorage.uuidToPath(packageId)

      val _ = verify(objectStorage)
        .write(packagePath, packageData, packageSize, MediaTypes.PackageZip)
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

    forAll { (packageId: UUID, packageContents: Array[Byte]) =>
      val packagePath = StagedPackageStorage.uuidToPath(packageId)
      val packageData = new ByteArrayInputStream(packageContents)
      when(objectStorage.read(packagePath))
        .thenReturn(Future.value(Some((MediaTypes.PackageZip, packageData))))

      val Some((_, inputStream)) = Await.result(stagedStorage.read(packageId))
      val packageIn = StreamIO.buffer(inputStream).toByteArray

      assertResult(packageContents)(packageIn)
    }

  }

  private[this] def storageWithWriteResult(
    returnValue: Future[Unit]
  ): (ObjectStorage, StagedPackageStorage) = {
    val objectStorage = mock[ObjectStorage]
    when(objectStorage.write(any[AbsolutePath], any[InputStream], any[Long], any[MediaType]))
      .thenReturn(returnValue)

    (objectStorage, StagedPackageStorage(objectStorage))
  }

}

object StagedPackageStorageSpec {

  def testPut(storage: StagedPackageStorage, packageData: InputStream, packageSize: Long): UUID = {
    Await.result(storage.write(packageData, packageSize, MediaTypes.PackageZip))
  }

  implicit val arbitraryInputStream: Arbitrary[InputStream] = {
    Arbitrary(implicitly[Arbitrary[Array[Byte]]].arbitrary.map(new ByteArrayInputStream(_)))
  }

  implicit val arbitraryUUID: Arbitrary[UUID] = Arbitrary(Gen.uuid)

}
