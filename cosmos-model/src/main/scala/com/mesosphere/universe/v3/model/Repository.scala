package com.mesosphere.universe.v3.model

/**
  * Conforms to: https://github.com/mesosphere/universe/blob/version-3.x/repo/meta/schema/v3-repo-schema.json
  */
case class Repository(packages: List[PackageDefinition])
