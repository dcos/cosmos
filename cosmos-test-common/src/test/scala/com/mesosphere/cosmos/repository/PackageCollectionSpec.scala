package com.mesosphere.cosmos

import com.mesosphere.Generators
import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.http.OriginHostScheme
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks

final class PackageCollectionSpec extends FreeSpec
  with Matchers
  with PropertyChecks {

  import PackageCollectionSpec._

  "Queries on PackageCollection" - {

    import universe.v3.model.Version
    import universe.v3.model.ReleaseVersion

    val a_99_2 = buildV4Package("a", Version("99"), ReleaseVersion(2))
    val a_88_1 = buildV4Package("a", Version("88"), ReleaseVersion(1))
    val a_99_3 = buildV4Package("a", Version("99"), ReleaseVersion(3))

    val b_99_2 = buildV4Package("b", Version("99"), ReleaseVersion(2))
    val b_77_2 = buildV4Package("b", Version("77"), ReleaseVersion(2))
    val b_66_1 = buildV4Package("b", Version("66"), ReleaseVersion(1))

    val c_99_2 = buildV4Package("c", Version("99"), ReleaseVersion(2))
    val c_55_2 = buildV4Package("c", Version("55"), ReleaseVersion(2))
    val c_44_1 = buildV4Package("c", Version("44"), ReleaseVersion(1))

    "merge" - {

      "should success on empty repositories" in {
        assertResult(
          List.empty
        )(
          PackageCollection.merge(List(getRepository(List.empty)))
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
          getRepository(List(a_99_3, b_66_1))
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

      "should sort by smallest index before releaseVersion" in {
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
            List.empty,
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
            List.empty,
            "test",
            Some(a_99_2.version)
          )
        ) shouldBe Throw(
          PackageNotFound("test").exception
        )

        Try(
          PackageCollection.getPackagesByPackageVersion(
            List.empty,
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
            a_88_1.name,
            Some(a_88_1.version)
          )
        ) shouldBe Throw(
          VersionNotFound(
            a_88_1.name,
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
          b_77_2.name,
          Some(b_77_2.version)
        )) shouldBe Return((b_77_2, t2))

        Try(PackageCollection.getPackagesByPackageVersion(
          sameNamePackages,
          b_66_1.name,
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

      implicit val originInfo : OriginHostScheme = OriginHostScheme("localhost", "http")

      "not found" in {
        assertResult(Return(Nil)
        )(Try(PackageCollection.search(List.empty, Some("test"))))

        assertResult(Return(Nil)
        )(Try(PackageCollection.search(List.empty, Some("mini*.+"))))
      }

      "all" in {

        val all = List(TestingPackages.MaximalV3ModelV3PackageDefinition,
          TestingPackages.MinimalV3ModelV2PackageDefinition)

        Try(PackageCollection.search(all, None).map(_.name)) shouldBe
          Return(List("MAXIMAL","minimal"))

        Try(PackageCollection.search(all, Some("minimal")).map(_.name)) shouldBe
          Return(List("minimal"))

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

        assertResult(Return(List("MAXIMAL", "minimal"))
        )(Try(PackageCollection.search(clientdata, None).map(_.name).sorted))

        assertResult(Return(List("minimal"))
        )(Try(PackageCollection.search(clientdata, Some("minimal")).map(_.name)))

        assertResult(Return(List(Set(minver, min2ver)))
        )(Try(PackageCollection.search(clientdata, Some("minimal")).map(_.versions.keys)))

        assertResult(Return(List(Set(maxver, max2ver)))
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

    "upgradesTo" - {

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

    "allUrls" - {
      import universe.v3.model.V3Resource
      import universe.v3.model.V2Resource
      import universe.v4.model.Repository

      val uri: Uri = Uri.parse("/irrelevant")

      "All the urls should be returned for single package" in {
        forAll(Generators.genV3ResourceTestData()) { case (expected, assets, images, clis) =>
          val v4package = buildV4Package(resource = Some(V3Resource(Some(assets), Some(images), Some(clis))))
          assertResult(expected)(PackageCollection.allUrls(List(getRepository(List(v4package)))))
        }
      }

      "All the urls should be returned for multiple package" in {
        forAll(
          Generators.genV3ResourceTestData(),
          Generators.genV3ResourceTestData(),
          Generators.genV2ResourceTestData()
        ) { case (
            (expected4, assets4, images4, cli4),
            (expected3, assets3, images3, cli3),
            (expected2, assets2, images2)
          ) =>
          val v4package = buildV4Package(resource = Some(V3Resource(Some(assets4), Some(images4), Some(cli4))))
          val v3package = buildV3Package(resource = Some(V3Resource(Some(assets3), Some(images3), Some(cli3))))
          val v2package = buildV2Package(resource = Some(V2Resource(Some(assets2), Some(images2))))
          val expected = expected4 ++ expected3 ++ expected2
          assertResult(expected)(PackageCollection.allUrls(
            List(getRepository(List(v4package, v3package, v2package))))
          )
          assertResult(expected)(PackageCollection.allUrls(
            List(
              getRepository(List(v4package)),
              (Repository(List(v3package)), uri),
              (Repository(List(v2package)), uri)
            )
          ))
          assertResult(expected)(PackageCollection.allUrls(
            List(getRepository(List(v4package)),
              (Repository(List(v3package, v2package)), uri)
            )
          ))
        }
      }

      "Duplicate urls should be removed" in {
        forAll(Generators.genV3ResourceTestData()) { case (expected, assets4, images4, cli4) =>
          val v4package = buildV4Package(resource = Some(V3Resource(Some(assets4), Some(images4), Some(cli4))))
          val v3package = buildV3Package(resource = Some(V3Resource(Some(assets4), Some(images4), Some(cli4))))
          assertResult(expected)(PackageCollection.allUrls(
            List(getRepository(List(v4package, v3package))))
          )
          assertResult(expected)(PackageCollection.allUrls(
            List(
              getRepository(List(v4package)),
              (Repository(List(v3package)), uri)
            )
          ))
          assertResult(expected)(PackageCollection.allUrls(
            List((Repository(List(v3package, v4package)), uri))
          ))
        }
      }

      "Packages with no urls should return an empty set" in {
        assertResult(Set())(PackageCollection.allUrls(List(getRepository(List(buildV4Package())))))
        assertResult(Set())(PackageCollection.allUrls(List(getRepository(List(buildV3Package())))))
        assertResult(Set())(PackageCollection.allUrls(List(getRepository(List(buildV2Package())))))
      }

      "Repo with zero packages should return an empty set" in {
        assertResult(Set())(PackageCollection.allUrls(List.empty))
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

object PackageCollectionSpec {
  def getRepository(
    packageDefinitions : List[universe.v4.model.PackageDefinition],
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

  def buildV4Package(
    name: String = "whatever4",
    version : universe.v3.model.Version = universe.v3.model.Version("424"),
    releaseVersion: universe.v3.model.ReleaseVersion = universe.v3.model.ReleaseVersion(2),
    resource: Option[universe.v3.model.V3Resource] = None
  ): universe.v4.model.PackageDefinition = {
    universe.v4.model.V4Package(
      name = name,
      version = version,
      releaseVersion = releaseVersion,
      maintainer = "cosmos@mesosphere.com",
      description = "a package definition going through a life span of tests",
      resource = resource
    )
  }

  def buildV3Package(
    name: String = "whatever3",
    version : universe.v3.model.Version = universe.v3.model.Version("423"),
    releaseVersion: universe.v3.model.ReleaseVersion = universe.v3.model.ReleaseVersion(1),
    resource: Option[universe.v3.model.V3Resource] = None
  ): universe.v3.model.V3Package = {
    universe.v3.model.V3Package(
      name = name,
      version = version,
      releaseVersion = releaseVersion,
      maintainer = "cosmos@mesosphere.com",
      description = "a package definition going through a life span of tests",
      resource = resource
    )
  }

  def buildV2Package(
    name: String = "whatever2",
    version : universe.v3.model.Version = universe.v3.model.Version("422"),
    releaseVersion: universe.v3.model.ReleaseVersion = universe.v3.model.ReleaseVersion(0),
    resource: Option[universe.v3.model.V2Resource] = None
  ): universe.v3.model.V2Package = {
    universe.v3.model.V2Package(
      name = name,
      version = version,
      releaseVersion = releaseVersion,
      maintainer = "cosmos@mesosphere.com",
      description = "a package definition going through a life span of tests",
      marathon = universe.v3.model.Marathon(
        v2AppMustacheTemplate = ByteBuffer.wrap("brief template".getBytes(StandardCharsets.UTF_8))
      ),
      resource = resource
    )
  }
}
