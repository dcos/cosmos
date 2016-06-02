package com.mesosphere.cosmos.repository

import java.io.InputStream

import com.netaporter.uri.Uri
import com.twitter.util.Future

trait UniverseClient extends (Uri => Future[InputStream])


object UniverseClient {
  private[this] final class FnUniverseClient(function: Uri => Future[InputStream])
  extends UniverseClient {
    override def apply(uri: Uri) = function(uri)
  }

  private[this] val defaultUniverseClient = new FnUniverseClient(
    universeUri => Future(universeUri.toURI.toURL.openStream())
  )

  def apply(): UniverseClient = defaultUniverseClient
  def apply(function: Uri => Future[InputStream]): UniverseClient = new FnUniverseClient(function)
}
