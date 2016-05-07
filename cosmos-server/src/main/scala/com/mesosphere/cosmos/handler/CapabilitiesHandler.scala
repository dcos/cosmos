package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.http.FinchExtensions._
import com.mesosphere.cosmos.http.{Authorization, MediaType, MediaTypes, RequestSession}
import com.mesosphere.cosmos.model.{CapabilitiesResponse, Capability}
import com.twitter.util.{Future, Try}
import io.circe.Encoder
import io.finch._

class CapabilitiesHandler private(implicit decodeRequest: DecodeRequest[Any], encoder: Encoder[CapabilitiesResponse])
  extends EndpointHandler[Any, CapabilitiesResponse]
 {

  private[this] val response = CapabilitiesResponse(List(Capability("PACKAGE_MANAGEMENT")))

  override val accepts: MediaType = MediaTypes.any
  override val produces: MediaType = MediaTypes.CapabilitiesResponse

  override lazy val reader: RequestReader[(RequestSession, Any)] = for {
    accept <- header("Accept").as[MediaType].should(beTheExpectedType(produces))
    auth <- headerOption("Authorization").as[String]
  } yield {
    RequestSession(auth.map(Authorization(_))) -> None
  }


  override def apply(v1: Any)(implicit session: RequestSession): Future[CapabilitiesResponse] = {
    Future.value(response)
  }
}

object CapabilitiesHandler {
  def apply()(implicit encoder: Encoder[CapabilitiesResponse]): CapabilitiesHandler = {
    implicit val anyDecodeRequest: DecodeRequest[Any] = DecodeRequest.instance[Any]( _ => Try { ??? })
    new CapabilitiesHandler()
  }
}
