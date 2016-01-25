package com.mesosphere.cosmos.model.mesos.master

import com.mesosphere.cosmos.model.AppId

//todo: flush out
case class MasterState(
  frameworks: List[Framework]
)

case class Framework(
  id: String,
  name: String
)

case class MesosFrameworkTearDownResponse()

case class MarathonAppResponse(app: MarathonApp)
case class MarathonAppsResponse(apps: List[MarathonApp])
case class MarathonApp(
  id: AppId,
  labels: Map[String, String],
  uris: List[String] /*TODO: uri type*/
)
