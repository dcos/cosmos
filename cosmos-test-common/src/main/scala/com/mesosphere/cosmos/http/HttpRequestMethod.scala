package com.mesosphere.cosmos.http

import com.twitter.io.Buf

sealed trait HttpRequestMethod

final case class Get(params: (String, String)*) extends HttpRequestMethod

final case class Post(body: Buf) extends HttpRequestMethod
