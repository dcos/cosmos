package com.mesosphere.cosmos.model

import com.netaporter.uri.Uri

case class PackageRepositoryDeleteRequest(name: Option[String] = None, uri: Option[Uri] = None)
