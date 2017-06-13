package com.mesosphere.cosmos.error

import com.mesosphere.universe
import com.mesosphere.universe.v2.circe.Encoders._
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class MultipleFrameworkIds(
  packageName: String,
  packageVersion: Option[universe.v2.model.PackageDetailsVersion],
  frameworkName: String,
  ids: List[String]
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    packageVersion match {
      case Some(ver) =>
        s"Uninstalled package [$packageName] version [$ver]\n" +
        s"Unable to shutdown [$packageName] service framework with name [$frameworkName] " +
        s"because there are multiple framework ids matching this name: [${ids.mkString(", ")}]"
      case None =>
        s"Uninstalled package [$packageName]\n" +
        s"Unable to shutdown [$packageName] service framework with name [$frameworkName] " +
        s"because there are multiple framework ids matching this name: [${ids.mkString(", ")}]"
    }
  }
}

object MultipleFrameworkIds {
  implicit val encoder: Encoder[MultipleFrameworkIds] = deriveEncoder
}
