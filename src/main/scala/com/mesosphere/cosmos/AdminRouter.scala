package com.mesosphere.cosmos

import com.mesosphere.cosmos.model.AppId
import com.mesosphere.cosmos.model.thirdparty.marathon.{MarathonAppResponse, MarathonAppsResponse}
import com.mesosphere.cosmos.model.thirdparty.mesos.master._
import com.twitter.finagle.http._
import com.twitter.util.Future
import io.circe.Json

class AdminRouter(marathon: MarathonClient, mesos: MesosMasterClient) {

  def createApp(appJson: Json): Future[Response] = marathon.createApp(appJson)

  def getAppOption(appId: AppId): Future[Option[MarathonAppResponse]] = marathon.getAppOption(appId)

  def getApp(appId: AppId): Future[MarathonAppResponse] = marathon.getApp(appId)

  def listApps(): Future[MarathonAppsResponse] = marathon.listApps()

  def deleteApp(appId: AppId, force: Boolean = false): Future[Response] = marathon.deleteApp(appId, force)

  def tearDownFramework(frameworkId: String): Future[MesosFrameworkTearDownResponse] = mesos.tearDownFramework(frameworkId)

  def getMasterState(frameworkName: String): Future[MasterState] = mesos.getMasterState(frameworkName)

}
