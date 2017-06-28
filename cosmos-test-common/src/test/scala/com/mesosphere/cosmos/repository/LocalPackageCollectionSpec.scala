package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage
import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.universe
import com.twitter.util.Await
import com.twitter.util.Future
import java.util.UUID
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import scala.util.Failure
import scala.util.Try

final class LocalPackageCollectionSpec extends FreeSpec with Matchers {

  val expectedLatestCeph = universe.v4.model.V4Package(
    name="ceph",
    version=universe.v3.model.Version("1.1"),
    releaseVersion=universe.v3.model.ReleaseVersion(3),
    maintainer="jose@mesosphere.com",
    description="Great object store",
    upgradesFrom = Some(List(universe.v3.model.ExactVersion(universe.v3.model.Version("0.8.4")))),
    downgradesTo = Some(List())
  )

  val expectedLambda05 = universe.v3.model.V3Package(
    name="lambda",
    version=universe.v3.model.Version("0.5"),
    releaseVersion=universe.v3.model.ReleaseVersion(2),
    maintainer="jose@mesosphere.com",
    description="Great compute framework"
  )

  val expectedAllMarathon = Seq(
    universe.v4.model.V4Package(
      name="marathon",
      version=universe.v3.model.Version("0.9"),
      releaseVersion=
        universe.v3.model.ReleaseVersion(4), // scalastyle:ignore magic.number
      maintainer="jose@mesosphere.com",
      description="paas framework",
      upgradesFrom = Some(List(universe.v3.model.ExactVersion(universe.v3.model.Version("0.10")))),
      downgradesTo = Some(List(universe.v3.model.ExactVersion(universe.v3.model.Version("0.10"))))

    ),
    universe.v3.model.V3Package(
      name="marathon",
      version=universe.v3.model.Version("0.8.4"),
      releaseVersion=universe.v3.model.ReleaseVersion(3),
      maintainer="jose@mesosphere.com",
      description="paas framework"
    ),
    universe.v3.model.V3Package(
      name="marathon",
      version=universe.v3.model.Version("1.0"),
      releaseVersion=universe.v3.model.ReleaseVersion(2),
      maintainer="jose@mesosphere.com",
      description="paas framework"
    ),
    universe.v3.model.V3Package(
      name="marathon",
      version=universe.v3.model.Version("0.10"),
      releaseVersion=universe.v3.model.ReleaseVersion(1),
      maintainer="jose@mesosphere.com",
      description="paas framework"
    )
  )

  val expectedAllGreat = Seq(
    universe.v3.model.V3Package(
      name="lambda",
      version=universe.v3.model.Version("0.12"),
      releaseVersion=universe.v3.model.ReleaseVersion(3),
      maintainer="jose@mesosphere.com",
      description="Great compute framework"
    ),
    expectedLambda05,
    expectedLatestCeph,
    universe.v3.model.V3Package(
      name="ceph",
      version=universe.v3.model.Version("0.8.4"),
      releaseVersion=universe.v3.model.ReleaseVersion(2),
      maintainer="jose@mesosphere.com",
      description="Great object store"
    ),
    universe.v3.model.V3Package(
      name="ceph",
      version=universe.v3.model.Version("1.5"),
      releaseVersion=universe.v3.model.ReleaseVersion(1),
      maintainer="jose@mesosphere.com",
      description="Great object store"
    )
  )

  val expected: Seq[universe.v4.model.SupportedPackageDefinition] = expectedAllMarathon ++ expectedAllGreat

  "Test all of the read operations" in TestUtil.withObjectStorage { objectStorage =>
    val packageStorage = PackageStorage(objectStorage)
    val packageCollection = LocalPackageCollection(packageStorage, TestUtil.EmptyReaderView)

    val _ = Await.result(
      Future.collect(
        expected.map(packageStorage.writePackageDefinition)
      )
    )

    // List of all packages
    val actualList = Await.result(packageCollection.list())
    actualList shouldBe expected.map(rpc.v1.model.Installed)

    // Find latest installed ceph package
    val actualInstalledCeph = Await.result(packageCollection.getInstalledPackage("ceph", None))
    actualInstalledCeph shouldBe rpc.v1.model.Installed(expectedLatestCeph)

    // Find lambda-0.5 package
    val actualLambda05 = Await.result(
      packageCollection.getPackageByPackageVersion(
        "lambda",
        Option(universe.v3.model.Version("0.5"))
      )
    )
    actualLambda05 shouldBe rpc.v1.model.Installed(expectedLambda05)

    // Find missing ceph-1.55 package
    val missingPackageVersion = Try(
      Await.result(
        packageCollection.getPackageByPackageVersion(
          "ceph",
          Option(universe.v3.model.Version("1.55"))
        )
      )
    )
    missingPackageVersion shouldBe Failure(
      VersionNotFound("ceph", universe.v3.model.Version("1.55")).exception
    )


    // Find missing queue-1.5 package
    val missingPackage = Try(
      Await.result(
        packageCollection.getPackageByPackageVersion(
          "queue",
          Option(universe.v3.model.Version("1.5"))
        )
      )
    )
    missingPackage shouldBe Failure(
      PackageNotFound("queue").exception
    )

    // Find all lambda packages
    val allMarathon = Await.result(packageCollection.getPackageByPackageName("marathon"))
    allMarathon shouldBe expectedAllMarathon.map(rpc.v1.model.Installed)

    // Search for 'PaaS'")
    val allPaas = Await.result(packageCollection.search(Some("PaaS")))
    allPaas shouldBe expectedAllMarathon.map(rpc.v1.model.Installed)

    // Search for 'great'
    val allGreat = Await.result(packageCollection.search(Some("great")))
    allGreat shouldBe expectedAllGreat.map(rpc.v1.model.Installed)
  }

  "We should only return Installed packages" in {
    val expectedInstalled = rpc.v1.model.Installed(
      universe.v3.model.V3Package(
        name="lambda",
        version=universe.v3.model.Version("0.8"),
        releaseVersion=universe.v3.model.ReleaseVersion(3),
        maintainer="jose@mesosphere.com",
        description="Great compute framework"
      )
    )

    val package010 = universe.v4.model.V4Package(
      name="lambda",
      version=universe.v3.model.Version("0.10"),
      releaseVersion=universe.v3.model.ReleaseVersion(3),
      maintainer="jose@mesosphere.com",
      description="Great compute framework"
    )

    val expected010 = rpc.v1.model.Failed(
      storage.v1.model.Install(
        UUID.fromString("467061c4-ed39-4718-a8b8-0b6756fddb18"),
        package010
      ),
      rpc.v1.model.ErrorResponse("type", "message", None)
    )

    val input = List[rpc.v1.model.LocalPackage](
      rpc.v1.model.Installing(
        universe.v3.model.V3Package(
          name="lambda",
          version=universe.v3.model.Version("0.12"),
          releaseVersion=universe.v3.model.ReleaseVersion(3),
          maintainer="jose@mesosphere.com",
          description="Great compute framework"
        )
      ),
      rpc.v1.model.Uninstalling(
        Right(
          universe.v3.model.V3Package(
            name="lambda",
            version=universe.v3.model.Version("0.11"),
            releaseVersion=universe.v3.model.ReleaseVersion(3),
            maintainer="jose@mesosphere.com",
            description="Great compute framework"
          )
        )
      ),
      expected010,
      rpc.v1.model.Invalid(
        rpc.v1.model.ErrorResponse("type", "message", None),
        rpc.v1.model.PackageCoordinate(
          "lambda",
          universe.v3.model.Version("0.9")
        )
      ),
      expectedInstalled
    ).sorted.reverse

    // Test lambda results
    LocalPackageCollection.installedPackage(input, "lambda", None) shouldBe expectedInstalled
    LocalPackageCollection.packageByPackageVersion(
      input,
      "lambda",
      Some(universe.v3.model.Version("0.10"))
    ) shouldBe expected010
    LocalPackageCollection.packageByPackageName(input, "lambda") shouldBe input

    // Test empty results
    Try(
      LocalPackageCollection.installedPackage(input, "missing", None)
    ) shouldBe Failure(PackageNotFound("missing").exception)
    Try(
      LocalPackageCollection.packageByPackageVersion(input, "missing", None)
    ) shouldBe Failure(PackageNotFound("missing").exception)
    LocalPackageCollection.packageByPackageName(input, "missing") shouldBe Nil
  }
}
