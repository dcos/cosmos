package com.mesosphere.cosmos.http

object MediaTypes {
  private[this] def vnd(kind: String): MediaType =
    MediaType(
      "application",
      MediaTypeSubType(s"vnd.dcos.cosmos.$kind", Some("json")),
      Some(Map("charset" -> "utf-8", "version" -> "v1"))
    )

  val UninstallRequest = vnd("uninstall-request")
  val UninstallResponse = vnd("uninstall-response")
  val ErrorResponse = vnd("error")

}
