package com.mesosphere.cosmos.janitor

import java.lang.Thread.State
import java.util.concurrent.DelayQueue

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.MarathonAppNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.janitor.SdkJanitor.JanitorRequest
import com.mesosphere.cosmos.janitor.SdkJanitor.Request
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppResponse
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import com.twitter.util.Future
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.FreeSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.SpanSugar

final class JanitorWorkerSpec extends FreeSpec with MockitoSugar
  with BeforeAndAfterEach with Eventually with SpanSugar {
  private val mockTracker = mock[Tracker]
  private val mockAdminRouter = mock[AdminRouter]
  private val mockSession = mock[RequestSession]

  private val appId = AppId("/test")
  private val request = JanitorRequest(
    appId,
    mockSession,
    failures = 0,
    created = System.currentTimeMillis(),
    checkInterval = 1,
    lastAttempt = 0L
  )

  private[this] var queue: DelayQueue[Request] = _
  private[this] var worker: JanitorWorker = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(mockTracker)
    reset(mockAdminRouter)

    queue = new DelayQueue[Request]()
    worker = new JanitorWorker(queue, mockTracker, mockAdminRouter)
  }

  "In the JanitorWorker" - {
    "If stop is called, the work loop exits" in {
      val thread = new Thread(worker)

      thread.start()
      worker.stop()

      eventually (timeout(10 seconds), interval(1 seconds)) {
        val _ = assertResult(State.TERMINATED)(thread.getState)
      }

    }
    "In doWork" - {
      "If the Marathon app is not found, the request is not requeued and is deleted from ZK" in {
        when(mockAdminRouter.getApp(appId)(mockSession)).thenThrow(new MarathonAppNotFound(appId))
        worker.doWork(request)

        verify(mockTracker).deleteZkRecord(appId)
      }
      "If an exception is thrown, the request is requeued" in {
        when(mockAdminRouter.getApp(appId)(mockSession)).thenThrow(new RuntimeException("test!"))
        worker.doWork(request)

        assertResult(1)(queue.size())
        assertResult(1)(queue.peek().asInstanceOf[JanitorRequest].failures)
      }
      "If the app should be deleted, it is" in {
        when(mockAdminRouter.getApp(appId)(mockSession))
          .thenReturn(Future.value(MarathonAppResponse(new MarathonApp(appId, Map()))))

        val mockResponse = Response(status = Status.Ok)
        val mockFuture = Future.value(mockResponse)
        when(mockAdminRouter.getSdkServicePlanStatus(
          service = appId.toString,
          apiVersion = "v1",
          plan = "deploy"
        )(mockSession)).thenReturn(mockFuture)

        val mockDeleteResponse = Response(status = Status.Ok)
        val mockDeleteFuture = Future.value(mockDeleteResponse)
        when(mockAdminRouter.deleteApp(appId)(mockSession)).thenReturn(mockDeleteFuture)

        worker.doWork(request)
        verify(mockTracker).deleteZkRecord(appId)
        verifyNoMoreInteractions(mockTracker)
        assertResult(0)(queue.size())
      }
      "If the app is not ready to be deleted, it is requeued" in {
        when(mockAdminRouter.getApp(appId)(mockSession))
          .thenReturn(Future.value(MarathonAppResponse(new MarathonApp(appId, Map()))))

        val mockResponse = Response(status = Status.Accepted)
        val mockFuture = Future.value(mockResponse)
        when(mockAdminRouter.getSdkServicePlanStatus(
          service = appId.toString,
          apiVersion = "v1",
          plan = "deploy"
        )(mockSession)).thenReturn(mockFuture)

        worker.doWork(request)
        verifyZeroInteractions(mockTracker)
        assertResult(1)(queue.size())
      }
    }
    "In checkUninstall" - {
      val labels = Map(SdkJanitor.SdkApiVersionLabel -> "v1")
      val app = new MarathonApp(appId, labels)

      val mockResponse = mock[Response]
      val mockFuture = Future.value(mockResponse)

      def setup(): Unit = {
        when(mockAdminRouter.getSdkServicePlanStatus(
          service = appId.toString,
          apiVersion = "v1",
          plan = "deploy"
        )(mockSession)).thenReturn(mockFuture)
        ()
      }

      "SDK version label is obeyed" - {
        "If not present, v1 is used" in {
          val noLabelsApp = new MarathonApp(appId, Map())
          setup()

          when(mockResponse.status).thenReturn(Status.Ok)
          assertResult(true)(worker.checkUninstall(noLabelsApp)(mockSession))
        }
        "If present, value is used" in {
          val newApp = new MarathonApp(appId, Map(SdkJanitor.SdkApiVersionLabel -> "v2"))
          setup()

          reset(mockAdminRouter)
          when(mockAdminRouter.getSdkServicePlanStatus(
            service = appId.toString,
            apiVersion = "v2",
            plan = "deploy"
          )(mockSession)).thenReturn(mockFuture)

          when(mockResponse.status).thenReturn(Status.Ok)
          assertResult(true)(worker.checkUninstall(newApp)(mockSession))
        }
      }
      "If the plan status is 200 OK, return true" in {
        setup()

        when(mockResponse.status).thenReturn(Status.Ok)

        assertResult(true)(worker.checkUninstall(app)(mockSession))
      }
      "If the plan status is anything but 200 OK, return false" in {
        setup()
        when(mockResponse.status).thenReturn(Status.BadGateway)

        assertResult(false)(worker.checkUninstall(app)(mockSession))
      }
      "If an exception is thrown, return false" in {
        setup()
        when(mockResponse.status).thenThrow(new NullPointerException())

        assertResult(false)(worker.checkUninstall(app)(mockSession))
      }
    }
    "In delete" - {
      val mockResponse = mock[Response]

      def setup(): Unit = {
        val mockFuture = Future.value(mockResponse)
        when(mockAdminRouter.deleteApp(appId)(mockSession)).thenReturn(mockFuture)
        ()
      }
      "If the delete response is 4XX, fail the request" in {
        setup()
        when(mockResponse.status).thenReturn(Status.Unauthorized)

        worker.delete(request)
        verify(mockTracker).failZkRecord(appId)
      }
      "If the delete response is 5XX, requeue the request" in {
        setup()
        when(mockResponse.status).thenReturn(Status.BadGateway)

        worker.delete(request)
        verifyZeroInteractions(mockTracker)

        assertResult(1)(queue.size())
        assertResult(1)(queue.peek().asInstanceOf[JanitorRequest].failures)
      }
      "If the response is 200, don't requeue and delete the record" in {
        setup()
        when(mockResponse.status).thenReturn(Status.Ok)

        worker.delete(request)
        verify(mockTracker).deleteZkRecord(appId)
        assertResult(0)(queue.size())
      }
      "Any other response, requeue the request" in {
        setup()
        when(mockResponse.status).thenReturn(Status.TemporaryRedirect)

        worker.delete(request)
        assertResult(1)(queue.size())
        assertResult(1)(queue.peek().asInstanceOf[JanitorRequest].failures)
      }
      "If an exception is thrown, requeue the request" in {
        setup()
        when(mockResponse.status).thenThrow(new NullPointerException())

        worker.delete(request)
        assertResult(1)(queue.size())
        assertResult(1)(queue.peek().asInstanceOf[JanitorRequest].failures)
      }
    }
    "In fail" - {
      "Mark the request as failed in ZK" in {
        worker.fail(request)
        verify(mockTracker).failZkRecord(appId)
      }
    }
    "In requeue" - {
      "If the request has exceeded the failure limit, fail it" in {
        worker.requeue(request.copy(failures = SdkJanitor.MaximumFailures))
        verify(mockTracker).failZkRecord(appId)
        assertResult(0)(queue.size())
      }
      "If the request has not exceeded the failure limit, add it back to the queue." in {
        worker.requeue(request.copy(failures = SdkJanitor.MaximumFailures - 1))
        verifyZeroInteractions(mockTracker)
        assertResult(1)(queue.size())
      }
    }
  }
}
