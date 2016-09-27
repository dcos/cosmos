package com.mesosphere.cosmos.storage

import com.netaporter.uri.Uri

import com.mesosphere.universe.v3.model.PackageDefinition

import scala.util.Either

case class PackageQueueContents(
  operation: Operation,
  data: Either[PackageDefinition, Uri]
)
