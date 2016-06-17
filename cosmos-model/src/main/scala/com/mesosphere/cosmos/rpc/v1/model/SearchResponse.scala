package com.mesosphere.cosmos.rpc.v1.model

case class SearchResponse(packages: Seq[SearchResult])

//TODO(version): rename this to SearchResponse
case class V3SearchResponse(packages: Seq[V3SearchResult])
