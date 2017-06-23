package com.mesosphere.cosmos.error

import com.mesosphere.cosmos.rpc
import com.mesosphere.universe
import io.circe.Encoder
import io.circe.JsonObject
import io.circe.generic.semiauto.deriveEncoder

final case class InvalidPackageVersionForAdd(
  packageName: String,
  packageVersion: universe.v3.model.Version
) extends CosmosError {
  override def data: Option[JsonObject] = CosmosError.deriveData(this)
  override def message: String = {
    s"Unable to add version [$packageVersion] of package [$packageName]. Packaging format " +
    "not supported for this operation."
  }
}

object InvalidPackageVersionForAdd {
  def apply(packageCoordinate: rpc.v1.model.PackageCoordinate): InvalidPackageVersionForAdd = {
    InvalidPackageVersionForAdd(packageCoordinate.name, packageCoordinate.version)
  }

  implicit val encoder: Encoder[InvalidPackageVersionForAdd] = deriveEncoder
}
