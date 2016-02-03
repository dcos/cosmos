package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.model.PackageSource
import com.netaporter.uri.Uri
import com.twitter.util.Future

private[cosmos] trait PackageSourcesStorage {

  def read(): Future[List[PackageSource]]

  def write(sources: List[PackageSource]): Future[List[PackageSource]]

}

private[cosmos] object PackageSourcesStorage {

  private[cosmos] def constUniverse(uri: Uri): PackageSourcesStorage = new PackageSourcesStorage {

    val read: Future[List[PackageSource]] = Future.value(List(PackageSource("Universe", uri)))

    def write(sources: List[PackageSource]): Future[List[PackageSource]] = {
      Future.exception(new UnsupportedOperationException)
    }

  }

}
