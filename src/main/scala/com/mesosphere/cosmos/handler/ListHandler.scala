package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model.{Installation, ListRequest, ListResponse, InstalledPackageInformation}
import com.mesosphere.cosmos.{AdminRouter, PackageCache}
import com.twitter.util.Future
import io.circe.Encoder
import io.finch.DecodeRequest

final class ListHandler(
  adminRouter: AdminRouter,
  packageCache: PackageCache
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
          (app.packageReleaseVersion, app.packageName) match {
            case (Some(releaseVersion), Some(packageName)) =>
              if (request.packageName.exists(_ != packageName)) {
                // Package name was specified and it doesn't match
                Future.value(None)
              } else if (request.appId.exists(_ != app.id)) {
                // Application id was specified and it doesn't match
                Future.value(None)
              } else {
                // TODO: We should change this to use a package cache collection
                packageCache.getPackageByReleaseVersion(
                  packageName,
                  releaseVersion
                ).map { packageFiles =>
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
