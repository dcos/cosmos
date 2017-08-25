package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.HttpClient
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
    val lengthLimit = resourceData.contentLength + 1.bytes
    val proxyHandler = ResourceProxyHandler(HttpClient, lengthLimit, NullStatsReceiver)
    val output = Await.result(proxyHandler(resourceData.uri))

    assertResult(Status.Ok)(output.status)
  }

  "Fails if content length is at the limit" in {
    val resourceData = ResourceProxyData.IconSmall
    val lengthLimit = resourceData.contentLength
    val proxyHandler = ResourceProxyHandler(HttpClient, lengthLimit, NullStatsReceiver)
    val exception = intercept[CosmosException](Await.result(proxyHandler(resourceData.uri)))

    assertResult(Status.Forbidden)(exception.status)
    assert(exception.error.isInstanceOf[ResourceTooLarge])
  }

  "Fails if content length is above the limit" in {
    val resourceData = ResourceProxyData.IconSmall
    val lengthLimit = resourceData.contentLength - 1.bytes
    val proxyHandler = ResourceProxyHandler(HttpClient, lengthLimit, NullStatsReceiver)
    val exception = intercept[CosmosException](Await.result(proxyHandler(resourceData.uri)))

    assertResult(Status.Forbidden)(exception.status)
    assert(exception.error.isInstanceOf[ResourceTooLarge])
  }

  // TODO proxy Test cases
  // If ContentLength was not specified, and the stream is > the limit, fail
  // If ContentLength was not specified, and the stream is == the limit, fail
  // If ContentLength was not specified, and the stream is < the limit, pass
  // If ContentLength *was* specified, and the stream is > that, fail
  // If ContentLength *was* specified, and the stream is == that, pass
  // If ContentLength *was* specified, and the stream is < that, pass (don't need to test this)
  //   - Verify that our ContentLength is the actual size of the data

}
