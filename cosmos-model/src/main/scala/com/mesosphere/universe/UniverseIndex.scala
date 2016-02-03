package com.mesosphere.universe

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-2.x/repo/meta/schema/index-schema.json
  */
case class UniverseIndex(
  version: UniverseVersion,
  packages: List[UniverseIndexEntry]
) {

  def getPackages: Map[String, UniverseIndexEntry] = {
    packages
      .map { entry => entry.name -> entry }
      .toMap
  }
}
