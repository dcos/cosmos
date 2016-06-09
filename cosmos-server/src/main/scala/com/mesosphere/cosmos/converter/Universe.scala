package com.mesosphere.cosmos.converter

import com.mesosphere.universe.v2.{model => v2}
import com.mesosphere.universe.v3.{model => v3}
import com.twitter.bijection.Conversion.asMethod
import com.twitter.bijection.{Bijection, Injection}

import scala.util.Try

object Universe {

  implicit val v3V3PackagingVersionToV2PackagingVersion: Injection[v3.V3PackagingVersion, v2.PackagingVersion] = {
    val fwd = (x: v3.V3PackagingVersion) => v2.PackagingVersion(x.v)
    val rev = (x: v2.PackagingVersion) => Try(v3.V3PackagingVersion(x.toString))

    Injection.build(fwd)(rev)
  }

  implicit val v3PackageDefinitionVersionToV2PackageDetailsVersion: Bijection[v3.PackageDefinition.Version, v2.PackageDetailsVersion] = {
    val fwd = (x: v3.PackageDefinition.Version) => v2.PackageDetailsVersion(x.toString)
    val rev = (x: v2.PackageDetailsVersion) => v3.PackageDefinition.Version(x.toString)

    Bijection.build(fwd)(rev)
  }

  implicit val v3PackageDefinitionTagToString: Injection[v3.PackageDefinition.Tag, String] = {
    val fwd = (x: v3.PackageDefinition.Tag) => x.value
    val rev = (x: String) => Try(v3.PackageDefinition.Tag(x))

    Injection.build(fwd)(rev)
  }

  implicit val v3LicenseTov2License: Injection[v3.License, v2.License] = {
    val fwd = (x: v3.License) => v2.License(name = x.name, url = x.url.toString)
    val rev = (x: v2.License) =>
      Common.uriToString.invert(x.url).map(url => v3.License(name = x.name, url = url))

    Injection.build(fwd)(rev)
  }

  implicit val v3CommandToV2Command: Bijection[v3.Command, v2.Command] = {
    val fwd = (x: v3.Command) => v2.Command(pip = x.pip)
    val rev = (x: v2.Command) => v3.Command(pip = x.pip)

    Bijection.build(fwd)(rev)
  }

  def v3V3ResourceToV2Resource(x: v3.V3Resource): v2.Resource = {
    v2.Resource(assets = x.assets.as[Option[v2.Assets]], images = x.images.as[Option[v2.Images]])
  }

  implicit val v3AssetsToV2Assets: Bijection[v3.Assets, v2.Assets] = {
    val fwd = (x: v3.Assets) =>
      v2.Assets(uris = x.uris, container = x.container.as[Option[v2.Container]])
    val rev = (x: v2.Assets) =>
      v3.Assets(uris = x.uris, container = x.container.as[Option[v3.Container]])

    Bijection.build(fwd)(rev)
  }

  implicit val v3ContainerToV2Container: Bijection[v3.Container, v2.Container] = {
    val fwd = (x: v3.Container) => v2.Container(docker = x.docker)
    val rev = (x: v2.Container) => v3.Container(docker = x.docker)

    Bijection.build(fwd)(rev)
  }

  implicit val v3ImagesToV2Images: Bijection[v3.Images, v2.Images] = {
    val fwd = (x: v3.Images) => v2.Images(
      iconSmall = x.iconSmall,
      iconMedium = x.iconMedium,
      iconLarge = x.iconLarge,
      screenshots = x.screenshots
    )

    val rev = (x: v2.Images) => v3.Images(
      iconSmall = x.iconSmall,
      iconMedium = x.iconMedium,
      iconLarge = x.iconLarge,
      screenshots = x.screenshots
    )

    Bijection.build(fwd)(rev)
  }

}
