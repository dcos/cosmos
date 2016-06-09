package com.mesosphere.cosmos.handler

import java.nio.charset.StandardCharsets
import java.util.Base64

import cats.data.Xor
import com.mesosphere.cosmos.{AdminRouter, CirceError}
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.model.{Installation, InstalledPackageInformation, ListRequest, ListResponse}
import com.mesosphere.cosmos.repository.CosmosRepository
import com.mesosphere.universe.v2.model.{PackageDetails, ReleaseVersion}
import com.mesosphere.universe.v2.circe.Decoders._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.util.Future
import io.circe.parse._

private[cosmos] final class ListHandler(
  adminRouter: AdminRouter,
  repositories: (Uri) => Future[Option[CosmosRepository]]
) extends EndpointHandler[ListRequest, ListResponse] {

  override def apply(request: ListRequest)(implicit
    session: RequestSession
  ): Future[ListResponse] = {
    adminRouter.listApps().flatMap { applications =>
      Future.collect {
        applications.apps.map { app =>
          (app.packageReleaseVersion, app.packageName, app.packageRepository) match {
            case (Some(releaseVersion), Some(packageName), Some(repositoryUri))
              if request.packageName.forall(_ == packageName) && request.appId.forall(_ == app.id) =>
                installedPackageInformation(packageName, releaseVersion, repositoryUri)
                  .map {
                    case Some(resolvedFromRepo) => resolvedFromRepo
                    case None =>
                      val b64PkgInfo = app.labels(MarathonApp.metadataLabel)
                      val pkgInfoBytes = Base64.getDecoder.decode(b64PkgInfo)
                      val pkgInfoString = new String(pkgInfoBytes, StandardCharsets.UTF_8)

                      decode[PackageDetails](pkgInfoString) match {
                        case Xor.Left(err) => throw new CirceError(err)
                        case Xor.Right(pkgDetails) => InstalledPackageInformation(pkgDetails)
                      }
                  }
                .map(packageInformation => Some(Installation(app.id, packageInformation)))
            case _ =>
              // TODO: log debug message when one of them is Some.
              Future.value(None)
          }
        }
      } map { installation =>
        ListResponse(installation.flatten)
      }
    }
  }

  private[this] def installedPackageInformation(
    packageName: String,
    releaseVersion: ReleaseVersion,
    repositoryUri: Uri
  ): Future[Option[InstalledPackageInformation]] = {
    repositories(repositoryUri)
      .flatMap {
        case Some(repository) =>
          repository.getPackageByReleaseVersion(packageName, releaseVersion)
            .map { packageFiles =>
              Some(InstalledPackageInformation(packageFiles.packageJson, packageFiles.resourceJson))
            }
        case _ => Future.value(None)
      }
  }

}
