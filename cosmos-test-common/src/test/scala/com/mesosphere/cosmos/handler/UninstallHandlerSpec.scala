package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.AdminRouter
import com.mesosphere.cosmos.AdminRouterClient
import com.mesosphere.cosmos.AppAlreadyUninstalling
import com.mesosphere.cosmos.CosmosException
import com.mesosphere.cosmos.FailedToStartUninstall
import com.mesosphere.cosmos.MarathonClient
import com.mesosphere.cosmos.MesosMasterClient
import com.mesosphere.cosmos.handler.UninstallHandler.SdkUninstall
import com.mesosphere.cosmos.handler.UninstallHandler.UninstallDetails
import com.mesosphere.cosmos.handler.UninstallHandler.UninstallOperation
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.janitor.Janitor
import com.mesosphere.cosmos.janitor.SdkJanitor
import com.mesosphere.cosmos.repository.PackageCollection
import com.mesosphere.cosmos.thirdparty.marathon.model.AppId
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import com.twitter.util.Future
import io.circe.JsonObject
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.mockito.MockitoSugar

class UninstallHandlerSpec
extends FreeSpec
with Matchers
with BeforeAndAfterEach
with MockitoSugar {

  private val appId = AppId("/test")

  private[this] var mockAdmin: AdminRouter = _
  private[this] var mockPackageCollection: PackageCollection = _
  private[this] var mockSdkJanitor: Janitor = _
  private[this] var mockSession: RequestSession = _


  private[this] var uninstallHandler: UninstallHandler = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    mockAdmin = mock[AdminRouter]
    mockPackageCollection = mock[PackageCollection]
    mockSdkJanitor = mock[Janitor]
    mockSession = mock[RequestSession]

    uninstallHandler = new UninstallHandler(mockAdmin, mockPackageCollection, mockSdkJanitor)
  }

  "In the UninstallHandler" - {
    "If a sdk uninstall is already locked, it is not run again" in {
      when(mockSdkJanitor.claimUninstall(appId)).thenReturn(SdkJanitor.UninstallClaimDenied)

      val exception = intercept[CosmosException](
        uninstallHandler.runSdkUninstall(
          UninstallOperation(
            appId,
            "test",
            Option.empty,
            Option.empty[String],
            SdkUninstall
          )
        )(mockSession)
      )

      exception.error shouldBe a[AppAlreadyUninstalling]

      verify(mockSdkJanitor).claimUninstall(appId)
      verifyNoMoreInteractions(mockSdkJanitor)
    }
    "If a sdk uninstall is unable to update the marathon app an error is returned" in {
      when(mockSdkJanitor.claimUninstall(appId)).thenReturn(SdkJanitor.UninstallClaimGranted)

      val router = new MockModifyAppAdminRouter(mock[AdminRouterClient],
        mock[MarathonClient],
        mock[MesosMasterClient],
        Response.apply(Status.BadGateway))

      uninstallHandler = new UninstallHandler(router, mockPackageCollection, mockSdkJanitor)

      val exception = intercept[CosmosException](
        Await.result(
          uninstallHandler.runSdkUninstall(
            UninstallOperation(
              appId,
              "test",
              Option.empty,
              Option.empty[String],
              SdkUninstall
            )
          )(mockSession)
        )
      )

      exception.error shouldBe a[FailedToStartUninstall]

      verify(mockSdkJanitor).claimUninstall(appId)
      verify(mockSdkJanitor).releaseUninstall(appId)
      verifyNoMoreInteractions(mockSdkJanitor)
    }
    "If marathon is updated, then the janitor is told to delete the app" in {
      when(mockSdkJanitor.claimUninstall(appId)).thenReturn(SdkJanitor.UninstallClaimGranted)

      val router = new MockModifyAppAdminRouter(mock[AdminRouterClient],
        mock[MarathonClient],
        mock[MesosMasterClient],
        Response.apply(Status.Ok))

      uninstallHandler = new UninstallHandler(router, mockPackageCollection, mockSdkJanitor)

      assertResult(new UninstallDetails(appId,
        "test",
        Option.empty,
        Option.empty,
        Option.empty))(Await.result(uninstallHandler.runSdkUninstall(UninstallOperation(
        appId,
        "test",
        Option.empty,
        Option.empty,
        SdkUninstall
      ))(mockSession)))

      verify(mockSdkJanitor).delete(appId, mockSession)
    }
  }
}

class MockModifyAppAdminRouter(
  adminRouterClient: AdminRouterClient,
  marathonClient: MarathonClient,
  mesosMasterClient: MesosMasterClient,
  response: Response
) extends AdminRouter(adminRouterClient, marathonClient, mesosMasterClient) {

  override def modifyApp(
    appId: AppId
  )(
    f: (JsonObject) => JsonObject
  )(
    implicit session: RequestSession
  ): Future[Response] = {
    Future.value(response)
  }
}
