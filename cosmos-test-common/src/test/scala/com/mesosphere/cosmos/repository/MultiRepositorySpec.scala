package com.mesosphere.cosmos

import cats.data.Ior
import com.mesosphere.cosmos.error.PackageNotFound
import com.mesosphere.cosmos.error.VersionNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.PackageSourcesStorage
import com.mesosphere.cosmos.repository.UniverseClient
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import com.mesosphere.cosmos.test.TestUtil.Anonymous
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.model.Version
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.netaporter.uri.Uri
import com.twitter.util.Await
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

final class MultiRepositorySpec extends FreeSpec with Matchers with TableDrivenPropertyChecks {

  case class TestClient(repos: List[PackageRepository] = Nil, ls: List[universe.v4.model.PackageDefinition] = Nil)
    extends UniverseClient {
    def apply(repo: PackageRepository)(implicit session: RequestSession): Future[universe.v4.model.Repository] = {
      Future(universe.v4.model.Repository(repos.filter( _ == repo).flatMap(_ => ls)))
    }
  }

  case class TestStorage(initial:List[PackageRepository] = Nil) extends PackageSourcesStorage {
    var cache: List[PackageRepository] = initial
    def read(): Future[List[PackageRepository]] =  Future { cache }
    def readCache(): Future[List[PackageRepository]] =  Future { cache }
    def add(index: Option[Int], packageRepository: PackageRepository): Future[List[PackageRepository]] = ???
    def delete(nameOrUri: Ior[String, Uri]): Future[List[PackageRepository]] =  ???
  }

  case class TestMultiClient(
    repos: List[(PackageRepository,List[universe.v4.model.PackageDefinition])] = Nil
  ) extends UniverseClient {
    def apply(
      repo: PackageRepository
    )(implicit
      session: RequestSession
    ): Future[universe.v4.model.Repository] = Future {
      universe.v4.model.Repository(repos.filter( _._1 == repo).flatMap(_._2))
    }
  }
  "getRepository" - {
    "empty" in {
      val c = new MultiRepository(TestStorage(), TestClient())
      assertResult(None)(Await.result(c.getRepository(Uri.parse("/test"))))
    }
    "many" in {
      val one = PackageRepository("one", Uri.parse("/one"))
      val two = PackageRepository("two", Uri.parse("/two"))
      val c = new MultiRepository(TestStorage(List(one,two)), TestClient())
      assertResult(None)(Await.result(c.getRepository(Uri.parse("/zero"))))
      assertResult(one)(Await.result(c.getRepository(Uri.parse("/one"))).get.repository)
      assertResult(two)(Await.result(c.getRepository(Uri.parse("/two"))).get.repository)
    }
    "many same uri" in {
      val one = PackageRepository("one", Uri.parse("/same"))
      val two = PackageRepository("two", Uri.parse("/same"))
      val c = new MultiRepository(TestStorage(List(one,two)), TestClient())
      assertResult(None)(Await.result(c.getRepository(Uri.parse("/zero"))))
      assertResult(two.uri)(Await.result(c.getRepository(Uri.parse("/same"))).get.repository.uri)
    }
  }

  "queries" - {
    "not found" in {
      val c = new MultiRepository(TestStorage(), TestClient())
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version

      Try(Await.result(c.getPackagesByPackageName("test"))) shouldBe Throw(
        new PackageNotFound("test").exception
      )

      Try(Await.result(c.getPackageByPackageVersion("test", Some(ver)))) shouldBe Throw(
        new VersionNotFound("test", ver).exception
      )

      Try(Await.result(c.getPackageByPackageVersion("test", None))) shouldBe Throw(
        new PackageNotFound("test").exception
      )

      Try(Await.result(c.search(Some("test")))) shouldBe Return(Nil)

      Try(Await.result(c.search(None))) shouldBe Return(Nil)
    }

    "found minimal" in {
      val u = Uri.parse("/test")
      val repos = List(PackageRepository("minimal", u))
      val storage = TestStorage(repos)
      val cls = List(TestingPackages.MinimalV3ModelV2PackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version

      Try(Await.result(c.getPackagesByPackageName("minimal"))) shouldBe Return(cls)
      Try(Await.result(c.getPackagesByPackageName("MAXIMAL"))) shouldBe Throw(
        PackageNotFound("MAXIMAL").exception
      )

      Try(Await.result(c.getPackageByPackageVersion("minimal", None))) shouldBe Return(
        (cls.head, u)
      )
      Try(Await.result(c.getPackageByPackageVersion("minimal", Some(ver)))) shouldBe Return(
        (cls.head, u)
      )

      Try(Await.result(c.search(None)).map(_.name)) shouldBe Return(List("minimal"))
      Try(Await.result(c.search(Some("minimal"))).map(_.name)) shouldBe Return(List("minimal"))
    }

    "invalid repo" in {
      val invalid = List(PackageRepository("invalid", Uri.parse("/invalid")))
      val repos = List(PackageRepository("minimal", Uri.parse("/test")))
      val storage = TestStorage(repos)
      val cls = List(TestingPackages.MinimalV3ModelV2PackageDefinition)
      val client = TestClient(invalid, cls)
      val c = new MultiRepository(storage, client)
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version

      Try(Await.result(c.getPackagesByPackageName("minimal"))) shouldBe Throw(
        PackageNotFound("minimal").exception
      )

      Try(Await.result(c.getPackageByPackageVersion("minimal", None))) shouldBe Throw(
        PackageNotFound("minimal").exception
      )

      Try(Await.result(c.getPackageByPackageVersion("minimal", Some(ver)))) shouldBe Throw(
        VersionNotFound("minimal",ver).exception
      )
    }

    "wrong query" in {
      val repos = List(PackageRepository("valid", Uri.parse("/valid")))
      val storage = TestStorage(repos)
      val cls = List(TestingPackages.MinimalV3ModelV2PackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      val ver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      val badver = TestingPackages.MaximalV3ModelV3PackageDefinition.version

      Try(Await.result(c.getPackagesByPackageName("MAXIMAL"))) shouldBe Throw(
        PackageNotFound("MAXIMAL").exception
      )

      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", None))) shouldBe Throw(
        PackageNotFound("MAXIMAL").exception
      )

      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(ver)))) shouldBe Throw(
        VersionNotFound("MAXIMAL", ver).exception
      )

      Try(Await.result(c.getPackageByPackageVersion("minimal", Some(badver)))) shouldBe Throw(
        VersionNotFound("minimal", badver).exception
      )
    }

    "from many" in {
      val u = Uri.parse("/valid")
      val repos = List(PackageRepository("valid", u))
      val storage = TestStorage(repos)
      val cls = List(TestingPackages.MaximalV3ModelV3PackageDefinition, TestingPackages.MinimalV3ModelV2PackageDefinition)
      val client = TestClient(repos, cls)
      val c = new MultiRepository(storage, client)
      val minver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      val minexp = (TestingPackages.MinimalV3ModelV2PackageDefinition, u)
      val maxver = TestingPackages.MaximalV3ModelV3PackageDefinition.version

      val expect = List(TestingPackages.MinimalV3ModelV2PackageDefinition)

      Try(Await.result(c.getPackagesByPackageName("minimal"))) shouldBe Return(expect)
      Try(Await.result(c.getPackageByPackageVersion("minimal", None))) shouldBe Return(minexp)
      Try(Await.result(c.getPackageByPackageVersion("minimal", Some(minver)))) shouldBe Return(
        minexp
      )
      Try(Await.result(c.getPackageByPackageVersion("minimal", Some(maxver)))) shouldBe Throw(
        VersionNotFound("minimal", maxver).exception
      )

      val maxexp = (TestingPackages.MaximalV3ModelV3PackageDefinition, u)

      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", None))) shouldBe Return(maxexp)
      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(maxver)))) shouldBe Return(
        maxexp
      )
      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(minver)))) shouldBe Throw(
        VersionNotFound("MAXIMAL", minver).exception
      )

      Try(Await.result(c.search(None)).map(_.name).sorted) shouldBe Return(
        List("MAXIMAL", "minimal")
      )
      Try(Await.result(c.search(Some("minimal"))).map(_.name)) shouldBe Return(List("minimal"))
      Try(Await.result(c.search(Some("MAXIMAL"))).map(_.name)) shouldBe Return(List("MAXIMAL"))
    }

    "multi repo multi same packages" in {
      val rmax = PackageRepository("max", Uri.parse("/MAXIMAL"))
      val rmin = PackageRepository("min", Uri.parse("/minimal"))
      val min2 = TestingPackages.MinimalV3ModelV2PackageDefinition.copy(version = Version("1.2.4"))
      val max2 = TestingPackages.MaximalV3ModelV3PackageDefinition.copy(version = Version("9.9.9"))
      val clientdata = List((rmax, List(TestingPackages.MaximalV3ModelV3PackageDefinition,max2)),
                            (rmin, List(TestingPackages.MinimalV3ModelV2PackageDefinition,min2)))
      val storage = TestStorage(List(rmax,rmin))
      val client = TestMultiClient(clientdata)
      val c = new MultiRepository(storage, client)

      Try(Await.result(c.getPackagesByPackageName("minimal")).length) shouldBe Return(2)
      Try(Await.result(c.getPackagesByPackageName("minimal"))) shouldBe Return(
        List(TestingPackages.MinimalV3ModelV2PackageDefinition,min2)
      )
      Try(Await.result(c.getPackagesByPackageName("MAXIMAL"))) shouldBe Return(
        List(TestingPackages.MaximalV3ModelV3PackageDefinition,max2)
      )
      Try(Await.result(c.getPackagesByPackageName("foobar"))) shouldBe Throw(
        PackageNotFound("foobar").exception
      )

      val minver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      val minexp = (TestingPackages.MinimalV3ModelV2PackageDefinition, Uri.parse("/minimal"))

      assertResult(Return(minexp))(Try(Await.result(c.getPackageByPackageVersion("minimal", None))))
      assertResult(Return(minexp))(Try(Await.result(c.getPackageByPackageVersion("minimal", Some(minver)))))

      val maxver = TestingPackages.MaximalV3ModelV3PackageDefinition.version
      val maxexp = (TestingPackages.MaximalV3ModelV3PackageDefinition, Uri.parse("/MAXIMAL"))

      assertResult(Return(maxexp))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", None))))
      assertResult(Return(maxexp))(Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(maxver)))))

      assertResult(Return(List("MAXIMAL", "minimal")))(Try(Await.result(c.search(None)).map(_.name).sorted))
      assertResult(Return(List("minimal")))(Try(Await.result(c.search(Some("minimal"))).map(_.name)))
      val min2ver = min2.version
      assertResult(Return(List(Set(minver, min2ver))))(Try(Await.result(c.search(Some("minimal"))).map(_.versions.keys)))

      val max2ver = max2.version
      assertResult(Return(List(Set(maxver, max2ver))))(Try(Await.result(c.search(Some("MAXIMAL"))).map(_.versions.keys)))
    }
    "multi repo multi different packages" in {
      val rmax = PackageRepository("max", Uri.parse("/MAXIMAL"))
      val rmin = PackageRepository("min", Uri.parse("/minimal"))
      val min2 = TestingPackages.MinimalV3ModelV2PackageDefinition.copy(version = Version("1.2.4"))
      val max2 = TestingPackages.MaximalV3ModelV3PackageDefinition.copy(version = Version("9.9.9"))
      val clientdata = List((rmax, List(TestingPackages.MaximalV3ModelV3PackageDefinition,min2)),
                            (rmin, List(TestingPackages.MinimalV3ModelV2PackageDefinition,max2)))
      val storage = TestStorage(List(rmax,rmin))
      val client = TestMultiClient(clientdata)
      val c = new MultiRepository(storage, client)

      Try(Await.result(c.getPackagesByPackageName("minimal")).length) shouldBe Return(1)
      //will return the first repo
      Try(Await.result(c.getPackagesByPackageName("minimal"))) shouldBe Return(List(min2))
      Try(Await.result(c.getPackagesByPackageName("MAXIMAL"))) shouldBe Return(
        List(TestingPackages.MaximalV3ModelV3PackageDefinition)
      )
      Try(Await.result(c.getPackagesByPackageName("foobar"))) shouldBe Throw(
        PackageNotFound("foobar").exception
      )

      val minexp1 = (min2, Uri.parse("/MAXIMAL"))
      Try(Await.result(c.getPackageByPackageVersion("minimal", None))) shouldBe Return(minexp1)

      val minver = TestingPackages.MinimalV3ModelV2PackageDefinition.version
      val minexp2 = (TestingPackages.MinimalV3ModelV2PackageDefinition, Uri.parse("/minimal"))
      Try(Await.result(c.getPackageByPackageVersion("minimal", Some(minver)))) shouldBe Return(
        minexp2
      )

      val maxver = TestingPackages.MaximalV3ModelV3PackageDefinition.version
      val maxexp = (TestingPackages.MaximalV3ModelV3PackageDefinition, Uri.parse("/MAXIMAL"))

      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", None))) shouldBe Return(maxexp)
      Try(Await.result(c.getPackageByPackageVersion("MAXIMAL", Some(maxver)))) shouldBe Return(
        maxexp
      )

      Try(Await.result(c.search(None)).map(_.name).sorted) shouldBe Return(List("MAXIMAL", "minimal"))
      Try(Await.result(c.search(Some("minimal"))).map(_.name)) shouldBe Return(List("minimal"))

      val min2ver = min2.version
      Try(Await.result(c.search(Some("minimal"))).map(_.versions.keys)) shouldBe Return(
        List(Set(min2ver))
      )
      Try(Await.result(c.search(Some("MAXIMAL"))).map(_.versions.keys)) shouldBe Return(
        List(Set(maxver))
      )
    }

    "getPackageByPackageVersion works for all packaging versions" in {
      forAll(TestingPackages.packageDefinitions) { packageDefinition =>
        val uri = Uri.parse("/test")
        val multiRepository: MultiRepository = singletonMultiRepository(packageDefinition, uri)
        val name = packageDefinition.name
        val version = packageDefinition.version
        val actual = Await.result(
          multiRepository.getPackageByPackageVersion(name, Some(version))
        )
        actual shouldBe ((packageDefinition, uri))
      }
    }

    "getPackagesByPackageName works for all packaging versions" in {
      forAll(TestingPackages.packageDefinitions) { packageDefinition =>
        val uri = Uri.parse("/test")
        val multiRepository: MultiRepository = singletonMultiRepository(packageDefinition, uri)
        val name = packageDefinition.name
        val actual = Await.result(
          multiRepository.getPackagesByPackageName(name)
        )
        actual shouldBe List(packageDefinition)
      }
    }
  }

  private[this] def singletonMultiRepository(
    packageDefinition: universe.v4.model.PackageDefinition, uri: Uri
  ): MultiRepository = {
    val repos = List(PackageRepository("singleton", uri))
    val storage = TestStorage(repos)
    val client = TestClient(repos, List(packageDefinition))
    new MultiRepository(storage, client)
  }
}
