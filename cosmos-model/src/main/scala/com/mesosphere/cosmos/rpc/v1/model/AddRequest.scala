package com.mesosphere.cosmos.rpc.v1.model

import com.twitter.concurrent.AsyncStream
import com.twitter.io.Buf

case class AddRequest(packageData: AsyncStream[Buf], packageSize: Long)
