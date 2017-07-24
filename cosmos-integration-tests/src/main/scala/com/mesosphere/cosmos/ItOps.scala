package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.CosmosError
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import java.util.UUID
import scala.language.implicitConversions

object ItOps {

  implicit def cosmosErrorToErrorResponse[E <: CosmosError](cosmosError: E): ErrorResponse = {
    cosmosError.exception.errorResponse
  }

  implicit def toOption[A](a: A): Option[A] = {
    Option(a)
  }

  implicit def stringToVersion(s: String): universe.v3.model.Version = {
    universe.v3.model.Version(s)
  }

  implicit def stringToDetailsVersion(s: String): universe.v2.model.PackageDetailsVersion = {
    universe.v2.model.PackageDetailsVersion(s)
  }

  implicit def uuidToAppId(
    uuid: UUID
  ): AppId = {
    AppId(uuid.toString)
  }

}
