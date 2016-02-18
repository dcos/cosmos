package com.mesosphere.cosmos.model

import com.netaporter.uri.Uri

case class RepositoryDescriptor(name: String, uri: Uri) extends PackageRepository
