package com.mesosphere.cosmos

import java.net.{MalformedURLException, URISyntaxException}

import akka.http.scaladsl.model.headers
import com.mesosphere.cosmos.error.{CosmosException, EndpointUriSyntax, GenericHttpError, UnsupportedContentEncoding}
import com.mesosphere.http.MediaType
import io.lemonlabs.uri.Uri
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.filter.LogFormatter
import com.twitter.finagle.stats.StatsReceiver
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.{Gzip, NoCoding}
import akka.http.scaladsl.model.headers.{HttpEncodings, ProductVersion, `User-Agent`}
import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse, Uri => AkkaUri}
import com.mesosphere.usi.async.Retry
import org.slf4j.Logger

import scala.async.Async.{async, await}
import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}

object HttpClient {

  lazy val DEFAULT_RETRIES = maxRetryCount()

  lazy val RETRY_INTERVAL = retryDuration()

  lazy val PRODUCT_VERSION = ProductVersion("cosmos", BuildProperties().cosmosVersion)

  val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def fetch[A](
    uri: Uri,
    headers: HttpHeader*
  )(
    processResponse: HttpResponse => Future[A]
  )(
    implicit statsReceiver: StatsReceiver,
    ec: ExecutionContext,
    system: ActorSystem
  ): Future[A] = async {
    // Validate URI
    try { uri.toJavaURI.toURL }
    catch {
        case t @ (_: IllegalArgumentException | _: MalformedURLException | _: URISyntaxException) =>
          throw CosmosException(EndpointUriSyntax(uri, t.getMessage), t)
    }
    val userAgent = `User-Agent`(PRODUCT_VERSION)
    val request = HttpRequest(uri = AkkaUri(uri.toString()), headers = headers.toVector :+ userAgent)

    val futureResponse = retry { performRequest(request) }
    val response = decodeResponse(await(futureResponse))
    // TODO: Handle status such

    await(processResponse(response))
  }

  private[this] def retry[A](f: => Future[A])(
    implicit ec: ExecutionContext,
    system: ActorSystem
  ): Future[A] = {

    val isRetryApplicable = (ex: Throwable) => ex match {
      case ce: CosmosException => ce.error.isInstanceOf[GenericHttpError] &&
        ce.error.asInstanceOf[GenericHttpError].clientStatus.code() >= 500
      case _: IOException => true
    }

    Retry("request",
      maxAttempts = DEFAULT_RETRIES,
      minDelay = FiniteDuration(RETRY_INTERVAL.inMillis, MILLISECONDS),
      maxDelay = FiniteDuration(RETRY_INTERVAL.inMillis, MILLISECONDS),
      retryOn = isRetryApplicable)(f)
  }

  private[this] def performRequest(request: HttpRequest)(implicit ec: ExecutionContext, system: ActorSystem): Future[HttpResponse] = async {
    val response = await(Http().singleRequest(request))
    if(response.status.isRedirection()) {
      response.entity.discardBytes()

      val locationHeader = response.header[headers.Location].get
      val redirectUri = locationHeader.uri.resolvedAgainst(request.uri)
      val updatedRequest = request.withUri(redirectUri)
      // TODO: count redirections
      await(performRequest(updatedRequest))
    } else {
      response
    }
  }

  private def decodeResponse(response: HttpResponse)(implicit sr: StatsReceiver): HttpResponse = {
    val decoder = response.encoding match {
      case HttpEncodings.gzip =>
        sr.scope("contentEncoding").counter("gzip").incr()
        Gzip
      case HttpEncodings.identity =>
        sr.scope("contentEncoding").counter("plain").incr()
        NoCoding
      case unsupported =>
        throw UnsupportedContentEncoding(List("gzip"), Some(unsupported.value)).exception
    }

    decoder.decodeMessage(response)
  }

  def format(conn: HttpURLConnection): String = {
    val contentLength = conn.getContentLength
    val contentLengthStr = if (contentLength > 0) s"${contentLength.toString}B" else "-"
    val userAgent:Option[String] = Option(conn.getHeaderField(Fields.UserAgent))

    def escape(s: String) = LogFormatter.escape(s)

    s"${conn.getURL.getHost} - " +
      s"${escape("\"")}${escape(conn.getRequestMethod)} " +
      s"${escape(conn.getURL.toURI.toString)} " +
      s"${escape(conn.getURL.getProtocol)}${escape("\"")} " +
      s"${conn.getResponseCode} " +
      s"$contentLengthStr" +
      s"${userAgent match {
        case Some(uaStr) => s" ${escape("\"")}${escape(uaStr)}${escape("\"")}"
        case None => " -"
      }}"
  }

  final case class ResponseData(
    contentType: MediaType,
    contentLength: Option[Long],
    contentStream: InputStream
  )

  val TemporaryRedirect: Int = 307
  val PermanentRedirect: Int = 308

  val RedirectStatuses: Set[Int] = {
    Set(
      HttpURLConnection.HTTP_MOVED_PERM,
      HttpURLConnection.HTTP_MOVED_TEMP,
      HttpURLConnection.HTTP_SEE_OTHER,
      TemporaryRedirect,
      PermanentRedirect
    )
  }
}
