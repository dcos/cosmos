package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.ResourceTooLarge
import com.mesosphere.cosmos.http.ResourceProxyData
import com.twitter.conversions.storage._
import com.twitter.finagle.http.Status
import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.util.Await
import org.scalatest.FreeSpec

final class ResourceProxyHandlerSpec extends FreeSpec {

  "Succeeds if content length is below the limit" in {
    val resourceData = ResourceProxyData.IconSmall
    val proxyHandler = ResourceProxyHandler(resourceData.contentLength + 1.bytes, NullStatsReceiver)
    val output = Await.result(proxyHandler(resourceData.uri))

    assertResult(Status.Ok)(output.status)
  }

  "Fails if content length is at the limit" in {
    val resourceData = ResourceProxyData.IconSmall
    val proxyHandler = ResourceProxyHandler(resourceData.contentLength, NullStatsReceiver)
    val exception = intercept[CosmosException](Await.result(proxyHandler(resourceData.uri)))

    assertResult(Status.Forbidden)(exception.status)
    assert(exception.error.isInstanceOf[ResourceTooLarge])
  }

  "Fails if content length is above the limit" in {
    val resourceData = ResourceProxyData.IconSmall
    val proxyHandler = ResourceProxyHandler(resourceData.contentLength - 1.bytes, NullStatsReceiver)
    val exception = intercept[CosmosException](Await.result(proxyHandler(resourceData.uri)))

    assertResult(Status.Forbidden)(exception.status)
    assert(exception.error.isInstanceOf[ResourceTooLarge])
  }

}
