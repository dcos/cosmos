package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonAppResponse, MarathonAppsResponse}
import com.mesosphere.cosmos.thirdparty.mesos.master.model._
import com.twitter.finagle.http._
import com.twitter.util.Future
import io.circe.Json

class AdminRouter(
  adminRouterClient: AdminRouterClient,
  marathon: MarathonClient,
  mesos: MesosMasterClient
) {

  def createApp(appJson: Json)(implicit session: RequestSession): Future[Response] = marathon.createApp(appJson)

  def getAppOption(appId: AppId)(implicit session: RequestSession): Future[Option[MarathonAppResponse]] = marathon.getAppOption(appId)

  def getApp(appId: AppId)(implicit session: RequestSession): Future[MarathonAppResponse] = marathon.getApp(appId)

  def listApps()(implicit session: RequestSession): Future[MarathonAppsResponse] = marathon.listApps()

  def deleteApp(appId: AppId, force: Boolean = false)(implicit session: RequestSession): Future[Response] = marathon.deleteApp(appId, force)

  def tearDownFramework(frameworkId: String)(implicit session: RequestSession): Future[MesosFrameworkTearDownResponse] = mesos.tearDownFramework(frameworkId)

  def getMasterState(frameworkName: String)(implicit session: RequestSession): Future[MasterState] = mesos.getMasterState(frameworkName)

  def getDcosVersion()(implicit session: RequestSession): Future[DcosVersion] = adminRouterClient.getDcosVersion()

}
