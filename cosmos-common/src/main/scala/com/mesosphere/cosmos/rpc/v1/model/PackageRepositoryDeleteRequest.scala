package com.mesosphere.cosmos.rpc.v1.model

import com.netaporter.uri.Uri

case class PackageRepositoryDeleteRequest(name: Option[String] = None, uri: Option[Uri] = None)
