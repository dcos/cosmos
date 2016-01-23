package com.mesosphere.cosmos.model

case class SearchRequest(query: String)
case class SearchResponse(results: List[PackageIndex])
