package com.mesosphere.cosmos.http

import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.conversions.storage._
import com.twitter.util.StorageUnit

case class ResourceProxyData(uri: Uri, contentType: String, contentLength: StorageUnit)

object ResourceProxyData {

  val IconSmall: ResourceProxyData = ResourceProxyData(
    uri = "https://github.com/dcos/dcos-ui/blob/master/plugins/services/src/img" +
      "/icon-service-default-small.png?raw=true",
    contentType = "image/png",
    contentLength = 888.bytes
  )

  val LinuxBinary: ResourceProxyData = ResourceProxyData(
    uri = "https://infinity-artifacts.s3.amazonaws.com/uninstalltestfixture/v2" +
      "/dcos-hello-world-linux",
    contentType = "binary/octet-stream",
    contentLength = 5098592.bytes
  )

  val thirdPartyUnknownResource: ResourceProxyData = ResourceProxyData(
    uri = "https://httpbin.org/image",
    contentType = "does/not-matter",
    contentLength = 1.byte
  )

  val knownResourceWithInvalidHeader: ResourceProxyData = ResourceProxyData(
    uri = "https://infinity-artifacts.s3.amazonaws.com/uninstalltestfixture/v2" +
      "/dcos-hello-world-linux",
    contentType = "binary/octet-stream",
    contentLength = 1.byte
  )
}
