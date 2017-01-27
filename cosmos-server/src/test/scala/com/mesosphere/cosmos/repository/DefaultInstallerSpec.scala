package com.mesosphere.cosmos.repository

import com.mesosphere.Generators.genV3Package
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Await
import com.twitter.util.Future
import java.util.UUID
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class DefaultInstallerSpec extends FreeSpec with Matchers with PropertyChecks {
  "Test that installing new package succeeds" in TestUtil.withObjectStorage { tempObjectStorage =>
    TestUtil.withObjectStorage { objectStorage =>
      forAll(genV3Package) { expected =>
        val packageObjectStorage = PackageObjectStorage(objectStorage)
        val adder = DefaultInstaller(
          StagedPackageStorage(tempObjectStorage),
          packageObjectStorage
        )

        val _ = Await.result(
          adder(
            UUID.randomUUID(),
            expected
          )
        )

        val actual = Await.result(
          packageObjectStorage.readPackageDefinition(expected.packageCoordinate)
        )

        actual shouldBe Some(expected)
      }
    }
  }

  "Test that installing a package that already exists is a noop" in TestUtil.withObjectStorage {
    tempObjectStorage =>
      TestUtil.withObjectStorage { objectStorage =>
        forAll(genV3Package) { expected =>
          val packageObjectStorage = PackageObjectStorage(objectStorage)
          val adder = DefaultInstaller(
            StagedPackageStorage(tempObjectStorage),
            packageObjectStorage
          )

          val _ = Await.result(
            adder(
              UUID.randomUUID(),
              expected
            ) before ( // Install the same package twice
              adder(
                UUID.randomUUID(),
                expected
              )
            )
          )

          val actual = Await.result(
            packageObjectStorage.readPackageDefinition(expected.packageCoordinate)
          )

          actual shouldBe Some(expected)
        }
      }
  }
}
