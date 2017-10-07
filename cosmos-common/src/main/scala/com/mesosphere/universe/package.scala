package com.mesosphere

import com.mesosphere.cosmos.http.OriginHostScheme
import com.mesosphere.http.DockerId
import com.netaporter.uri.Uri
import scala.util.Failure
import scala.util.Success
import scala.util.Try

package object universe {

  def rewriteAssets(
    rewriteDocker: Boolean
  )(
    assets: universe.v3.model.Assets
  )(
    implicit originInfo: OriginHostScheme
  ): universe.v3.model.Assets = {
    val newContainer = if (rewriteDocker) {
      assets.container.map { container =>
        val newMap = container.docker.map { case (key, value) =>
          (key, rewriteDockerIdWithProxyInfo(value))
        }

        container.copy(docker = newMap)
      }
    } else assets.container

    val newUris = assets.uris.map { uris =>
      uris.map { case (key, value) =>
        (key, rewriteUrlWithProxyInfo(value))
      }
    }

    assets.copy(
      uris = newUris,
      container = newContainer
    )
  }

  def rewriteCli(
    cli: universe.v3.model.Cli
  )(
    implicit originInfo: OriginHostScheme
  ): universe.v3.model.Cli = {

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

  def rewriteArchitecture(
    architecture: universe.v3.model.Architectures
  )(
    implicit originInfo: OriginHostScheme
  ): universe.v3.model.Architectures = {
    architecture.copy(
      `x86-64` = architecture.`x86-64`.copy(
        url = rewriteUrlWithProxyInfo(architecture.`x86-64`.url)
      )
    )
  }

  def rewriteImages(
    images: universe.v3.model.Images
  )(
    implicit originInfo: OriginHostScheme
  ): universe.v3.model.Images = {
    universe.v3.model.Images(
      iconSmall = images.iconSmall.map(rewriteUrlWithProxyInfo),
      iconMedium = images.iconMedium.map(rewriteUrlWithProxyInfo),
      iconLarge = images.iconLarge.map(rewriteUrlWithProxyInfo),
      screenshots = images.screenshots.map(_.map(rewriteUrlWithProxyInfo))
    )
  }

  def rewriteUrlWithProxyInfo(
    value: String
  )(
    implicit origin: OriginHostScheme
  ): String = {
    Try(Uri.parse(value)) match {
      case Success(url) =>
        // TODO: This can throw!!
        Uri.parse(
          s"${origin.urlScheme}://${origin.rawHost}/package/resource?url=$url"
        ).toString
      case Failure(_) =>
        value
    }
  }

  def rewriteDockerIdWithProxyInfo(
    value: String
  )(
    implicit origin: OriginHostScheme
  ): String = {
    DockerId.parse(value).map { id =>
      id.copy(hostAndPort = id.hostAndPort.map(_ => origin.hostAndPort))
        .show
    }
    .getOrElse(value)
  }
}
