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
      withInstalledPackage("helloworld") { installResponse =>
        withInstalledPackageInListResponse(installResponse) { case Some(Installation(_, _)) =>
          // Success
        }
      }
    }
  }

  "Issue #251: Package list should include packages whose repositories have been removed" in {
    val expectedPackageInformation = InstalledPackageInformation(
      InstalledPackageInformationPackageDetails(
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
    withInstalledPackage("helloworld") { installResponse =>
      withDeletedRepository(helloWorldRepository) {
        withInstalledPackageInListResponse(installResponse) { case Some(Installation(_, pkg)) =>
            assertResult(expectedPackageInformation)(pkg)
          // Success
        }
      }
    }
  }

  private[this] def withInstalledPackage(packageName: String)(f: InstallResponse => Unit): Unit = {
    val Xor.Right(installResponse) = apiClient.callEndpoint[InstallRequest, InstallResponse](
      "package/install",
      InstallRequest(packageName, appId = Some(AppId(UUID.randomUUID().toString))),
      MediaTypes.InstallRequest,
      MediaTypes.V1InstallResponse
    ) withClue "when installing package"

    try {
      assertResult(packageName)(installResponse.packageName)
      f(installResponse)
    } finally {
      val actualUninstall = apiClient.callEndpoint[UninstallRequest, UninstallResponse](
        "package/uninstall",
        UninstallRequest(installResponse.packageName, appId = Some(installResponse.appId), all = None),
        MediaTypes.UninstallRequest,
        MediaTypes.UninstallResponse
      ) withClue "when uninstalling package"

      inside (actualUninstall) {
        case Xor.Right(UninstallResponse(List(UninstallResult(uninstalledPackageName, appId, Some(packageVersion), _)))) =>
          assertResult(installResponse.appId)(appId)
          assertResult(installResponse.packageName)(uninstalledPackageName)
          assertResult(installResponse.packageVersion)(packageVersion)
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

  private[this] def withInstalledPackageInListResponse(installResponse: InstallResponse)(
    pf: PartialFunction[Option[Installation], Unit]
  ): Unit = {
    val actualList = apiClient.callEndpoint[ListRequest, ListResponse](
      "package/list",
      ListRequest(),
      MediaTypes.ListRequest,
      MediaTypes.ListResponse
    ) withClue "when listing installed packages"

    inside (actualList) { case Xor.Right(ListResponse(packages)) =>
      inside (packages.find(_.appId == installResponse.appId)) { pf }
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
      val installResponses = names map packageInstall
      try {
        val packages = packageList().packages.map(app => (app.packageInformation.packageDefinition.name, app.appId))
        val resultNames = packages.map(_._1)
        assert(packages == packages.sorted)
        assert(names.sorted == resultNames.sorted)
      } finally {
        installResponses.foreach(ir => packageUninstall(ir))
      }
    }
  }

  private[this] def packageList(): ListResponse = {
    val Xor.Right(listResponse) = apiClient.callEndpoint[ListRequest, ListResponse](
      "package/list",
      ListRequest(),
      MediaTypes.ListRequest,
      MediaTypes.ListResponse
    ) withClue "when listing installed packages"

    listResponse
  }

  private[this] def packageInstall(packageName: String): InstallResponse = {
    val Xor.Right(installResponse: InstallResponse) = apiClient.callEndpoint[InstallRequest, InstallResponse](
      "package/install",
      InstallRequest(packageName, appId = Some(AppId(UUID.randomUUID().toString))),
      MediaTypes.InstallRequest,
      MediaTypes.V1InstallResponse
    ) withClue "when installing package"

    assertResult(packageName)(installResponse.packageName)

    installResponse
  }

  private[this] def packageUninstall(installResponse: InstallResponse): Unit = {
    val Xor.Right(uninstallResponse: UninstallResponse) = apiClient.callEndpoint[UninstallRequest, UninstallResponse](
      "package/uninstall",
      UninstallRequest(installResponse.packageName, appId = Some(installResponse.appId), all = None),
      MediaTypes.UninstallRequest,
      MediaTypes.UninstallResponse
    ) withClue "when uninstalling package"

    val UninstallResponse(List(UninstallResult(uninstalledPackageName, appId, Some(packageVersion), _))) =
      uninstallResponse

    assertResult(installResponse.appId)(appId)
    assertResult(installResponse.packageName)(uninstalledPackageName)
    assertResult(installResponse.packageVersion)(packageVersion)
  }

}

object PackageListIntegrationSpec {

  private val Some(helloWorldRepository) = DefaultRepositories().getOrThrow.find(_.name == "Hello World")

}
