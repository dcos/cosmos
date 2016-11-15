package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.cosmos.test.TestUtil
import com.mesosphere.universe
import com.mesosphere.universe.v3.model.PackageDefinitionSpec.packageDefinitionGen
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.dsl._
import com.twitter.util.Await
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class PackageAdderSpec extends FreeSpec with Matchers with PropertyChecks {
  "Test that installing new package succeeds" in TestUtil.withObjectStorage { tempObjectStorage =>
    TestUtil.withObjectStorage { objectStorage =>
      forAll(packageDefinitionGen) { expected =>
        val packageObjectStorage = PackageObjectStorage(objectStorage)
        val adder = PackageAdder(
          tempObjectStorage,
          packageObjectStorage,
          LocalPackageCollection(packageObjectStorage)
        )

        val _ = Await.result(
          adder(
            "http://ignored/for/now",
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
        forAll(packageDefinitionGen) { expected =>
          val packageObjectStorage = PackageObjectStorage(objectStorage)
          val adder = PackageAdder(
            tempObjectStorage,
            packageObjectStorage,
            LocalPackageCollection(packageObjectStorage)
          )

          val _ = Await.result(
            adder(
              "http://ignored/for/now",
              expected
            ) before ( // Install the same package twice
              adder(
                "http://ignored/for/now",
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
