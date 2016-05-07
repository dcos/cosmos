package com.mesosphere.cosmos.http

case class Authorization(token: String) {
  val headerValue: String = token
}
