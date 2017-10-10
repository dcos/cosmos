package com.mesosphere

package object universe {

  def rewriteAssets(
    urlRewrite: (String) => String,
    dockerIdRewrite: (String) => String
  )(
    assets: universe.v3.model.Assets
  ): universe.v3.model.Assets = {
    val newContainer = assets.container.map { container =>
      val newMap = container.docker.map { case (key, value) =>
        (key, dockerIdRewrite(value))
      }

      container.copy(docker = newMap)
    }

    val newUris = assets.uris.map { uris =>
      uris.map { case (key, value) =>
        (key, urlRewrite(value))
      }
    }

    assets.copy(
      uris = newUris,
      container = newContainer
    )
  }

  def rewriteCli(
    urlRewrite: (String) => String
  )(
    cli: universe.v3.model.Cli
  ): universe.v3.model.Cli = {

    cli.binaries match {
      case Some(platforms) =>
        universe.v3.model.Cli(Some(platforms.copy(
          windows = platforms.windows.map(rewriteArchitecture(urlRewrite)),
          linux = platforms.linux.map(rewriteArchitecture(urlRewrite)),
          darwin = platforms.darwin.map(rewriteArchitecture(urlRewrite))
        )))
      case None => cli
    }
  }

  def rewriteArchitecture(
    urlRewrite: (String) => String
  )(
    architecture: universe.v3.model.Architectures
  ): universe.v3.model.Architectures = {
    architecture.copy(
      `x86-64` = architecture.`x86-64`.copy(
        url = urlRewrite(architecture.`x86-64`.url)
      )
    )
  }

  def rewriteImages(
    urlRewrite: (String) => String
  )(
    images: universe.v3.model.Images
  ): universe.v3.model.Images = {
    universe.v3.model.Images(
      iconSmall = images.iconSmall.map(urlRewrite),
      iconMedium = images.iconMedium.map(urlRewrite),
      iconLarge = images.iconLarge.map(urlRewrite),
      screenshots = images.screenshots.map(_.map(urlRewrite))
    )
  }
}
