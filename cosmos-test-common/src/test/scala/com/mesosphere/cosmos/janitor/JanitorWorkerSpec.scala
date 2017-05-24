package com.mesosphere.cosmos.janitor

import java.lang.Thread.State
import java.util.concurrent.DelayQueue
import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.janitor.SdkJanitor.JanitorRequest
import com.mesosphere.cosmos.janitor.SdkJanitor.Request
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonApp
import com.mesosphere.cosmos.thirdparty.marathon.model.MarathonAppResponse
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
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
    failures = List(),
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
      "If an exception is thrown, the request is requeued" in {
        when(mockAdminRouter.getApp(appId)(mockSession)).thenThrow(new RuntimeException("test!"))
        worker.doWork(request)

        assertResult(1)(queue.size())
        assertResult(List("Encountered exception: test!"))(queue.peek().asInstanceOf[JanitorRequest].failures)
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
        verify(mockTracker).completeUninstall(appId)
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

      var mockFuture: Future[Response] = Future.value(Response(Status.Ok))

      def setup(status: Status): Unit = {
        mockFuture = Future.value(Response(status))

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
          setup(Status.Ok)

          assertResult(true)(worker.checkUninstall(noLabelsApp)(mockSession))
        }
        "If present, value is used" in {
          val newApp = new MarathonApp(appId, Map(SdkJanitor.SdkApiVersionLabel -> "v2"))
          setup(Status.Ok)

          reset(mockAdminRouter)
          when(mockAdminRouter.getSdkServicePlanStatus(
            service = appId.toString,
            apiVersion = "v2",
            plan = "deploy"
          )(mockSession)).thenReturn(mockFuture)

          assertResult(true)(worker.checkUninstall(newApp)(mockSession))
        }
      }
      "If the plan status is 200 OK, return true" in {
        setup(Status.Ok)

        assertResult(true)(worker.checkUninstall(app)(mockSession))
      }
      "If the plan status is anything but 200 OK, return false" in {
        setup(Status.BadGateway)

        assertResult(false)(worker.checkUninstall(app)(mockSession))
      }
      "If an exception is thrown, return false" in {
        setup(Status.Ok)
        reset(mockAdminRouter)
        when(mockAdminRouter.getSdkServicePlanStatus(
          service = appId.toString,
          apiVersion = "v1",
          plan = "deploy"
        )(mockSession)).thenThrow(new RuntimeException("A network error"))

        assertResult(false)(worker.checkUninstall(app)(mockSession))
      }
    }
    "In delete" - {
      def setup(status: Status): Unit = {
        val mockResponse = Response(status)
        val mockFuture = Future.value(mockResponse)
        when(mockAdminRouter.deleteApp(appId)(mockSession)).thenReturn(mockFuture)
        ()
      }
      "If the delete response is 4XX, fail the request" in {
        setup(Status.Unauthorized)

        worker.delete(request)
        verify(mockTracker).failUninstall(appId, List("Encountered Marathon error: Status(401)"))
      }
      "If the delete response is 5XX, requeue the request" in {
        setup(Status.BadGateway)

        worker.delete(request)
        verifyZeroInteractions(mockTracker)

        assertResult(1)(queue.size())
        assertResult(1)(queue.peek().asInstanceOf[JanitorRequest].failures.length)
      }
      "If the response is 200, don't requeue and delete the record" in {
        setup(Status.Ok)

        worker.delete(request)
        verify(mockTracker).completeUninstall(appId)
        assertResult(0)(queue.size())
      }
      "Any other response, requeue the request" in {
        setup(Status.TemporaryRedirect)

        worker.delete(request)
        assertResult(1)(queue.size())
        assertResult(List("Encountered unexpected status: Status(307)"))(queue.peek().asInstanceOf[JanitorRequest].failures)
      }
      "If an exception is thrown, requeue the request" in {
        setup(Status.Ok)
        when(mockAdminRouter.deleteApp(appId)(mockSession)).thenThrow(new RuntimeException("A network error."))

        worker.delete(request)
        assertResult(1)(queue.size())
        assertResult(List("Encountered exception: A network error."))(queue.peek().asInstanceOf[JanitorRequest].failures)
      }
    }
    "In fail" - {
      "Notify the tracker to fail the uninstall" in {
        worker.fail(request)
        verify(mockTracker).failUninstall(appId, request.failures)
      }
    }
    "In requeue" - {
      "If the request has exceeded the failure limit, fail it" in {
        val failures = List("1", "2", "3", "4", "5")
        worker.requeue(request.copy(failures = failures))
        verify(mockTracker).failUninstall(appId, failures)
        assertResult(0)(queue.size())
      }
      "If the request has not exceeded the failure limit, add it back to the queue." in {
        val failures = List("1", "2", "3", "4")
        worker.requeue(request.copy(failures = failures))
        verifyZeroInteractions(mockTracker)
        assertResult(1)(queue.size())
      }
    }
  }
}
