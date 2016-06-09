package com.mesosphere.cosmos.converter

import com.netaporter.uri.Uri
import com.twitter.bijection.Injection

import scala.util.Try

object Common {

  implicit val uriToString: Injection[Uri, String] = {
    Injection.build[Uri, String](_.toString)(s => Try(Uri.parse(s)))
  }

}
