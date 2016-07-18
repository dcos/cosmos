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
import com.mesosphere.universe
import com.mesosphere.cosmos.converter.Universe.internalPackageDefinitionToInstalledPackageInformation
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
        // corner case: packageReleaseVersion will be None if parsing the label fails
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
          ).map { installPackageInfo =>
            installPackageInfo.getOrElse {
              val b64PkgInfo = pkgMetadata.getOrElse("")
              val pkgInfoBytes = Base64.getDecoder.decode(b64PkgInfo)
              val pkgInfoString = new String(pkgInfoBytes, StandardCharsets.UTF_8)
              decodePackageFromMarathon(pkgInfoString).as[rpc.v1.model.InstalledPackageInformation]
            }
          } map { installedPackageInfo =>
            rpc.v1.model.Installation(appId, installedPackageInfo)
          }
      }

      Future.collect(installations)
    } map { installations =>
      rpc.v1.model.ListResponse(installations.sortBy(i =>
        (i.packageInformation.packageDefinition.name ,i.appId)))
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
    : Future[Option[rpc.v1.model.InstalledPackageInformation]] = {
    repositories(repositoryUri).flatMap {
      case Some(repository) =>
        repository
          .getPackageByReleaseVersion(packageName, releaseVersion)
          .map { pkg =>
            Some(pkg.as[rpc.v1.model.InstalledPackageInformation])
          }
      case _ => Future.value(None)
    }
  }
}
