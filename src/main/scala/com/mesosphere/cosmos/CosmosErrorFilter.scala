package com.mesosphere.cosmos

import com.twitter.finagle.http._
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.util.Future

class CosmosErrorFilter()(implicit baseScope: BaseScope, statsReceiver: StatsReceiver) extends SimpleFilter[Request, Response] {
  lazy val logger = org.slf4j.LoggerFactory.getLogger(classOf[CosmosErrorFilter])
  val stats = {
    baseScope.name match {
      case Some(bs) if bs.nonEmpty => statsReceiver.scope(s"$bs/errorFilter")
      case _ => statsReceiver.scope("errorFilter")
    }
  }
  override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
    service(request)
      .map { resp =>
        val code = resp.status.code
        if (500 <= code && code < 600) {
          stats.counter(s"manual5xxResponse/$code").incr()
          logger.warn("Manually returned 5xx response: {}", resp.toString)
        }
        resp
      }
      .handle {
        case ce: CosmosError =>
          stats.counter(s"definedError/${ce.getClass.getName}").incr()
          val response = Response(ce.status)
          val jsonString = CosmosError.jsonEncoder(ce).noSpaces
          response.setContentTypeJson()
          response.setContentString(jsonString)
          response
        case t: Throwable =>
          stats.counter(s"unhandledThrowable/${t.getClass.getName}").incr()
          logger.warn("Unhandled exception: ", t)
          val response = Response(Status.InternalServerError)
          response.setContentTypeJson()
          response.setContentString(s"""{"message":"${t.getMessage}"}""")
          response
      }
  }
}
