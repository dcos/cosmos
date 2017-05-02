package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId

/**
  * .
  */
trait SDKJanitor {
  def start(): Unit
  def stop(): Unit
  def delete(appId: AppId, session: RequestSession): Boolean
}
