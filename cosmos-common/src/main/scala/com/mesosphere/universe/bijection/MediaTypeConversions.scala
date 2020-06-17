package com.mesosphere.universe.bijection

import akka.http.scaladsl.model.{MediaType => AkkaMediaType}
import com.mesosphere.http.{MediaType => CosmosMediaType, MediaTypeSubType => CosmosMediaTypeSubType}

object MediaTypeConversions {

  /** Convert from a Akka MediaType to a Cosmos MediaType*/
  implicit class RichAkkaMediaType(val akkaMediaType: AkkaMediaType) extends AnyVal {
    def asCosmos: CosmosMediaType = {
      val subType = CosmosMediaTypeSubType(akkaMediaType.subType, akkaMediaType.fileExtensions.headOption)
      CosmosMediaType(akkaMediaType.mainType, subType, akkaMediaType.params)
    }
  }
}
