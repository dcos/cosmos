package com.mesosphere.cosmos

import cats.data.Xor
import com.mesosphere.cosmos.http.CosmosRequest
import com.mesosphere.cosmos.repository.DefaultRepositories
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.circe.Encoders._
import com.mesosphere.cosmos.rpc.v1.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe.v2.model.PackageDetailsVersion
import com.mesosphere.universe.v2.model.PackagingVersion
import java.util.UUID
import org.scalatest.AppendedClues
import org.scalatest.FreeSpec
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually

final class PackageListIntegrationSpec
  extends FreeSpec with Inside with AppendedClues with Eventually {

  import PackageListIntegrationSpec._

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
    val request = CosmosRequest.post(
      "package/install",
      InstallRequest(packageName, appId = Some(AppId(UUID.randomUUID().toString))),
      MediaTypes.InstallRequest,
      MediaTypes.V1InstallResponse
    )
    val Xor.Right(installResponse) = CosmosClient.callEndpoint[InstallResponse](request) withClue "when installing package"

    try {
      assertResult(packageName)(installResponse.packageName)
      f(installResponse)
    } finally {
      val request = CosmosRequest.post(
        "package/uninstall",
        UninstallRequest(installResponse.packageName, appId = Some(installResponse.appId), all = None),
        MediaTypes.UninstallRequest,
        MediaTypes.UninstallResponse
      )
      val actualUninstall = CosmosClient.callEndpoint[UninstallResponse](request) withClue "when uninstalling package"

      inside (actualUninstall) {
        case Xor.Right(UninstallResponse(List(UninstallResult(uninstalledPackageName, appId, Some(packageVersion), _)))) =>
          assertResult(installResponse.appId)(appId)
          assertResult(installResponse.packageName)(uninstalledPackageName)
          assertResult(installResponse.packageVersion)(packageVersion)
      }
    }
  }

  private[this] def withDeletedRepository(repository: PackageRepository)(action: => Unit): Unit = {
    val request = CosmosRequest.post(
      "package/repository/delete",
      PackageRepositoryDeleteRequest(name = Some(repository.name)),
      MediaTypes.PackageRepositoryDeleteRequest,
      MediaTypes.PackageRepositoryDeleteResponse
    )
    val actualDelete = CosmosClient.callEndpoint[PackageRepositoryDeleteResponse](request) withClue "when deleting repo"

    try {
      assertResult(Xor.Right(None)) {
        actualDelete.map(_.repositories.find(_.name == repository.name))
      }

      action
    } finally {
      val request = CosmosRequest.post(
        "package/repository/add",
        PackageRepositoryAddRequest(repository.name, repository.uri),
        MediaTypes.PackageRepositoryAddRequest,
        MediaTypes.PackageRepositoryAddResponse
      )
      val actualAdd = CosmosClient.callEndpoint[PackageRepositoryAddResponse](request) withClue "when restoring deleted repo"

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
    val request = CosmosRequest.post(
      "package/list",
      ListRequest(),
      MediaTypes.ListRequest,
      MediaTypes.ListResponse
    )
    val actualList = CosmosClient.callEndpoint[ListResponse](request) withClue "when listing installed packages"

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
    val request = CosmosRequest.post(
      "package/list",
      ListRequest(),
      MediaTypes.ListRequest,
      MediaTypes.ListResponse
    )
    val Xor.Right(listResponse) = CosmosClient.callEndpoint[ListResponse](request) withClue "when listing installed packages"

    listResponse
  }

  private[this] def packageInstall(packageName: String): InstallResponse = {
    val request = CosmosRequest.post(
      "package/install",
      InstallRequest(packageName, appId = Some(AppId(UUID.randomUUID().toString))),
      MediaTypes.InstallRequest,
      MediaTypes.V1InstallResponse
    )
    val Xor.Right(installResponse: InstallResponse) = CosmosClient.callEndpoint[InstallResponse](request) withClue "when installing package"

    assertResult(packageName)(installResponse.packageName)

    installResponse
  }

  private[this] def packageUninstall(installResponse: InstallResponse): Unit = {
    val request = CosmosRequest.post(
      "package/uninstall",
      UninstallRequest(installResponse.packageName, appId = Some(installResponse.appId), all = None),
      MediaTypes.UninstallRequest,
      MediaTypes.UninstallResponse
    )
    val Xor.Right(uninstallResponse: UninstallResponse) = CosmosClient.callEndpoint[UninstallResponse](request) withClue "when uninstalling package"

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
