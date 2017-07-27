package com.mesosphere.cosmos

import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.util.RoundTrip
import com.mesosphere.universe
import com.netaporter.uri.Uri
import io.circe.JsonObject
import org.scalatest.concurrent.Eventually._
import org.scalatest.time.SpanSugar._

object RoundTrips {

  def withInstall(
    name: String,
    version: Option[universe.v2.model.PackageDetailsVersion] = None,
    options: Option[JsonObject] = None,
    appId: Option[AppId] = None
  ): RoundTrip[rpc.v1.model.InstallResponse] = {
    RoundTrip(
      eventually(timeout(2.minutes), interval(5.seconds)) {
        Requests.install(name, version, options, appId)
      }
    ) { ir =>
      Requests.uninstall(ir.packageName, Some(ir.appId))
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
      }
      (repo, repo.map(repos.indexOf(_)))
    }.flatMap { case (repo, index) =>
      withDeletedRepository(name, uri, repo, index)
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
          withDeletedRepository(Some(repo.name), Some(repo.uri), Some(repo), Some(0))
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
