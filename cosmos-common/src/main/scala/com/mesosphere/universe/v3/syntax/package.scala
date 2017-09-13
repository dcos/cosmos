package com.mesosphere.universe.v3

import com.mesosphere.cosmos.model.OriginHostScheme
import com.mesosphere.universe
import com.netaporter.uri.Uri

package object syntax {

  def rewriteAssets(assets: Option[universe.v3.model.Assets])(
    implicit originInfo : Option[OriginHostScheme]
  ) : Option[universe.v3.model.Assets] = {
    if (assets.isDefined && assets.get.uris.isDefined) {
      Some(assets.get.copy(uris = assets.get.uris.map(
        _.map { case (key, value) => (key, rewriteWithProxyURL(value))}
      )))
    } else assets
  }

  def rewriteCli(cli: Option[universe.v3.model.Cli])(
    implicit originInfo : Option[OriginHostScheme]
  ) : Option[universe.v3.model.Cli] = {

    def rewriteArchitecture(
      architecture: Option[universe.v3.model.Architectures]
    ): Option[universe.v3.model.Architectures] = {
      architecture.flatMap { arch =>
        Some(arch.copy(`x86-64` = arch.`x86-64`.copy(url = rewriteWithProxyURL(arch.`x86-64`.url))))
      }
    }

    if (cli.isDefined && cli.get.binaries.isDefined) {
      val initial = cli.get.binaries.get
      Some(universe.v3.model.Cli(Some(initial.copy(
        windows = rewriteArchitecture(initial.windows),
        linux = rewriteArchitecture(initial.linux),
        darwin = rewriteArchitecture(initial.darwin)
      ))))
    } else cli
  }

  def rewriteImages(images: Option[universe.v3.model.Images])(
    implicit originInfo : Option[OriginHostScheme]
  ) : Option[universe.v3.model.Images] = {
    images match {
      case Some(images) => Some(universe.v3.model.Images(
        iconSmall = images.iconSmall.map(rewriteWithProxyURL),
        iconMedium = images.iconMedium.map(rewriteWithProxyURL),
        iconLarge = images.iconLarge.map(rewriteWithProxyURL),
        screenshots = images.screenshots.map(_.map(rewriteWithProxyURL))
      ))
      case None => None
    }
  }

  def rewriteWithProxyURL(url : String)(
    implicit originInfo : Option[OriginHostScheme]
  ) : String = {
    originInfo match {
      case Some(origin) =>
        print(s"#############?>> Host : ${origin.host} Scheme :${origin.urlScheme}" +
          s" For: ${origin.forwardedFor} Port : ${origin.forwardedPort} Proto : ${origin.protocol}\n")
        s"${origin.protocol}://${origin.forwardedFor}:${origin.forwardedPort}" +
        s"/package/resource?url=${Uri.parse(url).toString}"
      case None => url
    }
  }
}
