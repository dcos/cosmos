package com.mesosphere.cosmos.model

case class SearchRequest(query: Option[String])
case class SearchResponse(packages: List[PackageIndex])
