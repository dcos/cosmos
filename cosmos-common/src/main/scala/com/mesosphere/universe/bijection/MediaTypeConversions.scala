package com.mesosphere.universe.bijection

import akka.http.scaladsl.model.{MediaType => AkkaMediaType}
import com.mesosphere.http.{MediaType => CosmosMediaType, MediaTypeSubType => CosmosMediaTypeSubType}

object MediaTypeConversions {

  /** Convert from a Akka MediaType to a Cosmos MediaType*/
  implicit class RichAkkaMediaType(val akkaMediaType: AkkaMediaType) extends AnyVal {
    def asCosmos: CosmosMediaType = {
      // TODO: Who are Comos media type "suffix" and Akka HTTP's "fileExtension" related?
      val subType = akkaMediaType.subType.split('+').toList match {
        case subType :: Nil => CosmosMediaTypeSubType(subType, None)
        case subType :: suffix :: Nil => CosmosMediaTypeSubType(subType, Some(suffix))
        case _ => CosmosMediaTypeSubType(akkaMediaType.subType, None)
      }
      CosmosMediaType(akkaMediaType.mainType, subType, akkaMediaType.params)
    }
  }
}
