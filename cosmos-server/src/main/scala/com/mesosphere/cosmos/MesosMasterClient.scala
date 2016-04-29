package com.mesosphere.cosmos

import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.model.thirdparty.mesos.master.{MasterState, MesosFrameworkTearDownResponse}
import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.finagle.Service
import com.twitter.finagle.http._
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http.HttpMethod

class MesosMasterClient(
  mesosUri: Uri,
  client: Service[Request, Response],
  authorization: Option[String]
) extends ServiceClient(mesosUri, authorization) {

  def tearDownFramework(frameworkId: String): Future[MesosFrameworkTearDownResponse] = {
    val formData = Uri.empty.addParam("frameworkId", frameworkId)
    // scala-uri makes it convenient to encode the actual framework id, but it will think its for a Uri
    // so we strip the leading '?' that signifies the start of a query string
    val encodedString = formData.toString.substring(1)
    val uri = "master" / "teardown"
    client(postForm(uri, encodedString))
      .map(validateResponseStatus(HttpMethod.POST, uri, _))
      .map { _ => MesosFrameworkTearDownResponse() }
  }

  def getMasterState(frameworkName: String): Future[MasterState] = {
    val uri = "master" / "state.json"
    val request = get(uri)
    client(request).flatMap(decodeTo[MasterState](HttpMethod.GET, uri, _))
  }

}
