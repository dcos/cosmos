package com.mesosphere.cosmos.http

import com.twitter.io.Buf

sealed trait HttpRequestBody

case object NoBody extends HttpRequestBody

final case class Monolithic(data: Buf) extends HttpRequestBody
