package com.mesosphere.cosmos.handler

import java.nio.charset.StandardCharsets
import java.util.Base64
import cats.data.Xor
import com.mesosphere.cosmos.{AdminRouter, CirceError}
import com.mesosphere.cosmos.converter.Label._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.label.v1.circe.Decoders._
import com.mesosphere.cosmos.repository.CosmosRepository
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.rpc.v1.model.InstalledPackageInformation
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
) extends EndpointHandler[rpc.v1.model.ListRequest, rpc.v1.model.ListResponse] {

  override def apply(request: rpc.v1.model.ListRequest)(implicit session: RequestSession): Future[rpc.v1.model.ListResponse] = {
    adminRouter.listApps().flatMap { applications =>

      val appData = applications.apps.map { app =>
        (app.id, app.packageReleaseVersion, app.packageName, app.packageRepository, app.packageMetadata)
      }

      val filtered = appData.filter {
        case (appId, Some(releaseVersion), Some(packageName), Some(repositoryUri), metaData) =>
          request.packageName.forall(_ == packageName) && request.appId.forall(_ == appId)
        case _ => false
      }

      val flattened = filtered.map { x => (x._1, x._2.get, x._3.get, x._4.get, x._5) }

      val installations = flattened.map {
        case (appId, releaseVersion, packageName, repositoryUri, pkgMetadata) =>
          attemptResolvePackageMetadata(
            packageName,
            releaseVersion.as[Try[universe.v3.model.PackageDefinition.ReleaseVersion]].get,
            repositoryUri
          ).map { packageMetadata =>
            packageMetadata.getOrElse {
              val b64PkgInfo = pkgMetadata.getOrElse("")
              val pkgInfoBytes = Base64.getDecoder.decode(b64PkgInfo)
              val pkgInfoString = new String(pkgInfoBytes, StandardCharsets.UTF_8)
              decodePackageFromMarathon(pkgInfoString)
            }
          } map { pkgMetadata =>
            rpc.v1.model.Installation(appId, InstalledPackageInformation(pkgMetadata))
          }
      }

      Future.collect(installations)
    } map { installation =>
      rpc.v1.model.ListResponse(installation)
    }
  }

  private[this] def decodePackageFromMarathon(
      pkgInfoString: String
  ): label.v1.model.PackageMetadata = {
    decode[label.v1.model.PackageMetadata](pkgInfoString) match {
      case Xor.Left(err) => throw new CirceError(err)
      case Xor.Right(pkg) => pkg
    }
  }

  private[this] def attemptResolvePackageMetadata(
      packageName: String,
      releaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion,
      repositoryUri: Uri
  )(implicit session: RequestSession)
    : Future[Option[label.v1.model.PackageMetadata]] = {
    repositories(repositoryUri).flatMap {
      case Some(repository) =>
        repository
          .getPackageByReleaseVersion(packageName, releaseVersion)
          .map { pkg =>
            Some(pkg.as[label.v1.model.PackageMetadata])
          }
      case _ => Future.value(None)
    }
  }
}
