package com.mesosphere.cosmos.repository

import java.io.InputStream

import com.netaporter.uri.Uri
import com.twitter.util.Future

trait UniverseClient extends Function1[Uri, Future[InputStream]]


object UniverseClient {
  def apply(): UniverseClient = new UniverseClient {
    override def apply(universeUri: Uri): Future[InputStream] = {
      Future(universeUri.toURI.toURL.openStream())
    }
  }

  def apply(function: Uri => Future[InputStream]): UniverseClient = new UniverseClient {
    override def apply(universeUri: Uri): Future[InputStream] = function(universeUri)
  }
}
