package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.io.Buf
import com.twitter.util.Future
import io.circe.Json

class AdminRouter(adminRouterUri: Uri, client: Service[Request, Response]) {
  private val baseUri = {
    val uri = adminRouterUri.toString
    if (uri.endsWith("/"))
      uri.substring(0, uri.length)
    else
      uri
  }

  private[this] def get(uri: Uri): Request = {
    RequestBuilder()
      .url(s"$baseUri${uri.toString}")
      .setHeader("Accept", "application/json;charset=utf-8")
      .buildGet
  }

  private[this] def post(uri: Uri, jsonBody: Json): Request = {
    RequestBuilder()
      .url(s"$baseUri${uri.toString}")
      .setHeader("Accept", "application/json;charset=utf-8")
      .setHeader("Content-Type", "application/json;charset=utf-8")
      .buildPost(Buf.Utf8(jsonBody.noSpaces))
  }

  private[this] def delete(uri: Uri): Request = {
    RequestBuilder()
      .url(s"$baseUri${uri.toString}")
      .setHeader("Accept", "application/json;charset=utf-8")
      .buildDelete()
  }

  def createApp(appJson: Json): Future[Response] = {
    client(post("marathon" / "v2" / "apps" , appJson))
  }

  def getApp(appId: Uri): Future[Response] = {
    client(get("marathon" / "v2" / "apps" / appId))
  }

  def listApps(): Future[Response] = {
    client(get("marathon" / "v2" / "apps"))
  }

  def deleteApp(appId: Uri, force: Boolean = false): Future[Response] = {
    force match {
      case true => client(delete("marathon" / "v2" / "apps" / appId ? ("force" -> "true")))
      case false => client(delete("marathon" / "v2" / "apps" / appId))
    }
  }
}
