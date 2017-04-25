package com.mesosphere.cosmos.repository

import com.mesosphere.Generators
import com.mesosphere.universe
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.prop.PropertyChecks

final class PackageCollectionSpec extends FreeSpec with PropertyChecks {

  import PackageCollectionSpec._

  "upgradesTo()" - {

    "returns the list of versions of repo packages that can be upgraded from the given version" in {
      val genParameters = for {
        name <- Generators.genPackageName
        version <- Generators.genVersion
        genPkgDef = Generators.genV4Package(
          genName = name,
          genUpgrades = Generators.genUpgradesFrom(requiredVersion = Some(version))
        )
        upgrades <- Gen.listOf(genPkgDef)
      } yield (name, version, upgrades)

      forAll (genParameters) { case (name, version, upgrades) =>
        val upgradeVersions = PackageCollection.upgradesTo(name, version, upgrades)
        assert(upgradeVersions.forall(v => upgrades.exists(_.version == v)))
        assert(upgrades.forall(p => upgradeVersions.contains(p.version)))
      }
    }

    "returns an empty list" - {

      "when there are no repo packages with the given name" in {
        val genParameters = for {
          name <- Generators.genPackageName
          version <- Generators.genVersion
          genPkgDef = Generators.genPackageDefinition(
            genUpgrades = Generators.genUpgradesFrom(requiredVersion = Some(version))
          ).suchThat(_.name != name)
          packages <- Gen.listOf(genPkgDef)
        } yield (name, version, packages)

        forAll (genParameters) { case (name, version, packages) =>
          assert(PackageCollection.upgradesTo(name, version, packages).isEmpty)
        }
      }

      "when there are packages that can't be upgraded from the given version" in {
        val genParameters = for {
          name <- Generators.genPackageName
          version <- Generators.genVersion
          genPkgDef = Generators.genPackageDefinition(
            genName = name,
            genUpgrades = genIncompatibleUpgradesFrom(version)
          )
          packages <- Gen.listOf(genPkgDef)
        } yield (name, version, packages)

        forAll (genParameters) { case (name, version, packages) =>
          assert(PackageCollection.upgradesTo(name, version, packages).isEmpty)
        }
      }

    }

  }

}

object PackageCollectionSpec {

  def genIncompatibleUpgradesFrom(
    versionToAvoid: universe.v3.model.Version
  ): Gen[Option[List[universe.v3.model.Version]]] = {
    Gen.option(Gen.listOf(Generators.genVersion.suchThat(_ != versionToAvoid)))
  }

}
