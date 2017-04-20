package com.mesosphere.cosmos.rpc.v1.model

import com.netaporter.uri.Uri

case class PackageRepositoryAddRequest(name: String, uri: Uri, index: Option[Int] = None)
