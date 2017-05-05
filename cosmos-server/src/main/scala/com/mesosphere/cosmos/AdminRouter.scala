package com.mesosphere.cosmos

import _root_.io.circe.JsonObject
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.thirdparty.adminrouter.model.DcosVersion
import com.mesosphere.cosmos.thirdparty.marathon.model.{AppId, MarathonAppResponse, MarathonAppsResponse}
import com.mesosphere.cosmos.thirdparty.mesos.master.model._
import com.twitter.finagle.http._
import com.twitter.util.Future

class AdminRouter(
  adminRouterClient: AdminRouterClient,
  marathon: MarathonClient,
  mesos: MesosMasterClient
) {

  def createApp(appJson: JsonObject)(implicit session: RequestSession): Future[Response] = marathon.createApp(appJson)

  def getAppOption(appId: AppId)(implicit session: RequestSession): Future[Option[MarathonAppResponse]] = marathon.getAppOption(appId)

  def getApp(appId: AppId)(implicit session: RequestSession): Future[MarathonAppResponse] = marathon.getApp(appId)

  def getRawApp(appId: AppId)(implicit session: RequestSession): Future[JsonObject] = marathon.getRawApp(appId)

  def updateApp(appId: AppId, appJson: JsonObject)(implicit session: RequestSession): Future[Response] = marathon.updateApp(appId, appJson)

  def setAppEnvvar(appId: AppId, envvar: String, value: String)(implicit session: RequestSession): Future[Response]
    = marathon.setAppEnvvar(appId, envvar, value)

  def listApps()(implicit session: RequestSession): Future[MarathonAppsResponse] = marathon.listApps()

  def deleteApp(appId: AppId, force: Boolean = false)(implicit session: RequestSession): Future[Response] = marathon.deleteApp(appId, force)

  def tearDownFramework(frameworkId: String)(implicit session: RequestSession): Future[MesosFrameworkTearDownResponse] = mesos.tearDownFramework(frameworkId)

  def getFrameworks(
    frameworkName: String
  )(
    implicit session: RequestSession
  ): Future[List[Framework]] = mesos.getFrameworks(frameworkName)

  def getDcosVersion()(implicit session: RequestSession): Future[DcosVersion] = adminRouterClient.getDcosVersion()

}
