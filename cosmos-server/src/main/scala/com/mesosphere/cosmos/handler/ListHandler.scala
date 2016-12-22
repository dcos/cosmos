package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.converter.Label._
import com.mesosphere.cosmos.converter.Response._
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.internal.model.MarathonAppOps
import com.mesosphere.cosmos.internal.model.PackageOrigin
import com.mesosphere.cosmos.label
import com.mesosphere.cosmos.label.v1.circe.Decoders._
import com.mesosphere.cosmos.repository.CosmosRepository
import com.mesosphere.cosmos.rpc.v1.model.{Installation, InstalledPackageInformation, ListRequest, ListResponse}
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonApp}
import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl.stringToUri
import com.twitter.bijection.Conversion.asMethod
import com.twitter.util.{Future, Try}
import java.nio.charset.StandardCharsets
import java.util.Base64


private[cosmos] final class ListHandler(
  adminRouter: AdminRouter,
  repositories: (Uri) => Future[Option[CosmosRepository]]
) extends EndpointHandler[ListRequest, ListResponse] {

  private case class App(
    id: AppId,
    pkgName: String,
    pkgReleaseVersion: universe.v3.model.PackageDefinition.ReleaseVersion,
    repoUri: PackageOrigin,
    pkgMetadata: Option[String]
  )

  override def apply(request: ListRequest)
                    (implicit session: RequestSession): Future[ListResponse] = {
    for {
      apps <- getApplications(adminRouter, request)
      repoAssocs <- getRepositoryAssociations(repositories, apps)
      installs <- getInstallations(repoAssocs)
    } yield {
      ListResponse(installs.sortBy(install =>
        (install.packageInformation.packageDefinition.name, install.appId)))
    }
  }

  private[this] def getApplications(adminRouter: AdminRouter, request: ListRequest)
                                   (implicit session: RequestSession): Future[Seq[App]] = {

    def satisfiesRequest(app: MarathonApp): Boolean = {
      // corner case: packageReleaseVersion will be None if parsing the label fails
      (app.packageReleaseVersion, app.packageName, app.packageRepository) match {
        case (Some(_), Some(pkgName), Some(_)) =>
          request.packageName.forall(_ == pkgName) && request.appId.forall(_ == app.id)
        case _ => false
      }
    }

    adminRouter.listApps().map { response =>
      response.apps.collect{
        case app if satisfiesRequest(app) =>
          App(
            app.id,
            app.packageName.get,
            app.packageReleaseVersion.get,
            app.packageRepository.get,
            app.packageMetadata
          )
      }
    }
  }

  private[this] def getRepositoryAssociations(
    repositories: (Uri) => Future[Option[CosmosRepository]],
    apps: Seq[App]
  ): Future[Seq[(App, Option[CosmosRepository])]] = {
    Future.collect {
      apps.map { app =>
        repositories(app.repoUri.uri).map(repo => (app, repo))
      }
    }
  }

  private[this] def getInstallations(
    assocs: Seq[(App, Option[CosmosRepository])]
  )(
    implicit session: RequestSession
  ): Future[Seq[Installation]] = {
    Future.collect {
      assocs map {
        case (app, Some(repo)) =>
          repo
            .getPackageByReleaseVersion(app.pkgName, app.pkgReleaseVersion)
            .map { pkg =>
              val adjustedPackage: universe.v3.model.PackageDefinition = pkg match {
                /* Note: The old code dropped CLI information when returning information to the
                 * client. We will fix this when we move to the new APIs.
                 *
                 * The clients expect the both selected and framework to be set.
                 */
                case pkg: universe.v3.model.V3Package =>
                  pkg.copy(
                    selected=pkg.selected orElse Some(false),
                    framework=pkg.framework orElse Some(false),
                    resource=pkg.resource.map(_.copy(cli=None))
                  )
                case pkg: universe.v3.model.V2Package =>
                  pkg.copy(
                    selected=pkg.selected orElse Some(false),
                    framework=pkg.framework orElse Some(false)
                  )
              }

              adjustedPackage.as[Try[InstalledPackageInformation]]
            }.lowerFromTry
            .map { pkgInfo =>
              Installation(app.id, pkgInfo)
            }
        case (app, None) =>
          Future.value(Installation(app.id, decodeInstalledPackageInformation(app)))
      }
    }
  }

  private[this] def decodeInstalledPackageInformation(app: App): InstalledPackageInformation = {
    val pkgMetadata = app.pkgMetadata.getOrElse("")
    val pkgInfo = new String(
      Base64.getDecoder.decode(pkgMetadata),
      StandardCharsets.UTF_8
    )
    decode[label.v1.model.PackageMetadata](pkgInfo).as[InstalledPackageInformation]
  }

}

