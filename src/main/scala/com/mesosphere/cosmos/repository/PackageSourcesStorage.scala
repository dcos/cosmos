package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.model.PackageRepository
import com.netaporter.uri.Uri
import com.twitter.util.Future

private[cosmos] trait PackageSourcesStorage {

  def read(): Future[List[PackageRepository]]

  def write(sources: List[PackageRepository]): Future[List[PackageRepository]]

}

private[cosmos] object PackageSourcesStorage {

  private[cosmos] def constUniverse(uri: Uri): PackageSourcesStorage = new PackageSourcesStorage {

    val read: Future[List[PackageRepository]] = {
      Future.value(List(PackageRepository("Universe", uri)))
    }

    def write(sources: List[PackageRepository]): Future[List[PackageRepository]] = {
      Future.exception(new UnsupportedOperationException)
    }

  }

}
