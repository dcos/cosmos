package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.rpc
import com.twitter.finagle.http.Status
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder
import org.jboss.netty.handler.codec.http.HttpMethod

final case class UniverseClientHttpError(
  packageRepository: rpc.v1.model.PackageRepository,
  method: HttpMethod,
  clientStatus: Status,
  comsosStatus: Status
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)

  override def message: String = {
    "Unexpected upstream http error " +
      s"while fetching repository '${packageRepository.name}'" +
      s" at ${packageRepository.uri.toString}: " +
      s"${method.getName} ${clientStatus.code}"
  }

  override def status: Status = clientStatus

}

object UniverseClientHttpError {
  implicit val encoder: Encoder[UniverseClientHttpError] = deriveEncoder
}
