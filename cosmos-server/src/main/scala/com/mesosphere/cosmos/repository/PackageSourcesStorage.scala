package com.mesosphere.cosmos.repository

import cats.data.Ior
import com.mesosphere.cosmos.rpc.v1.model.PackageRepository
import io.lemonlabs.uri.Uri
import com.twitter.util.Future

private[cosmos] trait PackageSourcesStorage {

  def read(): Future[List[PackageRepository]]

  def readCache(): Future[List[PackageRepository]]

  def add(index: Option[Int], packageRepository: PackageRepository): Future[List[PackageRepository]]

  def delete(nameOrUri: Ior[String, Uri]): Future[List[PackageRepository]]
}
