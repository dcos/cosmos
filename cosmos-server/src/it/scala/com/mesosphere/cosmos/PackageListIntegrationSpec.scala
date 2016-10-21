package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.{PackageDetailsVersion, PackagingVersion}
import org.scalatest.concurrent.Eventually
import org.scalatest.{AppendedClues, FreeSpec, Inside}

import java.util.UUID

final class PackageListIntegrationSpec
  extends FreeSpec with Inside with AppendedClues with Eventually {

  import PackageListIntegrationSpec._

  val apiClient = CosmosIntegrationTestClient.CosmosClient

  // These tests may be better implemented as focused unit tests
  // There's a bunch of setup and teardown infrastructure here which complicates the control flow
  // Until then, if you need to understand the code, ask @cruhland (says @BenWhitehead)

  "The package list endpoint" - {
    "responds with repo and package data for packages whose repositories are in the repo list" in {
      withRunningPackage("helloworld") { runResponse =>
        withRunningPackageInListResponse(runResponse) { case Some(Instantiation(_, _)) =>
          // Success
        }
      }
    }
  }

  "Issue #251: Package list should include packages whose repositories have been removed" in {
    val expectedPackageInformation = RunningPackageInformation(
      RunningPackageInformationPackageDetails(
        packagingVersion = PackagingVersion("2.0"),
        name = "helloworld",
        version = PackageDetailsVersion("0.1.0"),
        website = Some("https://github.com/mesosphere/dcos-helloworld"),
        maintainer = "support@mesosphere.io",
        description = "Example DCOS application package",
        preInstallNotes = Some("A sample pre-installation message"),
        postInstallNotes = Some("A sample post-installation message"),
        tags = List("mesosphere", "example", "subcommand"),
        selected = Some(false),
        framework = None
      )
    )
    withRunningPackage("helloworld") { runResponse =>
      withDeletedRepository(helloWorldRepository) {
        withRunningPackageInListResponse(runResponse) { case Some(Instantiation(_, pkg)) =>
            assertResult(expectedPackageInformation)(pkg)
          // Success
        }
      }
    }
  }

  private[this] def withRunningPackage(packageName: String)(f: RunResponse => Unit): Unit = {
    val Xor.Right(runResponse) = apiClient.callEndpoint[RunRequest, RunResponse](
      RunPath,
      RunRequest(packageName, appId = Some(AppId(UUID.randomUUID().toString))),
      MediaTypes.RunRequest,
      MediaTypes.V1RunResponse
    ) withClue "when running package"

    try {
      assertResult(packageName)(runResponse.packageName)
      f(runResponse)
    } finally {
      val actualKill = apiClient.callEndpoint[KillRequest, KillResponse](
        "package/kill",
        KillRequest(runResponse.packageName, appId = Some(runResponse.appId), all = None),
        MediaTypes.KillRequest,
        MediaTypes.KillResponse
      ) withClue "when killing package"

      inside (actualKill) {
        case Xor.Right(KillResponse(List(KillResult(killedPackageName, appId, Some(packageVersion), _)))) =>
          assertResult(runResponse.appId)(appId)
          assertResult(runResponse.packageName)(killedPackageName)
          assertResult(runResponse.packageVersion)(packageVersion)
      }
    }
  }

  private[this] def withDeletedRepository(repository: PackageRepository)(action: => Unit): Unit = {
    val actualDelete = apiClient.callEndpoint[PackageRepositoryDeleteRequest, PackageRepositoryDeleteResponse](
      "package/repository/delete",
      PackageRepositoryDeleteRequest(name = Some(repository.name)),
      MediaTypes.PackageRepositoryDeleteRequest,
      MediaTypes.PackageRepositoryDeleteResponse
    ) withClue "when deleting repo"

    try {
      assertResult(Xor.Right(None)) {
        actualDelete.map(_.repositories.find(_.name == repository.name))
      }

      action
    } finally {
      val actualAdd = apiClient.callEndpoint[PackageRepositoryAddRequest, PackageRepositoryAddResponse](
        "package/repository/add",
        PackageRepositoryAddRequest(repository.name, repository.uri),
        MediaTypes.PackageRepositoryAddRequest,
        MediaTypes.PackageRepositoryAddResponse
      ) withClue "when restoring deleted repo"

      inside(actualAdd) { case Xor.Right(PackageRepositoryAddResponse(repositories)) =>
        inside(repositories.find(_.name == repository.name)) { case Some(addedRepository) =>
          assertResult(repository)(addedRepository)
        }
      }
    }
  }

  private[this] def withRunningPackageInListResponse(runResponse: RunResponse)(
    pf: PartialFunction[Option[Instantiation], Unit]
  ): Unit = {
    val actualList = apiClient.callEndpoint[ListRequest, ListResponse](
      "package/list",
      ListRequest(),
      MediaTypes.ListRequest,
      MediaTypes.ListResponse
    ) withClue "when listing running packages"

    inside (actualList) { case Xor.Right(ListResponse(packages)) =>
      inside (packages.find(_.appId == runResponse.appId)) { pf }
    }
  }

  "Package list endpoint responds with" - {
    "(issue #124) packages sorted by name and app id" in {
      val names = List(
        "linkerd",
        "linkerd",
        "zeppelin",
        "jenkins",
        "cassandra")
      val runResponses = names map packageRun
      try {
        val packages = packageList().packages.map(app => (app.packageInformation.packageDefinition.name, app.appId))
        val resultNames = packages.map(_._1)
        assert(packages == packages.sorted)
        assert(names.sorted == resultNames.sorted)
      } finally {
        runResponses.foreach(ir => packageKill(ir))
      }
    }
  }

  private[this] def packageList(): ListResponse = {
    val Xor.Right(listResponse) = apiClient.callEndpoint[ListRequest, ListResponse](
      "package/list",
      ListRequest(),
      MediaTypes.ListRequest,
      MediaTypes.ListResponse
    ) withClue "when listing running packages"

    listResponse
  }

  private[this] def packageRun(packageName: String): RunResponse = {
    val Xor.Right(runResponse: RunResponse) = apiClient.callEndpoint[RunRequest, RunResponse](
      RunPath,
      RunRequest(packageName, appId = Some(AppId(UUID.randomUUID().toString))),
      MediaTypes.RunRequest,
      MediaTypes.V1RunResponse
    ) withClue "when running package"

    assertResult(packageName)(runResponse.packageName)

    runResponse
  }

  private[this] def packageKill(runResponse: RunResponse): Unit = {
    val Xor.Right(killResponse: KillResponse) = apiClient.callEndpoint[KillRequest, KillResponse](
      KillPath,
      KillRequest(runResponse.packageName, appId = Some(runResponse.appId), all = None),
      MediaTypes.KillRequest,
      MediaTypes.KillResponse
    ) withClue "when killing package"

    val KillResponse(List(KillResult(killedPackageName, appId, Some(packageVersion), _))) =
      killResponse

    assertResult(runResponse.appId)(appId)
    assertResult(runResponse.packageName)(killedPackageName)
    assertResult(runResponse.packageVersion)(packageVersion)
  }

}

object PackageListIntegrationSpec {

  private val Some(helloWorldRepository) = DefaultRepositories().getOrThrow.find(_.name == "Hello World")
  private val RunPath: String = "package/run"
  private val KillPath: String = "package/kill"

}
