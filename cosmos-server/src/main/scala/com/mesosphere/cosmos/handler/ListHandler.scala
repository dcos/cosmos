package com.mesosphere.cosmos.handler

import cats.data.Xor
import com.mesosphere.cosmos.converter.Universe._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.repository.{CosmosRepository, V3CosmosRepository}
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.{AdminRouter, CirceError}
import com.mesosphere.universe
import com.mesosphere.universe.v2.circe.Decoders._
import com.mesosphere.universe.v3.circe.Decoders._
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.Future
import io.circe.parse._
import java.nio.charset.StandardCharsets
import java.util.Base64
import scala.util.Try

private[cosmos] final class ListHandler(
  adminRouter: AdminRouter,
  repositories: (Uri) => Future[Option[CosmosRepository]]
) extends EndpointHandler[rpc.v1.model.ListRequest, rpc.v1.model.ListResponse] {

  override def apply(request: rpc.v1.model.ListRequest)(implicit
    session: RequestSession
  ): Future[rpc.v1.model.ListResponse] = {
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
                      val b64PkgInfo = app.packageMetadata.getOrElse("")
                      val pkgInfoBytes = Base64.getDecoder.decode(b64PkgInfo)
                      val pkgInfoString = new String(pkgInfoBytes, StandardCharsets.UTF_8)
                      decodePackageFromMarathon(pkgInfoString)
                  }
                .map(packageInformation => Some(rpc.v1.model.Installation(app.id, packageInformation)))
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
    decode[universe.v2.model.PackageDetails](pkgInfoString) match {
      case Xor.Left(err) => throw new CirceError(err)
      case Xor.Right(pkgDetails) => rpc.v1.model.InstalledPackageInformation(pkgDetails)
    }
  }

  private[this] def installedPackageInformation(
    packageName: String,
    releaseVersion: universe.v2.model.ReleaseVersion,
    repositoryUri: Uri
  ): Future[Option[rpc.v1.model.InstalledPackageInformation]] = {
    repositories(repositoryUri)
      .flatMap {
        case Some(repository) =>
          repository.getPackageByReleaseVersion(packageName, releaseVersion)
            .map { packageFiles =>
              Some(rpc.v1.model.InstalledPackageInformation(packageFiles.packageJson, packageFiles.resourceJson))
            }
        case _ => Future.value(None)
      }
  }
}

// TODO (version): Rename to ListHandler
private[cosmos] final class V3ListHandler(
  adminRouter: AdminRouter,
  repositories: (Uri) => Future[Option[V3CosmosRepository]]
) extends EndpointHandler[rpc.v1.model.ListRequest, rpc.v2.model.ListResponse] {

  override def apply(request: rpc.v1.model.ListRequest)(implicit
    session: RequestSession
  ): Future[rpc.v2.model.ListResponse] = {
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
                      val b64PkgInfo = app.packageMetadata.getOrElse("")
                      val pkgInfoBytes = Base64.getDecoder.decode(b64PkgInfo)
                      val pkgInfoString = new String(pkgInfoBytes, StandardCharsets.UTF_8)
                      decodePackageFromMarathon(pkgInfoString)
                  }
                .map(packageInformation => Some(rpc.v2.model.Installation(app.id, packageInformation)))
            case _ =>
              // TODO: log debug message when one of them is Some.
              Future.value(None)
          }
        }
      } map { installation =>
        rpc.v2.model.ListResponse(installation.flatten)
      }
    }
  }

  private[this] def decodePackageFromMarathon(
    pkgInfoString: String
  ): universe.v3.model.PackageDefinition = {
    decode[universe.v3.model.PackageDefinition](pkgInfoString) match {
      case Xor.Left(err) => throw new CirceError(err)
      case Xor.Right(pkgDetails) => pkgDetails
    }
  }

  private[this] def installedPackageInformation(
    packageName: String,
    releaseVersion: universe.v2.model.ReleaseVersion,
    repositoryUri: Uri
  ): Future[Option[universe.v3.model.PackageDefinition]] = {
    repositories(repositoryUri)
      .flatMap {
        case Some(repository) =>
          repository.getPackageByReleaseVersion(packageName, releaseVersion).map { pkg =>
            // TODO(version): I am sure this is not the right exception: IllegalArgumentException
            Some(pkg.as[Try[universe.v3.model.PackageDefinition]].get)
          }
        case _ => Future.value(None)
      }
  }
}
