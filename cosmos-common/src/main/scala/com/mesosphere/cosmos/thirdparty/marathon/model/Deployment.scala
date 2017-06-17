package com.mesosphere.cosmos.thirdparty.marathon.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

/** Partial Marathon Deployment.
 *
 *  This is not a full Marathon Deployment. Marathon's Deployment is a moving target.
 *  We should only decode the parts that Cosmos cares about. That is the `id` property.
 *  This is okay as long as we don't have an encoder for this class.
 */
final case class Deployment(
  id: String
)

object Deployment {
  implicit val decoder: Decoder[Deployment] = deriveDecoder
}
