package com.mesosphere.cosmos.rpc.v1.model

case class ListResponse(
  packages: Seq[Installation]
)
