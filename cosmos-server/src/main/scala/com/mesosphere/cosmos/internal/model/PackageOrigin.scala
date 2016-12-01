package com.mesosphere.cosmos.internal.model

import com.netaporter.uri.Uri

sealed trait PackageOrigin {
  val uri: Uri
  override def toString(): String = uri.toString
}

object LocalPackageOrigin extends PackageOrigin {
  val uri = Uri.parse("urn:dcos:cosmos:local-repository")
}

case class ExternalRepoPackageOrigin private[model] (val uri: Uri) extends PackageOrigin

object PackageOrigin {
  def apply(uri: Uri): PackageOrigin = {
    if (uri == LocalPackageOrigin.uri) {
      LocalPackageOrigin
    } else {
      ExternalRepoPackageOrigin(uri)
    }
  }
}
