package com.mesosphere.cosmos.model

import com.netaporter.uri.Uri

trait PackageRepository {
  def name: String
  def uri: Uri
}

object PackageRepository {

  def apply(name: String, uri: Uri): PackageRepository = RepositoryDescriptor(name, uri)

}
