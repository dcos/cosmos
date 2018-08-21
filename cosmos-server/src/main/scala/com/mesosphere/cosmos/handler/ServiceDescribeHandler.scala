package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.universe.v4.model.PackageDefinition
import io.circe.JsonObject
import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.finch.EndpointHandler
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.render.PackageDefinitionRenderer
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.repository.rewriteUrlWithProxyInfo
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.service.CustomPackageManagerRouter
import com.mesosphere.universe
import com.twitter.util.Future
import com.mesosphere.universe.bijection.UniverseConversions._
import org.slf4j.Logger
import com.twitter.bijection.Conversion.asMethod


private[cosmos] final class ServiceDescribeHandler(
  adminRouter: AdminRouter,
  packageCollection: PackageCollection
) extends EndpointHandler[rpc.v1.model.ServiceDescribeRequest, rpc.v1.model.ServiceDescribeResponse] {
  lazy val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  override def apply(
    request: rpc.v1.model.ServiceDescribeRequest)(implicit
    session: RequestSession
  ): Future[rpc.v1.model.ServiceDescribeResponse] = {
    CustomPackageManagerRouter.getCustomPackageManagerId(
      adminRouter,
      packageCollection,
      request.managerId,
      request.packageName,
      request.packageVersion.as[Option[universe.v3.model.Version]],
      Option(request.appId)
    ).flatMap {
      case managerId if !managerId.isEmpty => {
        logger.debug("Request requires a custom manager: " + managerId)
        CustomPackageManagerRouter.callCustomServiceDescribe(
          adminRouter,
          request,
          managerId
        ).flatMap {
          case response =>
            Future {response}
        }
      }
      case managerId if managerId.isEmpty => {
      for {
        marathonAppResponse <- adminRouter.getApp (request.appId)
        packageDefinition <- getPackageDefinition (marathonAppResponse.app)
        upgradesTo <- packageCollection.upgradesTo (packageDefinition.name, packageDefinition.version)
        downgradesTo <- packageCollection.downgradesTo (packageDefinition)
      } yield {
          val userProvidedOptions = marathonAppResponse.app.serviceOptions
          rpc.v1.model.ServiceDescribeResponse (
          `package` = packageDefinition,
          upgradesTo = upgradesTo,
          downgradesTo = downgradesTo,
          resolvedOptions = getResolvedOptions (packageDefinition, userProvidedOptions),
          userProvidedOptions = userProvidedOptions
      )
    }
    }
    }
  }

  private def getPackageDefinition(
    app: MarathonApp)(implicit
    session: RequestSession
  ): Future[universe.v4.model.PackageDefinition] = {
    app.packageDefinition
      .map(pkg => Future.value(pkg.rewrite(rewriteUrlWithProxyInfo(session.originInfo), identity)))
      .getOrElse {
        val (name, version) =
          app.packageName.flatMap(name => app.packageVersion.map(name -> _))
            .getOrElse(throw new IllegalStateException(
              "The name and version of the service were not found in the labels"))
        packageCollection
          .getPackageByPackageVersion(name, Some(version))
          .map(_._1)
      }
  }

  private def getResolvedOptions(
    packageDefinition: PackageDefinition,
    serviceOptions: Option[JsonObject]
  ): Option[JsonObject] = {
    serviceOptions.map { userSuppliedOptions =>
      PackageDefinitionRenderer.mergeDefaultAndUserOptions(packageDefinition, Some(userSuppliedOptions))
    }
  }
}
