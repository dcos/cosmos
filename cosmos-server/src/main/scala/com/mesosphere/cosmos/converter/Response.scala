package com.mesosphere.cosmos.converter

import java.nio.charset.StandardCharsets
import com.mesosphere.cosmos.{ServiceMarathonTemplateNotFound, Trys, rpc}
import com.mesosphere.universe
import com.mesosphere.universe.bijection.UniverseConversions._
import com.mesosphere.universe.common.ByteBuffers
import com.mesosphere.universe.v3.model.{V2Package, V3Package}
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Conversion, Injection}
import com.twitter.bijection.Bijection._
import com.twitter.bijection.twitter_util.UtilBijections._
import com.twitter.util.{Return, Try}

object Response {
  implicit val v2RunResponseToV1RunResponse: Conversion[
    rpc.v2.model.RunResponse,
    Try[rpc.v1.model.RunResponse]
  ] = Conversion.fromFunction { (x: rpc.v2.model.RunResponse) =>
    Try(x.appId.getOrElse(throw ServiceMarathonTemplateNotFound(x.packageName, x.packageVersion))).map { appId =>
      rpc.v1.model.RunResponse(
        packageName = x.packageName,
        packageVersion = x.packageVersion.as[universe.v2.model.PackageDetailsVersion],
        appId = appId
      )
    }
  }

  implicit val packageDefinitionToV1DescribeResponse: Conversion[
    universe.v3.model.PackageDefinition,
    Try[rpc.v1.model.DescribeResponse]
    ] = Conversion.fromFunction { (packageDefinition: universe.v3.model.PackageDefinition) =>
    Trys.join(
      Try(packageDefinition.marathon.map(_.v2AppMustacheTemplate).getOrElse(
        throw ServiceMarathonTemplateNotFound(packageDefinition.name, packageDefinition.version)
      )),
      v2Resource(packageDefinition)
    ).map { case (b64MarathonTemplate, resources) =>
      rpc.v1.model.DescribeResponse(
        `package` = universe.v2.model.PackageDetails(
          packagingVersion = packageDefinition.packagingVersion.as[universe.v2.model.PackagingVersion],
          name = packageDefinition.name,
          version = packageDefinition.version.as[universe.v2.model.PackageDetailsVersion],
          maintainer = packageDefinition.maintainer,
          description = packageDefinition.description,
          tags = packageDefinition.tags.as[List[String]],
          selected = packageDefinition.selected,
          scm = packageDefinition.scm,
          website = packageDefinition.website,
          framework = packageDefinition.framework,
          preInstallNotes = packageDefinition.preInstallNotes,
          postInstallNotes = packageDefinition.postInstallNotes,
          postUninstallNotes = packageDefinition.postUninstallNotes,
          licenses = packageDefinition.licenses.as[Option[List[universe.v2.model.License]]]
        ),
        marathonMustache = new String(ByteBuffers.getBytes(b64MarathonTemplate), StandardCharsets.UTF_8),
        command = packageDefinition.command.as[Option[universe.v2.model.Command]],
        config = packageDefinition.config,
        resource = resources
      )
    }
  }

  implicit val packageDefinitionToV2DescribeResponse: Conversion[
    universe.v3.model.PackageDefinition,
    rpc.v2.model.DescribeResponse
    ] = Conversion.fromFunction { (pkg: universe.v3.model.PackageDefinition) =>
      rpc.v2.model.DescribeResponse(
        pkg.packagingVersion,
        pkg.name,
        pkg.version,
        pkg.maintainer,
        pkg.description,
        pkg.tags,
        pkg.selected.getOrElse(false),
        pkg.scm,
        pkg.website,
        pkg.framework.getOrElse(false),
        pkg.preInstallNotes,
        pkg.postInstallNotes,
        pkg.postUninstallNotes,
        pkg.licenses,
        pkg.minDcosReleaseVersion,
        pkg.marathon,
        pkg.v3Resource,
        pkg.config,
        pkg.command
      )
    }

  implicit val packageDefinitionToRunningPackageInformation: Conversion[
    universe.v3.model.PackageDefinition,
    Try[rpc.v1.model.RunningPackageInformation]
    ] =
    Conversion.fromFunction {
      case pkg: universe.v3.model.V2Package =>
        Return(rpc.v1.model.RunningPackageInformation(
          packageDefinition = rpc.v1.model.RunningPackageInformationPackageDetails(
            packagingVersion = pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
            name = pkg.name,
            version = pkg.version.as[universe.v2.model.PackageDetailsVersion],
            maintainer = pkg.maintainer,
            description = pkg.description,
            tags = pkg.tags.as[List[String]],
            selected = pkg.selected,
            scm = pkg.scm,
            website = pkg.website,
            framework = pkg.framework,
            preInstallNotes = pkg.preInstallNotes,
            postInstallNotes = pkg.postInstallNotes,
            postUninstallNotes = pkg.postUninstallNotes,
            licenses = pkg.licenses.as[Option[List[universe.v2.model.License]]]
          ),
          resourceDefinition = pkg.resource.map(_.as[universe.v2.model.Resource])
        ))
      case pkg: universe.v3.model.V3Package =>
        v2Resource(pkg).map { res =>
          rpc.v1.model.RunningPackageInformation(
            packageDefinition = rpc.v1.model.RunningPackageInformationPackageDetails(
              packagingVersion = pkg.packagingVersion.as[universe.v2.model.PackagingVersion],
              name = pkg.name,
              version = pkg.version.as[universe.v2.model.PackageDetailsVersion],
              maintainer = pkg.maintainer,
              description = pkg.description,
              tags = pkg.tags.as[List[String]],
              selected = pkg.selected,
              scm = pkg.scm,
              website = pkg.website,
              framework = pkg.framework,
              preInstallNotes = pkg.preInstallNotes,
              postInstallNotes = pkg.postInstallNotes,
              postUninstallNotes = pkg.postUninstallNotes,
              licenses = pkg.licenses.as[Option[List[universe.v2.model.License]]]
            ),
            resourceDefinition = res
          )
        }
    }



  private[this] def v2Resource(pkg: universe.v3.model.PackageDefinition): Try[Option[universe.v2.model.Resource]] =
    pkg match {
      case v2: V2Package =>
        Injection.invert[Option[universe.v2.model.Resource], Option[universe.v3.model.V2Resource]](v2.resource)
          .as[Try[Option[universe.v2.model.Resource]]]
      case v3: V3Package =>
        Injection.invert[Option[universe.v2.model.Resource], Option[universe.v3.model.V3Resource]](v3.resource)
          .as[Try[Option[universe.v2.model.Resource]]]
    }
}
