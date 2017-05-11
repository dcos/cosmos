package com.mesosphere.cosmos.janitor

import java.util.concurrent.DelayQueue

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.janitor.SdkJanitor.JanitorRequest
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.FreeSpec
import org.scalatest.mockito.MockitoSugar

final class SdkJanitorSpec extends FreeSpec with BeforeAndAfterEach with MockitoSugar {
  private val mockTracker = mock[Tracker]
  private val mockWorker = mock[Worker]
  private val mockSession = mock[RequestSession]
  private val appId = AppId("/test")

  private[this] var queue: DelayQueue[JanitorRequest] = _
  private[this] var janitor: SdkJanitor = _

  override def beforeEach(): Unit = {
    queue = new DelayQueue[JanitorRequest]()
    janitor = new SdkJanitor(mockTracker, mockWorker, queue, true, 1)
  }

  "In the SDKJanitor" - {
    "Delete adds request to queue and marks InProgress in ZK" in {
      janitor.delete(appId, mockSession)

      verify(mockTracker).createZkRecord(appId)
      assertResult(1)(queue.size())
    }
    "Start submits the worker to the thread pool" in {
      janitor.start()
      val wait = 100L
      Thread.sleep(wait)
      verify(mockWorker).run()
    }
    "Stop sets running to false" in {
      janitor.stop()
      assertResult(false)(janitor.running)
    }
  }
}
