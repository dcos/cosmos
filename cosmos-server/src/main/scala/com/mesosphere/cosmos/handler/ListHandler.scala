package com.mesosphere.cosmos.handler

import java.nio.charset.StandardCharsets
import java.util.Base64
import cats.data.Xor
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.{AdminRouter, CirceError}
import com.mesosphere.cosmos.http.{MediaTypes, RequestSession}
import com.mesosphere.cosmos.model.thirdparty.marathon.MarathonApp
import com.mesosphere.cosmos.model.{Installation, InstalledPackageInformation, ListRequest, ListResponse}
import com.mesosphere.cosmos.repository.Repository
import com.mesosphere.universe.{PackageDetails, ReleaseVersion}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.util.Future
import io.circe.Encoder
import io.circe.parse._
import io.finch.DecodeRequest

final class ListHandler(
  adminRouter: AdminRouter,
  repositories: (Uri) => Future[Option[Repository]]
)(implicit
  requestDecoder: DecodeRequest[ListRequest],
  responseEncoder: Encoder[ListResponse]
) extends EndpointHandler[ListRequest, ListResponse] {

  val accepts = MediaTypes.ListRequest
  val produces = MediaTypes.ListResponse

  override def apply(request: ListRequest)(implicit session: RequestSession): Future[ListResponse] = {
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
