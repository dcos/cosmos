package com.mesosphere.cosmos.handler

import java.nio.charset.StandardCharsets
import java.util.Base64

import cats.data.Xor
import com.mesosphere.cosmos.{AdminRouter, CirceError}
import com.mesosphere.cosmos.converter.Label._
import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.label.v1.circe.Decoders._
import com.mesosphere.cosmos.repository.CosmosRepository
import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.parse._

import scala.util.Try

private[cosmos] final class ListHandler(
    adminRouter: AdminRouter,
    repositories: (Uri) => Future[Option[CosmosRepository]]
)
    extends EndpointHandler[
        rpc.v1.model.ListRequest, rpc.v1.model.ListResponse] {

  override def apply(request: rpc.v1.model.ListRequest)(
      implicit session: RequestSession): Future[rpc.v1.model.ListResponse] = {
    adminRouter.listApps().flatMap { applications =>
      Future.collect {
        applications.apps.map { app =>
          (app.packageReleaseVersion, app.packageName, app.packageRepository) match {
            case (Some(releaseVersion), Some(packageName), Some(repositoryUri))
                if request.packageName.forall(_ == packageName) &&
                request.appId.forall(_ == app.id) =>
              installedPackageInformation(
                  packageName,
                  releaseVersion
                    .as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]]
                    .get,
                  repositoryUri
              ).map { packageInfo =>
                val packageInformation = packageInfo.getOrElse {
                  val b64PkgInfo = app.packageMetadata.getOrElse("")
                  val pkgInfoBytes = Base64.getDecoder.decode(b64PkgInfo)
                  val pkgInfoString =
                    new String(pkgInfoBytes, StandardCharsets.UTF_8)
                  decodePackageFromMarathon(pkgInfoString)
                }

                Some(rpc.v1.model.Installation(app.id, packageInformation))
              }
            case _ =>
              // TODO: log debug message when one of them is Some.
              Future.value(None)
          }
        }
      } map { installation =>
        rpc.v1.model.ListResponse(installation.flatten)
      }
    }
  }

  private[this] def decodePackageFromMarathon(
      pkgInfoString: String
  ): rpc.v1.model.InstalledPackageInformation = {
    decode[label.v1.model.PackageMetadata](pkgInfoString) match {
      case Xor.Left(err) => throw new CirceError(err)
      case Xor.Right(pkg) => pkg.as[rpc.v1.model.InstalledPackageInformation]
    }
  }

  private[this] def installedPackageInformation(
      packageName: String,
      releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion,
      repositoryUri: Uri
  )(implicit session: RequestSession)
    : Future[Option[rpc.v1.model.InstalledPackageInformation]] = {
    repositories(repositoryUri).flatMap {
      case Some(repository) =>
        repository
          .getPackageByReleaseVersion(packageName, releaseVersion)
          .map { pkg =>
            // TODO(version): The package definition conversion can throw: IllegalArgumentException
            Some(
                rpc.v1.model.InstalledPackageInformation(
                    packageDefinition = pkg
                      .as[Try[universe.v3.model.PackageDefinition]]
                      .map(_.as[universe.v2.model.PackageDetails])
                      .get,
                    resourceDefinition =
                      pkg.resource.as[Option[universe.v2.model.Resource]]
                ))
          }
      case _ => Future.value(None)
    }
  }
}
