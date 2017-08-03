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
    uri: Uri = Uri.parse("/irrelevant")
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

  def singlePackageDefinition(
    name: String,
    version : universe.v3.model.Version,
    releaseVersion: universe.v3.model.ReleaseVersion
  ): universe.v4.model.PackageDefinition = {
    universe.v4.model.V4Package(
      name = name,
      version = version,
      releaseVersion = releaseVersion,
      maintainer = "cosmos@mesosphere.com",
      description = "a package definition going through a life span of tests"
    )
  }

  "Queries on PackageCollection" - {

    import universe.v3.model.Version
    import universe.v3.model.ReleaseVersion
    import universe.v4.model.PackageDefinition

    val a_99_2 = singlePackageDefinition("a", Version("99"), ReleaseVersion(2))
    val a_88_1 = singlePackageDefinition("a", Version("88"), ReleaseVersion(1))
    val a_99_3 = singlePackageDefinition("a", Version("99"), ReleaseVersion(3))

    val b_99_2 = singlePackageDefinition("b", Version("99"), ReleaseVersion(2))
    val b_77_2 = singlePackageDefinition("b", Version("77"), ReleaseVersion(2))
    val b_66_1 = singlePackageDefinition("b", Version("66"), ReleaseVersion(1))

    val c_99_2 = singlePackageDefinition("c", Version("99"), ReleaseVersion(2))
    val c_55_2 = singlePackageDefinition("c", Version("55"), ReleaseVersion(2))
    val c_44_1 = singlePackageDefinition("c", Version("44"), ReleaseVersion(1))

    "merge" - {

      "should success on empty repositories" in {
        assertResult(
          List.empty[universe.v4.model.PackageDefinition]
        )(
          PackageCollection.merge(List(getRepository()))
        )
      }

      "should remove duplicate packages" in {
        val packages = List(
          a_99_2,
          a_99_2,
          b_66_1
        )
        assertResult(List(a_99_2, b_66_1))(PackageCollection.merge(List(getRepository(packages))))
      }

      "should remove packages with same coordinates" in {
        val repositories = List(
          getRepository(List(a_88_1, a_99_2)),
          getRepository(List(a_88_1, b_66_1))
        )
        assertResult(List(a_99_2, a_88_1, b_66_1))(PackageCollection.merge(repositories))
      }

      "should sort packages by name" in {
        val packages = List(c_44_1, b_66_1, a_88_1)
        assertResult(packages.reverse)(PackageCollection.merge(List(getRepository(packages))))
        assertResult(List(
          a_99_2,
          b_99_2,
          c_99_2
        ))(PackageCollection.merge(List(
          getRepository(List(c_99_2)),
          getRepository(List(b_99_2)),
          getRepository(List(a_99_2))
        )))
      }

      "should sort packages by repository index" in {
        assertResult(List(
          a_99_2,
          a_88_1,
          b_66_1,
          b_77_2,
          c_44_1,
          c_55_2
        ))(PackageCollection.merge(List(
          getRepository(List(c_44_1, a_99_2)),
          getRepository(List(c_55_2, b_66_1)),
          getRepository(List(a_88_1, b_77_2))
        )))
      }

      "should sort packages by releaseVersion" in {
        assertResult(
          List(a_99_3, a_88_1, b_77_2, b_66_1, c_55_2, c_44_1)
        )(PackageCollection.merge(List(
          getRepository(List(a_88_1, a_99_3, b_66_1, b_77_2, c_44_1, c_55_2))
        )))

        assertResult(
          List(a_99_2)
        )(PackageCollection.merge(List(
          getRepository(List(a_99_2)),
          getRepository(List(a_99_3))
        )))

        assertResult(
          List(a_99_3)
        )(PackageCollection.merge(List(
          getRepository(List(a_99_3)),
          getRepository(List(a_99_2))
        )))
      }

      "should merge the package definitions in order of +name, +index and -releaseVersion" in {

        assertResult(List(
          a_88_1,
          a_99_2
        ))(PackageCollection.merge(List(
          getRepository(List(a_88_1)),
          getRepository(List(a_99_2))
        )))

        val repositories = List(
          getRepository(List(b_77_2, a_88_1)),
          getRepository(List(a_99_2, b_66_1)),
          getRepository(List(c_55_2, c_44_1))
        )
        assertResult(List(
          a_88_1,
          a_99_2,
          b_77_2,
          b_66_1,
          c_55_2,
          c_44_1
        ))(PackageCollection.merge(repositories))

      }
    }

    "getPackageByPackageName" - {

      "package not found as repo is empty" in {
        Try(
          PackageCollection.getPackagesByPackageName(
            List.empty[universe.v4.model.PackageDefinition],
            "test"
          )
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )
      }

      "name not found in not empty collection" in {
        Try(
          PackageCollection.getPackagesByPackageName(
            List(a_99_2, a_99_3),
            "test"
          )
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )
      }

      "found package by name" in {
        Try(PackageCollection.getPackagesByPackageName(
          List(a_99_2),
          a_99_2.name
        )) shouldBe Return(List(a_99_2))

        Try(PackageCollection.getPackagesByPackageName(
          List(a_99_2),
          b_99_2.name
        )) shouldBe Throw(PackageNotFound(b_99_2.name).exception)

        Try(PackageCollection.getPackagesByPackageName(
          List(a_99_2, a_88_1, a_99_3),
          a_99_2.name
        )) shouldBe Return(List(a_99_2, a_88_1, a_99_3))

        Try(PackageCollection.getPackagesByPackageName(
          List(a_99_2, a_88_1, a_99_3),
          b_99_2.name
        )) shouldBe Throw(PackageNotFound(b_99_2.name).exception)

        Try(PackageCollection.getPackagesByPackageName(
          List(a_99_2, a_88_1, a_99_3, b_66_1, b_77_2, c_44_1, c_55_2),
          a_99_2.name
        )) shouldBe Return(List(a_99_2, a_88_1, a_99_3))

        Try(PackageCollection.getPackagesByPackageName(
          List(a_99_2, a_88_1, a_99_3, c_44_1, c_55_2),
          b_99_2.name
        )) shouldBe Throw(PackageNotFound(b_99_2.name).exception)
      }

      "works for all packaging versions" in {
        forAll(TestingPackages.packageDefinitions) { packageDefinition =>
          PackageCollection.getPackagesByPackageName(
            List(packageDefinition),
            packageDefinition.name
          ) shouldBe List(packageDefinition)
        }
      }
    }

    "getPackagesByPackageVersion" - {

      "not found" in {
        Try(
          PackageCollection.getPackagesByPackageVersion(
            List.empty[(universe.v4.model.PackageDefinition, Uri)],
            "test",
            Some(a_99_2.version)
          )
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(
          PackageCollection.getPackagesByPackageVersion(
            List.empty[(universe.v4.model.PackageDefinition, Uri)],
            "test",
            None
          )
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(
          PackageCollection.getPackagesByPackageVersion(
            List((a_99_2, Uri.parse("/test"))),
            "test",
            Some(a_99_2.version)
          )
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(
          PackageCollection.getPackagesByPackageVersion(
            List((a_99_2, Uri.parse("/test"))),
            "test",
            None
          )
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(
          PackageCollection.getPackagesByPackageVersion(
            List((a_99_2, Uri.parse("/test")), (a_99_3, Uri.parse("/test"))),
            a_99_2.name,
            Some(a_88_1.version)
          )
        ) shouldBe Throw(
          VersionNotFound(
            a_99_2.name,
            a_88_1.version
          ).exception
        )
      }

      val t0 = Uri.parse("/test0")
      val t1 = Uri.parse("/test1")
      val t2 = Uri.parse("/test2")

      "found package" in {

        val u = Uri.parse("/uri")
        val packages = List((a_99_2, u))
        val ver = a_99_2.version

        Try(PackageCollection.getPackagesByPackageVersion(
            packages,
            a_99_2.name,
            Some(ver)
        )) shouldBe Return((a_99_2, u))

        Try(PackageCollection.getPackagesByPackageVersion(
          packages,
          a_99_2.name,
          None
        )) shouldBe Return((a_99_2, u))

        val morePackages = List(
          (a_99_2, t0),
          (b_99_2, t2),
          (c_99_2, t1)
        )

        Try(PackageCollection.getPackagesByPackageVersion(
          morePackages,
          a_99_2.name,
          Some(ver)
        )) shouldBe Return((a_99_2, t0))

        Try(PackageCollection.getPackagesByPackageVersion(
          morePackages,
          a_99_2.name,
          None
        )) shouldBe Return((a_99_2, t0))
      }

      "works for packages with same name" in {
        val sameNamePackages = List(
          (b_66_1, t0),
          (b_77_2, t2),
          (b_99_2, t1)
        )

        Try(PackageCollection.getPackagesByPackageVersion(
          sameNamePackages,
          b_99_2.name,
          Some(b_77_2.version)
        )) shouldBe Return((b_77_2, t2))

        Try(PackageCollection.getPackagesByPackageVersion(
          sameNamePackages,
          b_99_2.name,
          None
        )) shouldBe Return((b_66_1, t0))
      }

      "works for all packaging versions" in {
        val u = Uri.parse("/test")
        forAll(TestingPackages.packageDefinitions) { packageDefinition =>
          PackageCollection.getPackagesByPackageVersion(
            List((packageDefinition, u)),
            packageDefinition.name,
            Some(packageDefinition.version)
          ) shouldBe ((packageDefinition, u))
        }
      }
    }

    "search" - {

      "not found" in {
        assertResult(
          Return(Nil)
        )(
          Try(
            PackageCollection.search(List.empty[universe.v4.model.PackageDefinition], Some("test"))
          )
        )

        assertResult(
          Return(Nil)
        )(
          Try(
            PackageCollection.search(List.empty[universe.v4.model.PackageDefinition], Some("mini*.+"))
          )
        )
      }

      "all" in {

        val all = List(TestingPackages.MaximalV3ModelV3PackageDefinition,
          TestingPackages.MinimalV3ModelV2PackageDefinition)

        Try(PackageCollection.search(all, None).map(_.name)) shouldBe
          Return(List("MAXIMAL","minimal"))

        Try(PackageCollection.search(all, Some("minimal")).map(_.name)) shouldBe
          Return(List("minimal"))

        assertResult(Return(2))(Try(PackageCollection.search(all, None).length))

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
        )(Try(PackageCollection.search(clientdata, None).map(_.name).sorted))

        assertResult(
          Return(List("minimal"))
        )(Try(PackageCollection.search(clientdata, Some("minimal")).map(_.name)))

        assertResult(
          Return(List(Set(minver, min2ver)))
        )(Try(PackageCollection.search(clientdata, Some("minimal")).map(_.versions.keys)))

        assertResult(
          Return(List(Set(maxver, max2ver)))
        )(Try(PackageCollection.search(clientdata, Some("MAXIMAL")).map(_.versions.keys)))
      }

      "found" in {
        val l = List(TestingPackages.MinimalV3ModelV2PackageDefinition)
        assertResult("minimal")(PackageCollection.search(l, Some("minimal")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("mini*mal")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("min*mal")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("minimal*")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("*minimal")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("*minimal*")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("*inimal")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("minima*")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("minima**")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("**minimal")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("**minimal**")).head.name)
        assertResult("minimal")(PackageCollection.search(l, Some("**mi**mal**")).head.name)
      }

      "by tag" in {
        val l = List(TestingPackages.MaximalV3ModelV3PackageDefinition)
        assertResult("MAXIMAL")(PackageCollection.search(l, Some("all")).head.name)
        assertResult("MAXIMAL")(PackageCollection.search(l, Some("thing*")).head.name)
      }

      "Search results are sorted by selected field and then package name" in {

        val p = TestingPackages.MaximalV3ModelV3PackageDefinition.copy(selected = Some(false))
        val q = TestingPackages.MinimalV3ModelV2PackageDefinition.copy(selected = Some(false))
        val r = TestingPackages.MinimalV4ModelV4PackageDefinition.copy(selected = Some(true))
        val s = TestingPackages.HelloWorldV3Package.copy(selected = Some(true))

        val expectedNameList = List(s.name, r.name, p.name, q.name)

        assertResult(expectedNameList)(
          PackageCollection.search(List(q, p, r, s), Some("*")).map(_.name)
        )

        assertResult(expectedNameList)(
          PackageCollection.search(List(s, r, q, p), Some("*")).map(_.name)
        )
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
          val upgradeVersions = PackageCollection.upgradesTo(upgrades, name, version)
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
            assert(PackageCollection.upgradesTo(packages, name, version).isEmpty)
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
            assert(PackageCollection.upgradesTo(packages, name, version).isEmpty)
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
