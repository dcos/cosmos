package com.mesosphere.cosmos.model.mesos.master

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
  id: String,
  labels: Map[String, String],
  uris: List[String] /*TODO: uri type*/
)
