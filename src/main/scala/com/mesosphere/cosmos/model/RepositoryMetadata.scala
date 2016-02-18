package com.mesosphere.cosmos.model

import com.netaporter.uri.Uri

case class RepositoryMetadata(name: String, uri: Uri, state: RepositoryState)
  extends PackageRepository
