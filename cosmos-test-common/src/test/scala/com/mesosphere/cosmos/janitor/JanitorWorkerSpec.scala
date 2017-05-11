package com.mesosphere.cosmos.janitor

import java.io.IOException
import java.util.concurrent.DelayQueue

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.MarathonAppNotFound
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.janitor.SdkJanitor.JanitorRequest
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppResponse
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.util.Awaitable.CanAwait
import com.twitter.util.Duration
import com.twitter.util.Future
import org.scalatest.BeforeAndAfterEach
import org.scalatest.FreeSpec
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._

class JanitorWorkerSpec extends FreeSpec with MockitoSugar with BeforeAndAfterEach {
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

  private[this] var queue: DelayQueue[JanitorRequest] = _
  private[this] var worker: JanitorWorker = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(mockTracker)
    reset(mockAdminRouter)

    queue = new DelayQueue[JanitorRequest]()
    worker = new JanitorWorker(queue, true, mockTracker, mockAdminRouter)
  }


  "In the JanitorWorker" - {
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
        assertResult(1)(queue.peek().failures)
      }
      "If the app should be deleted, it is" in {
        when(mockAdminRouter.getApp(appId)(mockSession))
          .thenReturn(Future.value(MarathonAppResponse(new MarathonApp(appId, Map()))))

        val mockFuture = mock[Future[Response]]
        val mockResponse = mock[Response]
        when(mockFuture.isReady(any[CanAwait])).thenReturn(true)
        when(mockFuture.result(any[Duration])(any[CanAwait])).thenReturn(mockResponse)
        when(mockAdminRouter.getSdkServicePlanStatus(
          service = appId.toString,
          apiVersion = "v1",
          plan = "deploy"
        )(mockSession)).thenReturn(mockFuture)
        when(mockResponse.status).thenReturn(Status.Ok)

        val mockDeleteFuture = mock[Future[Response]]
        val mockDeleteResponse = mock[Response]
        when(mockDeleteFuture.isReady(any[CanAwait])).thenReturn(true)
        when(mockDeleteFuture.result(any[Duration])(any[CanAwait])).thenReturn(mockDeleteResponse)
        when(mockAdminRouter.deleteApp(appId)(mockSession)).thenReturn(mockDeleteFuture)
        when(mockDeleteResponse.statusCode).thenReturn(Status.Ok.code)

        worker.doWork(request)
        verify(mockTracker).deleteZkRecord(appId)
        verifyNoMoreInteractions(mockTracker)
        assertResult(0)(queue.size())
      }
      "If the app is not ready to be deleted, it is requeued" in {
        when(mockAdminRouter.getApp(appId)(mockSession))
          .thenReturn(Future.value(MarathonAppResponse(new MarathonApp(appId, Map()))))

        val mockFuture = mock[Future[Response]]
        val mockResponse = mock[Response]
        when(mockFuture.isReady(any[CanAwait])).thenReturn(true)
        when(mockFuture.result(any[Duration])(any[CanAwait])).thenReturn(mockResponse)
        when(mockAdminRouter.getSdkServicePlanStatus(
          service = appId.toString,
          apiVersion = "v1",
          plan = "deploy"
        )(mockSession)).thenReturn(mockFuture)
        when(mockResponse.status).thenReturn(Status.Accepted)

        worker.doWork(request)
        verifyZeroInteractions(mockTracker)
        assertResult(1)(queue.size())
      }
    }
    "In checkUninstall" - {
      val labels = Map(SdkJanitor.SdkApiVersionLabel -> "v1")
      val app = new MarathonApp(appId, labels)

      val mockFuture = mock[Future[Response]]
      val mockResponse = mock[Response]

      def setup(): Unit = {
        when(mockFuture.isReady(any[CanAwait])).thenReturn(true)
        when(mockFuture.result(any[Duration])(any[CanAwait])).thenReturn(mockResponse)
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
        val mockFuture = mock[Future[Response]]
        when(mockFuture.isReady(any[CanAwait])).thenReturn(true)
        when(mockFuture.result(any[Duration])(any[CanAwait])).thenReturn(mockResponse)
        when(mockAdminRouter.deleteApp(appId)(mockSession)).thenReturn(mockFuture)
        ()
      }
      "If the delete response is 4XX, fail the request" in {
        setup()
        when(mockResponse.statusCode).thenReturn(Status.Unauthorized.code)

        worker.delete(request)
        verify(mockTracker).failZkRecord(appId)
      }
      "If the delete response is 5XX, requeue the request" in {
        setup()
        when(mockResponse.statusCode).thenReturn(Status.BadGateway.code)

        worker.delete(request)
        verifyZeroInteractions(mockTracker)

        assertResult(1)(queue.size())
        assertResult(1)(queue.peek().failures)
      }
      "If the response is 200, don't requeue and delete the record" in {
        setup()
        when(mockResponse.statusCode).thenReturn(Status.Ok.code)

        worker.delete(request)
        verify(mockTracker).deleteZkRecord(appId)
        assertResult(0)(queue.size())
      }
      "Any other response, requeue the request" in {
        setup()
        when(mockResponse.statusCode).thenReturn(Status.TemporaryRedirect.code)

        worker.delete(request)
        assertResult(1)(queue.size())
        assertResult(1)(queue.peek().failures)
      }
      "If an exception is thrown, requeue the request" in {
        setup()
        when(mockResponse.statusCode).thenThrow(new NullPointerException())

        worker.delete(request)
        assertResult(1)(queue.size())
        assertResult(1)(queue.peek().failures)
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
