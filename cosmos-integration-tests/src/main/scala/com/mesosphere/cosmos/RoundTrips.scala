package com.mesosphere.cosmos

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.util.RoundTrip
import com.mesosphere.universe
import com.netaporter.uri.Uri
import io.circe.JsonObject
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.SpanSugar._
import org.scalatest.Matchers._

object RoundTrips {

  def withInstall(
    name: String,
    version: Option[universe.v2.model.PackageDetailsVersion] = None,
    options: Option[JsonObject] = None,
    appId: Option[AppId] = None
  ): RoundTrip[rpc.v1.model.InstallResponse] = {
    RoundTrip(
      Requests.install(name, version, options, appId)
    ){ ir =>
      Requests.uninstall(ir.packageName, Some(ir.appId))
      eventually(timeout(5.minutes), interval(5.seconds)) {
        Requests.listPackages(Some(ir.packageName), Some(ir.appId)).map(_.appId).should(
          not contain ir.appId
        )
      }
    }
  }

  def withDeletedRepository(
    name: Option[String] = None,
    uri: Option[Uri] = None
  ): RoundTrip[rpc.v1.model.PackageRepositoryDeleteResponse] = {
    RoundTrip.value {
      val repos = Requests.listRepositories()
      val repo = repos.find { repo =>
        name.contains(repo.name) || uri.contains(repo.uri)
      }.getOrElse(
        throw new RuntimeException("Attempting to delete a non existent repository")
      )
      (repo, repos.indexOf(repo))
    }.flatMap { case (repo, index) =>
      withDeletedRepository(repo.name, repo.uri, index)
    }
  }

  def withRepositoriesReplaced(
    newRepositories: List[rpc.v1.model.PackageRepository]
  ): RoundTrip[List[rpc.v1.model.PackageRepositoryAddResponse]] = {
    RoundTrip.value(Requests.listRepositories()).flatMap { old =>
      val withDeletes = RoundTrip.sequence(
        old.map { repo =>
          /* we always delete the front, so we also put
           * it back at the front.
           * tricky but saves us N listRepositories,
           * which is N requests to the server,
           * which is N log lines.
           */
          withDeletedRepository(repo.name, repo.uri, 0)
        }
      )
      val withAdds = RoundTrip.sequence(
        newRepositories.map(repo => withRepository(repo.name, repo.uri))
      )
      withDeletes.flatMap(_ => withAdds)
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

  def withDeletedRepository(
    name: String,
    uri: Uri,
    oldIndex: Int
  ): RoundTrip[rpc.v1.model.PackageRepositoryDeleteResponse] = {
    RoundTrip(
      Requests.deleteRepository(Some(name), Some(uri)))(_ =>
      Requests.addRepository(name, uri, Some(oldIndex))
    )
  }

}
