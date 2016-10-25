package com.mesosphere.cosmos

import java.net.InetSocketAddress

import com.mesosphere.cosmos.Uris._
import com.netaporter.uri.Uri
import com.twitter.finagle.client.Transporter
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.ssl.Ssl
import com.twitter.finagle.transport.Transport
import com.twitter.finagle._
import com.twitter.util.{Future, Try}

object Services {
  def adminRouterClient(uri: Uri): Try[Service[Request, Response]] = {
    httpClient("adminRouter", uri)
  }

  def marathonClient(uri: Uri): Try[Service[Request, Response]] = {
    httpClient("marathon", uri)
  }

  def mesosClient(uri: Uri): Try[Service[Request, Response]] = {
    httpClient("mesos", uri)
  }

  def httpClient(
    serviceName: String,
    uri: Uri,
    postAuthFilter: SimpleFilter[Request, Response] = Filter.identity
  ): Try[Service[Request, Response]] = {
    extractHostAndPort(uri) map { case ConnectionDetails(hostname, port, tls) =>
      val cBuilder = tls match {
        case false =>
          Http.client
        case true =>
          Http.client
            .configured(Transport.TLSClientEngine(Some({
              case inet: InetSocketAddress => Ssl.client(hostname, inet.getPort)
              case _ => Ssl.client()
            })))
            .configured(Transporter.TLSHostname(Some(hostname)))
      }

      new ConnectionExceptionHandler(serviceName) andThen
        new AuthFilter(serviceName) andThen
        postAuthFilter andThen
        cBuilder.newService(s"$hostname:$port", serviceName)
    }
  }

  private[this] class ConnectionExceptionHandler(serviceName: String) extends SimpleFilter[Request, Response] {
    override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
      service(request)
        .rescue {
          case ce: ChannelException =>
            Future.exception(new ServiceUnavailable(serviceName, ce))
          case e: NoBrokersAvailableException =>
            Future.exception(new ServiceUnavailable(serviceName, e))
          case e: RequestException =>
            Future.exception(new ServiceUnavailable(serviceName, e))
        }
    }
  }

  private[this] class AuthFilter(serviceName: String) extends SimpleFilter[Request, Response] {
    override def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
      service(request)
        .map { response =>
          response.status match {
            case Status.Unauthorized => throw Unauthorized(serviceName, response.headerMap.get("WWW-Authenticate"))
            case Status.Forbidden => throw Forbidden(serviceName)
            case _ => response
          }
        }
    }
  }

}
