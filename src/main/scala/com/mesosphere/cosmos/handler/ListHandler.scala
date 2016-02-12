package com.mesosphere.cosmos.handler

import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model.Installation
import com.mesosphere.cosmos.model.InstalledPackageInformation
import com.mesosphere.cosmos.model.ListRequest
import com.mesosphere.cosmos.model.ListResponse
import com.mesosphere.cosmos.repository.Repository

final class ListHandler(
  adminRouter: AdminRouter,
  repositories: (Uri) => Future[Repository]
)(implicit
  requestDecoder: DecodeRequest[ListRequest],
  responseEncoder: Encoder[ListResponse]
) extends EndpointHandler[ListRequest, ListResponse] {

  val accepts = MediaTypes.ListRequest
  val produces = MediaTypes.ListResponse

  override def apply(request: ListRequest): Future[ListResponse] = {
    adminRouter.listApps().flatMap { applications =>
      Future.collect {
        applications.apps.map { app =>
          (app.packageReleaseVersion, app.packageName, app.packageRepository) match {
            case (Some(releaseVersion), Some(packageName), Some(packageRepository)) =>
              if (request.packageName.exists(_ != packageName)) {
                // Package name was specified and it doesn't match
                Future.value(None)
              } else if (request.appId.exists(_ != app.id)) {
                // Application id was specified and it doesn't match
                Future.value(None)
              } else {
                repositories(packageRepository).flatMap { repository =>
                  repository.getPackageByReleaseVersion(
                    packageName,
                    releaseVersion
                  )
                } map { packageFiles =>
                  Some(
                    Installation(
                      app.id.toString,
                      InstalledPackageInformation(
                        packageFiles.packageJson,
                        packageFiles.resourceJson
                      )
                    )
                  )
                }
              }
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
}
