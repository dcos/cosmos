
package com.mesosphere.cosmos

import com.mesosphere.Generators
import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import org.scalatest.prop.TableDrivenPropertyChecks

final class PackageCollectionSpec extends FreeSpec
  with Matchers
  with PropertyChecks
  with TableDrivenPropertyChecks {

  def getRepository(
    packageDefinitions : List[universe.v4.model.PackageDefinition] = List.empty[universe.v4.model.PackageDefinition],
    uri: Uri = Uri.empty
  ): (universe.v4.model.Repository, Uri) = {
    (universe.v4.model.Repository(packageDefinitions), uri)
  }

  def genIncompatibleUpgradesFrom(
    versionToAvoid: universe.v3.model.Version
  ): Gen[Option[List[universe.v3.model.VersionSpecification]]] = {
    val genVersionSpecification = Generators.genVersion
      .suchThat(_ != versionToAvoid)
      .map(universe.v3.model.ExactVersion)

    Gen.option(Gen.listOf(genVersionSpecification))
  }

  "Queries on PackageCollection" - {

    "merge" - {

      "merge should success on empty repositories" in {
        assertResult(
          List.empty[universe.v4.model.PackageDefinition]
        )(
          PackageCollection.merge(List(getRepository()))
        )
      }

      "merge should remove duplicate definitions" in {
        val u = Uri.parse("/uri")
        val packages = List(
          TestingPackages.MinimalV3ModelV2PackageDefinition,
          TestingPackages.MinimalV3ModelV2PackageDefinition
        )
        assertResult(List(TestingPackages.MinimalV3ModelV2PackageDefinition))(
          PackageCollection.merge(List(getRepository(packages, u)))
        )
      }

      "merge should sort packages by name" in {
        assertResult(List(
          TestingPackages.HelloWorldV3Package,
          TestingPackages.MinimalV3ModelV2PackageDefinition
        ))(PackageCollection.merge(List(getRepository(
          List(
            TestingPackages.MinimalV3ModelV2PackageDefinition,
            TestingPackages.HelloWorldV3Package
          ), Uri.parse("/uri")
        ))))
      }

      "merge should remove duplicates and retain the package definition according to spec" in {
        val repositories = List(
          getRepository(List(
            TestingPackages.MinimalV3ModelV2PackageDefinition,
            TestingPackages.MaximalV3ModelV2PackageDefinition
          ), Uri.parse("/uri")),
          getRepository(List(
            TestingPackages.MaximalV3ModelV2PackageDefinition,
            TestingPackages.HelloWorldV3Package
          ), Uri.parse("/test")),
          getRepository(List(
            TestingPackages.MaximalV4ModelPackageDefinitionV4,
            TestingPackages.MinimalV4ModelPackageDefinitionV4
          ), Uri.parse("/minimal"))
        )
        assertResult(List(
          TestingPackages.MaximalV3ModelV2PackageDefinition,
          TestingPackages.MaximalV4ModelPackageDefinitionV4,
          TestingPackages.HelloWorldV3Package,
          TestingPackages.MinimalV3ModelV2PackageDefinition,
          TestingPackages.MinimalV4ModelPackageDefinitionV4
        ))(PackageCollection.merge(repositories))
      }
    }

    "getPackageByPackageName" - {

      "package not found as repo is empty" in {
        Try(PackageCollection.getPackagesByPackageName(
          "test",
          List.empty[universe.v4.model.PackageDefinition])
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )
      }

      "invalid package name should throw" in {
        Try(PackageCollection.getPackagesByPackageName("test",
          List(TestingPackages.HelloWorldV3Package,
            TestingPackages.MinimalV3ModelV2PackageDefinition
          )
        )) shouldBe Throw(
          PackageNotFound("test").exception
        )
      }

      "found minimal" in {
        val cls = List(TestingPackages.MinimalV3ModelV2PackageDefinition)
        //val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version

        Try(PackageCollection.getPackagesByPackageName("minimal", cls)) shouldBe Return(cls)
        Try(PackageCollection.getPackagesByPackageName("MAXIMAL", cls)) shouldBe Throw(
          PackageNotFound("MAXIMAL").exception
        )
      }

      "found MAXIMAL" in {
        val minimal = List(
          TestingPackages.MinimalV3ModelV2PackageDefinition,
          TestingPackages.MinimalV3ModelV2PackageDefinition
        )
        val packages = TestingPackages.MaximalV3ModelV3PackageDefinition :: minimal

        assertResult(Return(minimal))(
          Try(PackageCollection.getPackagesByPackageName("minimal", packages))
        )
        assertResult(
          Return(List(TestingPackages.MaximalV3ModelV3PackageDefinition))
        )(
          Try(PackageCollection.getPackagesByPackageName("MAXIMAL", packages))
        )

        Try(PackageCollection.getPackagesByPackageName("test", packages)) shouldBe(
          Throw(PackageNotFound("test").exception)
        )
      }

      "multi repo multi packages" in {

        val min2 = TestingPackages.MinimalV3ModelV2PackageDefinition.copy(
          version = universe.v3.model.Version("1.2.4"))
        val max2 = TestingPackages.MaximalV3ModelV3PackageDefinition.copy(
          version = universe.v3.model.Version("9.9.9"))
        val packageDefinitions = List(
          TestingPackages.MaximalV3ModelV3PackageDefinition,
          max2,
          TestingPackages.MinimalV3ModelV2PackageDefinition,
          min2
        )

        Try(
          PackageCollection.getPackagesByPackageName("minimal", packageDefinitions).length
        ) shouldBe Return(2)

        Try(
          PackageCollection.getPackagesByPackageName("minimal", packageDefinitions)
        ) shouldBe Return(
          List(TestingPackages.MinimalV3ModelV2PackageDefinition, min2)
        )

        Try(
          PackageCollection.getPackagesByPackageName("MAXIMAL", packageDefinitions)
        ) shouldBe Return(
          List(TestingPackages.MaximalV3ModelV3PackageDefinition, max2)
        )

        Try(
          PackageCollection.getPackagesByPackageName("foobar", packageDefinitions)
        ) shouldBe Throw(
          PackageNotFound("foobar").exception
        )
      }

      "works for all packaging versions" in {
        forAll(TestingPackages.packageDefinitions) { packageDefinition =>
          PackageCollection.getPackagesByPackageName(
            packageDefinition.name,
            List(packageDefinition)
          ) shouldBe List(packageDefinition)
        }
      }
    }

    "getPackagesByPackageVersion" - {

      "not found" in {
        Try(PackageCollection.getPackagesByPackageVersion(
          "test",
          Some(TestingPackages.HelloWorldV3Package.version),
          List.empty[(universe.v4.model.PackageDefinition, Uri)])
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(PackageCollection.getPackagesByPackageVersion(
          "test",
          None,
          List.empty[(universe.v4.model.PackageDefinition, Uri)])
          ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(PackageCollection.getPackagesByPackageVersion(
          "test",
          Some(TestingPackages.HelloWorldV3Package.version),
          List((TestingPackages.HelloWorldV3Package, Uri.parse("/test")))
        )) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(PackageCollection.getPackagesByPackageVersion(
          "test",
          None,
          List((TestingPackages.HelloWorldV3Package, Uri.parse("/test")))
        )) shouldBe Throw(
          PackageNotFound("test").exception
        )
      }

      "invalid package should throw" in {
        val packages = List((TestingPackages.HelloWorldV3Package, Uri.parse("/test")))

        Try(PackageCollection.getPackagesByPackageVersion(
          "test",
          Some(TestingPackages.HelloWorldV3Package.version),
          packages)
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(PackageCollection.getPackagesByPackageVersion(
          "test",
          None,
          packages)
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(PackageCollection.getPackagesByPackageVersion(
          "",
          Some(TestingPackages.HelloWorldV3Package.version),
          packages)
        ) shouldBe Throw(
          PackageNotFound("").exception
        )

        Try(PackageCollection.getPackagesByPackageVersion(
          TestingPackages.HelloWorldV3Package.name,
          Some(universe.v3.model.Version("6.7.8")),
          packages)
        ) shouldBe Throw(
          VersionNotFound(
            TestingPackages.HelloWorldV3Package.name,
            universe.v3.model.Version("6.7.8")
          ).exception
        )
      }

      "found minimal" in {

        val u = Uri.parse("/uri")
        val cls = List((TestingPackages.MinimalV3ModelV2PackageDefinition, u))
        val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version

        Try(PackageCollection.getPackagesByPackageVersion(
          "minimal",
          Some(ver),cls
        )) shouldBe Return((TestingPackages.MinimalV3ModelV2PackageDefinition, u))

        Try(PackageCollection.getPackagesByPackageVersion("minimal", None, cls)) shouldBe Return(
          (TestingPackages.MinimalV3ModelV2PackageDefinition, u)
        )

        val bad = TestingPackages.MaximalV3ModelV3PackageDefinition.version
        Try(
          PackageCollection.getPackagesByPackageVersion("minimal", Some(bad), cls)
        ) shouldBe Throw(
          VersionNotFound("minimal", bad).exception
        )
        Try(PackageCollection.getPackagesByPackageVersion("test", Some(ver), cls)) shouldBe Throw(
          PackageNotFound("test").exception
        )
      }

      "found MAXIMAL" in {
        val u = Uri.parse("/uri")
        val cls = List(
          (TestingPackages.MinimalV3ModelV2PackageDefinition, u),
          (TestingPackages.MaximalV3ModelV3PackageDefinition, u)
        )
        val ver = TestingPackages.MaximalV3ModelV3PackageDefinition.version
        Try(
          PackageCollection.getPackagesByPackageVersion("MAXIMAL", Some(ver), cls)
        ) shouldBe Return(
          (TestingPackages.MaximalV3ModelV3PackageDefinition, u)
        )
        val bad = TestingPackages.MinimalV3ModelV2PackageDefinition.version
        Try(
          PackageCollection.getPackagesByPackageVersion("MAXIMAL", Some(bad), cls)
        ) shouldBe Throw(
          VersionNotFound("MAXIMAL", bad).exception
        )
      }

      "works for a single version with single package" in {
        val u = Uri.parse("/test")
        val packages = List((TestingPackages.MinimalV3ModelV2PackageDefinition, u))

        assertResult(
          Return((TestingPackages.MinimalV3ModelV2PackageDefinition, u))
        )(Try(PackageCollection.getPackagesByPackageVersion(
          TestingPackages.MinimalV3ModelV2PackageDefinition.name,
          Some(TestingPackages.MinimalV3ModelV2PackageDefinition.version),
          packages)
        ))
      }

      "works for a single version with multiple versions of multiple packages" in {
        val u = Uri.parse("/test")
        val packages = List((TestingPackages.MinimalV3ModelV2PackageDefinition, u))
        packages :+ TestingPackages.MinimalV4ModelV4PackageDefinition
        packages :+ TestingPackages.MinimalV4ModelV4PackageDefinition.copy(
          version = universe.v3.model.Version("7.7.7")
        )

        assertResult(
          Return((TestingPackages.MinimalV3ModelV2PackageDefinition, u))
        )(Try(PackageCollection.getPackagesByPackageVersion(
          TestingPackages.MinimalV3ModelV2PackageDefinition.name,
          Some(TestingPackages.MinimalV3ModelV2PackageDefinition.version),
          packages)
        ))
      }

      "works for all packaging versions" in {
        val u = Uri.parse("/test")
        forAll(TestingPackages.packageDefinitions) { packageDefinition =>
          PackageCollection.getPackagesByPackageVersion(
            packageDefinition.name,
            Some(packageDefinition.version),
            List((packageDefinition, u))
          ) shouldBe ((packageDefinition, u))
        }
      }
    }

    "search" - {

      "not found" in {
        assertResult(Return(Nil))(Try(PackageCollection.search(
            Some("test"),
            List.empty[universe.v4.model.PackageDefinition])
        ))
        assertResult(Return(Nil))(Try(PackageCollection.search(
          Some("mini*.+"),
          List.empty[universe.v4.model.PackageDefinition])
        ))
      }

      "all" in {

        val all = List(TestingPackages.MaximalV3ModelV3PackageDefinition,
          TestingPackages.MinimalV3ModelV2PackageDefinition)

        Try(PackageCollection.search(None, all).map(_.name)) shouldBe
          Return(List("MAXIMAL","minimal"))

        Try(PackageCollection.search(Some("minimal"), all).map(_.name)) shouldBe
          Return(List("minimal"))

        assertResult(Return(2))(Try(PackageCollection.search(None, all).length))

        val min2 = TestingPackages.MinimalV3ModelV2PackageDefinition.copy(
          version = universe.v3.model.Version("1.2.4")
        )
        val max2 = TestingPackages.MaximalV3ModelV3PackageDefinition.copy(
          version = universe.v3.model.Version("9.9.9")
        )
        val minver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
        val maxver = TestingPackages.MaximalV3ModelV3PackageDefinition.version
        val min2ver = min2.version
        val max2ver = max2.version
        val clientdata = List(
          TestingPackages.MaximalV3ModelV3PackageDefinition,
          max2,
          TestingPackages.MinimalV3ModelV2PackageDefinition,
          min2
        )

        assertResult(
          Return(List("MAXIMAL", "minimal"))
        )(Try(PackageCollection.search(None, clientdata).map(_.name).sorted))

        assertResult(
          Return(List("minimal"))
        )(Try(PackageCollection.search(Some("minimal"), clientdata).map(_.name)))

        assertResult(
          Return(List(Set(minver, min2ver)))
        )(Try(PackageCollection.search(Some("minimal"), clientdata).map(_.versions.keys)))

        assertResult(
          Return(List(Set(maxver, max2ver)))
        )(Try(PackageCollection.search(Some("MAXIMAL"), clientdata).map(_.versions.keys)))
      }

      "found" in {
        val l = List(TestingPackages.MinimalV3ModelV2PackageDefinition)
        assertResult("minimal")(PackageCollection.search(Some("minimal"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("mini*mal"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("min*mal"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("minimal*"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("*minimal"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("*minimal*"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("*inimal"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("minima*"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("minima**"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("**minimal"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("**minimal**"), l).head.name)
        assertResult("minimal")(PackageCollection.search(Some("**mi**mal**"), l).head.name)
      }

      "by tag" in {
        val l = List(TestingPackages.MaximalV3ModelV3PackageDefinition)
        assertResult("MAXIMAL")(PackageCollection.search(Some("all"), l).head.name)
        assertResult("MAXIMAL")(PackageCollection.search(Some("thing*"), l).head.name)
      }

    }

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

  "createRegex" in {
    assertResult("^\\Qminimal\\E$")(PackageCollection.createRegex("minimal").toString)
    assertResult("^\\Qmin\\E.*\\Qmal\\E$")(PackageCollection.createRegex("min*mal").toString)
    assertResult("^\\Qmini\\E.*\\Q.+\\E$")(PackageCollection.createRegex("mini*.+").toString)
    assertResult("^\\Qminimal\\E.*$")(PackageCollection.createRegex("minimal*").toString)
    assertResult("^\\Qminimal\\E.*.*$")(PackageCollection.createRegex("minimal**").toString)
  }
}
