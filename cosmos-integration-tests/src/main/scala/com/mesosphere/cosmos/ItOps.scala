package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.CosmosError
import com.mesosphere.cosmos.rpc.v1.model.ErrorResponse
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.universe
import com.twitter.bijection.Conversion
import io.circe.Json
import io.circe.jawn.parse
import java.util.UUID
import scala.language.implicitConversions

object ItOps {

  implicit def cosmosErrorToErrorResponse[E <: CosmosError]: Conversion[E, ErrorResponse] = {
    Conversion.fromFunction(_.exception.errorResponse)
  }

  implicit def stringToVersion(s: String): universe.v3.model.Version = {
    universe.v3.model.Version(s)
  }

  implicit def stringToDetailsVersion(s: String): universe.v2.model.PackageDetailsVersion = {
    universe.v2.model.PackageDetailsVersion(s)
  }

  implicit val uuidToAppId: Conversion[UUID, AppId] = {
    Conversion.fromFunction { id =>
      AppId(id.toString)
    }
  }

  implicit def stringToJson(s: String): Json = {
    val Right(res) = parse(s)
    res
  }

}
