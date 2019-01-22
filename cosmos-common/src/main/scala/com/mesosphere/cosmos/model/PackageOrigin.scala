package com.mesosphere.cosmos.model

import io.lemonlabs.uri.Uri

sealed trait PackageOrigin {
  val uri: Uri
  override def toString(): String = uri.toString
}

object LocalPackageOrigin extends PackageOrigin {
  val uri = Uri.parse("urn:dcos:cosmos:local-repository")
}

final case class ExternalRepoPackageOrigin(val uri: Uri) extends PackageOrigin

object PackageOrigin {
  def apply(uri: Uri): PackageOrigin = {
    if (uri == LocalPackageOrigin.uri) {
      LocalPackageOrigin
    } else {
      ExternalRepoPackageOrigin(uri)
    }
  }
}
