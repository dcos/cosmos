package com.mesosphere.cosmos

import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.AppId
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonAppResponse, MarathonAppsResponse}
import com.mesosphere.cosmos.model.thirdparty.mesos.master._
import com.twitter.finagle.http._
import com.twitter.util.Future
import io.circe.Json

class AdminRouter(marathon: MarathonClient, mesos: MesosMasterClient) {

  def createApp(appJson: Json)(implicit session: RequestSession): Future[Response] = marathon.createApp(appJson)

  def getAppOption(appId: AppId)(implicit session: RequestSession): Future[Option[MarathonAppResponse]] = marathon.getAppOption(appId)

  def getApp(appId: AppId)(implicit session: RequestSession): Future[MarathonAppResponse] = marathon.getApp(appId)

  def listApps()(implicit session: RequestSession): Future[MarathonAppsResponse] = marathon.listApps()

  def deleteApp(appId: AppId, force: Boolean = false)(implicit session: RequestSession): Future[Response] = marathon.deleteApp(appId, force)

  def tearDownFramework(frameworkId: String)(implicit session: RequestSession): Future[MesosFrameworkTearDownResponse] = mesos.tearDownFramework(frameworkId)

  def getMasterState(frameworkName: String)(implicit session: RequestSession): Future[MasterState] = mesos.getMasterState(frameworkName)

}
