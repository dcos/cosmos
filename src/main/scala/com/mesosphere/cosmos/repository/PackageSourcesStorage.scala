package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.model.PackageRepository
import com.netaporter.uri.Uri
import com.twitter.util.Future

private[cosmos] trait PackageSourcesStorage {

  def read(): Future[List[PackageRepository]]

  def readCache(): Future[List[PackageRepository]]

  def add(index: Option[Int], packageRepository: PackageRepository): Future[List[PackageRepository]]

  def delete(name: Option[String], uri: Option[Uri]): Future[List[PackageRepository]]
}
