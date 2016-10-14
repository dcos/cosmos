package com.mesosphere.universe.v3.model

/**
  * Conforms to: https://universe.mesosphere.com/v3/schema/repo#/definitions/cliInfo
  */
case class Binary(kind: String, url: String, contentHash: List[HashInfo])
