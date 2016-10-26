package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.PackageNotFound
import com.mesosphere.cosmos.VersionNotFound
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage.LocalObjectStorage
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.universe
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.util.Failure
import scala.util.Try

final class LocalPackageCollectionSpec extends FreeSpec with Matchers {
  implicit val stats = com.twitter.finagle.stats.NullStatsReceiver

  def withTempDirectory(testCode: ObjectStorage => Unit): Unit = {
    val path = Files.createTempDirectory("cosmos-dlpc-")
    try {
      testCode(LocalObjectStorage(path))
    } finally removeDir(path)
  }

  def removeDir(dir: Path): Unit = {
    val visitor = new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {
        Option(e) match {
          case Some(failure) => throw failure
          case _ =>
            Files.delete(dir)
            FileVisitResult.CONTINUE
        }
      }
    }

    val _ = Files.walkFileTree(dir, visitor)
  }

  val expectedLatestCeph = universe.v3.model.V3Package(
    packagingVersion=universe.v3.model.V3PackagingVersion,
    name="ceph",
    version=universe.v3.model.PackageDefinition.Version("1.5"),
    releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(1).get,
    maintainer="jose@mesosphere.com",
    description="Great object store"
  )

  val expectedLambda05 = universe.v3.model.V3Package(
    packagingVersion=universe.v3.model.V3PackagingVersion,
    name="lambda",
    version=universe.v3.model.PackageDefinition.Version("0.5"),
    releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(2).get,
    maintainer="jose@mesosphere.com",
    description="Great compute framework"
  )

  val expectedAllMarathon = Seq(
    universe.v3.model.V3Package(
      packagingVersion=universe.v3.model.V3PackagingVersion,
      name="marathon",
      version=universe.v3.model.PackageDefinition.Version("1.0"),
      releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(2).get,
      maintainer="jose@mesosphere.com",
      description="paas framework"
    ),
    universe.v3.model.V3Package(
      packagingVersion=universe.v3.model.V3PackagingVersion,
      name="marathon",
      version=universe.v3.model.PackageDefinition.Version("0.10"),
      releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(1).get,
      maintainer="jose@mesosphere.com",
      description="paas framework"
    ),
    universe.v3.model.V3Package(
      packagingVersion=universe.v3.model.V3PackagingVersion,
      name="marathon",
      version=universe.v3.model.PackageDefinition.Version("0.9"),
      releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(4).get,
      maintainer="jose@mesosphere.com",
      description="paas framework"
    ),
    universe.v3.model.V2Package(
      packagingVersion=universe.v3.model.V2PackagingVersion,
      name="marathon",
      version=universe.v3.model.PackageDefinition.Version("0.8.4"),
      releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(3).get,
      maintainer="jose@mesosphere.com",
      description="paas framework",
      universe.v3.model.Marathon(ByteBuffer.allocate(0))
    )
  )

  val expectedAllGreat = Seq(
    universe.v3.model.V3Package(
      packagingVersion=universe.v3.model.V3PackagingVersion,
      name="lambda",
      version=universe.v3.model.PackageDefinition.Version("0.12"),
      releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(3).get,
      maintainer="jose@mesosphere.com",
      description="Great compute framework"
    ),
    expectedLambda05,
    expectedLatestCeph,
    universe.v3.model.V3Package(
      packagingVersion=universe.v3.model.V3PackagingVersion,
      name="ceph",
      version=universe.v3.model.PackageDefinition.Version("1.1"),
      releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(3).get,
      maintainer="jose@mesosphere.com",
      description="Great object store"
    ),
    universe.v3.model.V2Package(
      packagingVersion=universe.v3.model.V2PackagingVersion,
      name="ceph",
      version=universe.v3.model.PackageDefinition.Version("0.8.4"),
      releaseVersion=universe.v3.model.PackageDefinition.ReleaseVersion(3).get,
      maintainer="jose@mesosphere.com",
      description="Great object store",
      universe.v3.model.Marathon(ByteBuffer.allocate(0))
    )
  )

  val expected = expectedAllMarathon ++ expectedAllGreat

  "Test all of the read operations" in withTempDirectory { objectStorage =>
    val packageStorage = PackageObjectStorage(objectStorage)
    val packageCollection = LocalPackageCollection(packageStorage)

    val _ = Await.result(
      Future.collect(
        expected.map(packageStorage.writePackageDefinition)
      )
    )

    // List of all packages
    val actualList = Await.result(packageCollection.list())
    actualList shouldBe expected.map(rpc.v1.model.Installed(_))

    // Find latest installed ceph package
    val actualInstalledCeph = Await.result(packageCollection.getInstalledPackage("ceph", None))
    actualInstalledCeph shouldBe rpc.v1.model.Installed(expectedLatestCeph)

    // Find lambda-0.5 package
    val actualLambda05 = Await.result(
      packageCollection.getPackageByPackageVersion(
        "lambda",
        Option(universe.v3.model.PackageDefinition.Version("0.5"))
      )
    )
    actualLambda05 shouldBe rpc.v1.model.Installed(expectedLambda05)

    // Find missing ceph-1.55 package
    val missingPackageVersion = Try(
      Await.result(
        packageCollection.getPackageByPackageVersion(
          "ceph",
          Option(universe.v3.model.PackageDefinition.Version("1.55"))
        )
      )
    )
    missingPackageVersion shouldBe Failure(
      VersionNotFound("ceph", universe.v3.model.PackageDefinition.Version("1.55"))
    )


    // Find missing queue-1.5 package
    val missingPackage = Try(
      Await.result(
        packageCollection.getPackageByPackageVersion(
          "queue",
          Option(universe.v3.model.PackageDefinition.Version("1.5"))
        )
      )
    )
    missingPackage shouldBe Failure(
      PackageNotFound("queue")
    )

    // Find all lambda packages
    val allMarathon = Await.result(packageCollection.getPackageByPackageName("marathon"))
    allMarathon shouldBe expectedAllMarathon.map(rpc.v1.model.Installed(_))

    // Search for 'PaaS'")
    val allPaas = Await.result(packageCollection.search(Some("PaaS")))
    allPaas shouldBe expectedAllMarathon.map(rpc.v1.model.Installed(_))

    // Search for 'great'
    val allGreat = Await.result(packageCollection.search(Some("great")))
    allGreat shouldBe expectedAllGreat.map(rpc.v1.model.Installed(_))
  }
}
