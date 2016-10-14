package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage.LocalObjectStorage
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.cosmos.storage.S3ObjectStorage
import com.mesosphere.universe
import com.twitter.util.Await
import com.twitter.util.Future
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import scala.util.Try

// TODO: Make sure that we look at the operation store
final class DefaultLocalPackageCollection(
  objectStorage: PackageObjectStorage
) extends LocalPackageCollection {
  override def list(): Future[List[rpc.v1.model.LocalPackage]] = {
    objectStorage.list()
  }
}

object DefaultLocalPackageCollection {
  implicit val stats = com.twitter.finagle.stats.NullStatsReceiver

  def testLocal(): Unit = {
    test(LocalObjectStorage(Paths.get("/tmp/cosmos/objects")))
  }

  import com.amazonaws.services.s3.AmazonS3Client
  import com.amazonaws.auth.BasicAWSCredentials

  def testS3(accessKey: String, secretKey: String): Unit = {
    test(
      S3ObjectStorage(
        new AmazonS3Client(
          new BasicAWSCredentials(accessKey, secretKey)
        ),
        "jose-s3-storage-test",
        "cosmos/objects"
      )
    )
  }

  def test(objectStorage: ObjectStorage): Unit = {
    implicit val packageStorage = new PackageObjectStorage(objectStorage)

    val packageCollection = new DefaultLocalPackageCollection(packageStorage)


    // TODO: DescribeResponse is not the correct type as we shouldn't have a selected property
    val addedPackages = Await.result(
      Future.collect(
        Seq(
          addPackage(
            rpc.v2.model.DescribeResponse(
              packagingVersion=universe.v3.model.V3PackagingVersion,
              name="ceph",
              version=universe.v3.model.PackageDefinition.Version("1.1"),
              maintainer="jose@mesosphere.com",
              description="Great object store"
            )
          ),
          addPackage(
            rpc.v2.model.DescribeResponse(
              packagingVersion=universe.v3.model.V3PackagingVersion,
              name="ceph",
              version=universe.v3.model.PackageDefinition.Version("1.5"),
              maintainer="jose@mesosphere.com",
              description="Great object store"
            )
          ),
          addPackage(
            rpc.v2.model.DescribeResponse(
              packagingVersion=universe.v3.model.V3PackagingVersion,
              name="lambda",
              version=universe.v3.model.PackageDefinition.Version("0.5"),
              maintainer="jose@mesosphere.com",
              description="Great compute framework"
            )
          ),
          addPackage(
            rpc.v2.model.DescribeResponse(
              packagingVersion=universe.v3.model.V3PackagingVersion,
              name="lambda",
              version=universe.v3.model.PackageDefinition.Version("0.12"),
              maintainer="jose@mesosphere.com",
              description="Great compute framework"
            )
          )
        )
      )
    )

    println("List of installed packages")
    Await.result(packageCollection.list()).foreach { pkg =>
      println(pkg)
      println("")
    }

    println("List installed ceph package")
    println(Await.result(packageCollection.getInstalledPackage("ceph", None)))
    println("")

    println("Find ceph-1.5 package")
    println(
      Await.result(
        packageCollection.getPackageByPackageVersion(
          "ceph",
          Option(universe.v3.model.PackageDefinition.Version("1.5"))
        )
      )
    )
    println("")

    println("Find missing ceph-1.55 package")
    println(
      Try(
        Await.result(
          packageCollection.getPackageByPackageVersion(
            "ceph",
            Option(universe.v3.model.PackageDefinition.Version("1.55"))
          )
        )
      )
    )
    println("")

    println("Find missing queue-1.5 package")
    println(
      Try(
        Await.result(
          packageCollection.getPackageByPackageVersion(
            "queue",
            Option(universe.v3.model.PackageDefinition.Version("1.5"))
          )
        )
      )
    )
    println("")

    println("All lambda packages")
    Await.result(
      packageCollection.getPackageByPackageName("lambda")
    ).foreach { pkg =>
      println(pkg)
      println("")
    }

    println("Search for 'great'")
    Await.result(
      packageCollection.search(Some("great"))
    ).foreach { pkg =>
      println(pkg)
      println("")
    }

    println("Search for 'lambda'")
    Await.result(
      packageCollection.search(Some("lambda"))
    ).foreach { pkg =>
      println(pkg)
      println("")
    }
  }

  def addPackage(
    pkg: rpc.v2.model.DescribeResponse
  )(
    implicit packageStorage: PackageObjectStorage
  ): Future[rpc.v2.model.DescribeResponse] = {
    packageStorage.writePackageDefinition(pkg).map(_ => pkg)
  }
}
