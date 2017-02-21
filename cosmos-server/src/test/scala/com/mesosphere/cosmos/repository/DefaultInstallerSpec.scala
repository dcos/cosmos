package com.mesosphere.cosmos.repository

import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.storage.PackageStorage
import com.mesosphere.cosmos.storage.StagedPackageStorage
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.util.Await
import java.util.UUID
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class DefaultInstallerSpec extends FreeSpec with Matchers with PropertyChecks {
  "Test that installing new package succeeds" in TestUtil.withObjectStorage { tempObjectStorage =>
    TestUtil.withObjectStorage { objectStorage =>
      forAll { (expected: universe.v3.model.V3Package) =>
        val packageStorage = PackageStorage(objectStorage)
        val adder = DefaultInstaller(
          StagedPackageStorage(tempObjectStorage),
          packageStorage
        )

        val _ = Await.result(
          adder(
            UUID.randomUUID(),
            expected
          )
        )

        val actual = Await.result(
          packageStorage.readPackageDefinition(expected.packageCoordinate)
        )

        actual shouldBe Some(expected)
      }
    }
  }

  "Test that installing a package that already exists is a noop" in TestUtil.withObjectStorage {
    tempObjectStorage =>
      TestUtil.withObjectStorage { objectStorage =>
        forAll { (expected: universe.v3.model.V3Package) =>
          val packageStorage = PackageStorage(objectStorage)
          val adder = DefaultInstaller(
            StagedPackageStorage(tempObjectStorage),
            packageStorage
          )

          val _ = Await.result(
            adder(
              UUID.randomUUID(),
              expected
            ) before adder( // Install the same package twice
              UUID.randomUUID(),
              expected
            )
          )

          val actual = Await.result(
            packageStorage.readPackageDefinition(expected.packageCoordinate)
          )

          actual shouldBe Some(expected)
        }
      }
  }
}
