package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId

/**
  * Daemon for deleting SDK (dcos-commons) based services after their uninstall is completed.
  */
trait SDKJanitor {
  def start(): Unit
  def stop(): Unit
  def delete(appId: AppId, session: RequestSession): Unit
}
