package com.mesosphere.cosmos

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.mesosphere.util.RoundTrip
import com.netaporter.uri.Uri
import io.circe.JsonObject

object RoundTrips {

  def withInstallV1(
    name: String,
    version: Option[universe.v2.model.PackageDetailsVersion] = None,
    options: Option[JsonObject] = None,
    appId: Option[AppId] = None
  ): RoundTrip[rpc.v1.model.InstallResponse] = {
    RoundTrip(
      Requests.installV1(name, version, options, appId)
    ) { ir =>
      Requests.uninstall(ir.packageName, Some(ir.appId))
      Requests.waitForDeployments()
    }
  }

  def withInstallV2(
   name: String,
   version: Option[universe.v2.model.PackageDetailsVersion] = None,
   options: Option[JsonObject] = None,
   appId: Option[AppId] = None,
   managerId: Option[String] = None
 ): RoundTrip[rpc.v2.model.InstallResponse] = {
    RoundTrip(
      Requests.installV2(name, version, options, appId, managerId)
    ) { ir =>
      Requests.uninstall(ir.packageName, ir.appId, None, managerId)
      Requests.waitForDeployments()
    }
  }

  def withDeletedRepository(
    name: Option[String] = None,
    uri: Option[Uri] = None
  ): RoundTrip[rpc.v1.model.PackageRepositoryDeleteResponse] = {
    RoundTrip.lift {
      val repos = Requests.listRepositories()
      val repo = repos.find { repo =>
        name.contains(repo.name) || uri.contains(repo.uri)
      }
      (repo, repo.map(repos.indexOf(_)))
    }.flatMap { case (repo, index) =>
      withDeletedRepository(name, uri, repo, index)
    }
  }

  def withRepository(
    name: String,
    uri: Uri,
    index: Option[Int] = None
  ): RoundTrip[rpc.v1.model.PackageRepositoryAddResponse] = {
    RoundTrip(
      Requests.addRepository(name, uri, index))(_ =>
      Requests.deleteRepository(Some(name))
    )
  }

  private[this] def withDeletedRepository(
    name: Option[String],
    uri: Option[Uri],
    oldRepo: Option[rpc.v1.model.PackageRepository],
    oldIndex: Option[Int]
  ): RoundTrip[rpc.v1.model.PackageRepositoryDeleteResponse] = {
    RoundTrip(
      Requests.deleteRepository(name, uri)
    ) { _ =>
      val repo = oldRepo.getOrElse {
        throw new RuntimeException("Unable to restore repository")
      }
      val index = oldIndex.getOrElse(
        throw new RuntimeException("Unable to restore repository index")
      )
      Requests.addRepository(repo.name, repo.uri, Some(index))
    }
  }

}
