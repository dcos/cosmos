package com.mesosphere.cosmos.model

import com.netaporter.uri.Uri

case class PackageRepositoryAddRequest(name: String, uri: Uri, index: Option[Int] = None)
