package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.CosmosError
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.twitter.bijection.Conversion
import io.circe.Json
import io.circe.jawn.parse
import java.util.UUID

object ItOps {

  implicit def cosmosErrorToErrorResponse[E <: CosmosError]: Conversion[E, ErrorResponse] = {
    Conversion.fromFunction(rpc.v1.model.ErrorResponse(_))
  }

  implicit val uuidToAppId: Conversion[UUID, AppId] = {
    Conversion.fromFunction { id =>
      AppId(id.toString)
    }
  }

  implicit final class ItStringOps(val string: String) extends AnyVal {
    def version: universe.v3.model.Version =
      universe.v3.model.Version(string)

    def detailsVersion: universe.v2.model.PackageDetailsVersion =
      universe.v2.model.PackageDetailsVersion(string)

    def json: Json = {
      val Right(result) = parse(string)
      result
    }
  }

}
