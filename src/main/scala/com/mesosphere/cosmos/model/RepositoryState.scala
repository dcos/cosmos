package com.mesosphere.cosmos.model

import com.mesosphere.cosmos.ErrorResponse

sealed trait RepositoryState
case object Healthy extends RepositoryState
case object Unused extends RepositoryState
case class Unhealthy(reason: ErrorResponse) extends RepositoryState
