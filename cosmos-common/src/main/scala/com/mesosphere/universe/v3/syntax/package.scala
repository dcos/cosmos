package com.mesosphere.universe.v3

import com.mesosphere.cosmos.http.OriginHostScheme
import com.mesosphere.universe
import com.netaporter.uri.Uri

package object syntax {

  def rewriteAssets(assets: universe.v3.model.Assets)(
    implicit originInfo : OriginHostScheme
  ) : universe.v3.model.Assets = {
    assets.copy(
      uris = assets.uris.map(
        _.map { case (key, value) =>
          (key, rewriteWithProxyURL(value))
        }
      )
    )
  }

  def rewriteCli(cli: universe.v3.model.Cli)(
    implicit originInfo : OriginHostScheme
  ) : universe.v3.model.Cli = {

    cli.binaries match {
      case Some(platforms) =>
        universe.v3.model.Cli(Some(platforms.copy(
          windows = platforms.windows.map(rewriteArchitecture),
          linux = platforms.linux.map(rewriteArchitecture),
          darwin = platforms.darwin.map(rewriteArchitecture)
        )))
      case None => cli
    }
  }

  def rewriteArchitecture(architecture: universe.v3.model.Architectures)(
    implicit originInfo : OriginHostScheme
  ): universe.v3.model.Architectures = {
    architecture.copy(
      `x86-64` = architecture.`x86-64`.copy(
        url = rewriteWithProxyURL(architecture.`x86-64`.url)
      )
    )
  }

  def rewriteImages(images: universe.v3.model.Images)(
    implicit originInfo : OriginHostScheme
  ) : universe.v3.model.Images = {
    universe.v3.model.Images(
      iconSmall = images.iconSmall.map(rewriteWithProxyURL),
      iconMedium = images.iconMedium.map(rewriteWithProxyURL),
      iconLarge = images.iconLarge.map(rewriteWithProxyURL),
      screenshots = images.screenshots.map(_.map(rewriteWithProxyURL))
    )
  }

  def rewriteWithProxyURL(url : String)(
    implicit origin : OriginHostScheme
  ) : String = {
    Uri.parse(s"${origin.urlScheme}://${origin.host}" +
      s"/package/resource?url=${Uri.parse(url).toString}").toString
  }
}
