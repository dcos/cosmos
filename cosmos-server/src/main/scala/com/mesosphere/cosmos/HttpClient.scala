package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.error.EndpointUriConnection
import com.mesosphere.cosmos.error.EndpointUriSyntax
import com.mesosphere.cosmos.error.GenericHttpError
import com.mesosphere.cosmos.error.UnsupportedContentEncoding
import com.mesosphere.cosmos.error.UnsupportedRedirect
import com.mesosphere.http.MediaType
import com.mesosphere.http.MediaTypeParser
import io.lemonlabs.uri.Uri
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.filter.LogFormatter
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future
import io.netty.handler.codec.http.HttpResponseStatus
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.util.zip.GZIPInputStream
import org.slf4j.Logger
import scala.util.Failure
import scala.util.Success

object HttpClient {

  lazy val DEFAULT_RETRIES = maxRetryCount()

  lazy val RETRY_INTERVAL = retryDuration()

  val logger: Logger = org.slf4j.LoggerFactory.getLogger(getClass)

  def fetch[A](
    uri: Uri,
    headers: (String, String)*
  )(
    processResponse: ResponseData => A
  )(
    implicit statsReceiver: StatsReceiver
  ): Future[A] = {
    fetchResponse(uri, DEFAULT_RETRIES, headers: _*)
      .flatMap { case (responseData, conn) =>
        Future(processResponse(responseData))
          .ensure(responseData.contentStream.close())
          .ensure(conn.disconnect())
      }
      .handle { case e: IOException =>
        throw CosmosException(EndpointUriConnection(uri, e.getMessage), e)
      }
  }

  def fetchStream[A](
    uri: Uri,
    headers: (String, String)*
  )(
    processResponse: (ResponseData, HttpURLConnection) => A
  )(
    implicit statsReceiver: StatsReceiver
  ): Future[A] = {
    fetchResponse(uri, DEFAULT_RETRIES, headers: _*)
      .map { case (responseData, conn) => processResponse(responseData, conn) }
      .handle { case e: IOException =>
        throw CosmosException(EndpointUriConnection(uri, e.getMessage), e)
      }
  }

  private[this] def fetchResponse(
    uri: Uri,
    retryCount : Int,
    headers: (String, String)*
  )(
    implicit statsReceiver: StatsReceiver
  ): Future[(ResponseData, HttpURLConnection)] = {
    val isRetryApplicable = (ex: Exception) => ex match {
      case ce: CosmosException => ce.error.isInstanceOf[GenericHttpError] &&
        ce.error.asInstanceOf[GenericHttpError].clientStatus.code() >= 500
      case _: IOException => true
    }
    Future(uri.toJavaURI.toURL.openConnection())
      .handle {
        case t @ (_: IllegalArgumentException | _: MalformedURLException | _: URISyntaxException) =>
          throw CosmosException(EndpointUriSyntax(uri, t.getMessage), t)
      }
      .map { case conn: HttpURLConnection =>
        conn.setRequestProperty(Fields.UserAgent, s"cosmos/${BuildProperties().cosmosVersion}")
        // UserAgent set above can be overridden below.
        headers.foreach { case (name, value) => conn.setRequestProperty(name, value) }
        logger.info(format(conn))
        val responseData = extractResponseData(uri, conn)
        (responseData, conn)
      }
      .rescue {
        case ex: Exception if isRetryApplicable(ex) =>
          if (retryCount > 0) {
            logger.info(s"Retry [remaining - $retryCount] : ${ex.getMessage}")
            Future.sleep(RETRY_INTERVAL).before(fetchResponse(uri, retryCount - 1, headers: _*))
          } else {
            logger.warn(s"Retries exhausted, giving up due to ${ex.getMessage}", ex)
            throw ex
          }
      }
  }

  private[this] def extractResponseData(
    uri: Uri,
    conn: HttpURLConnection
  )(implicit
    sr: StatsReceiver
  ): ResponseData = {
    val (contentType, contentEncoding) = parseContentHeaders(uri, conn)

    val contentLength = conn.getContentLengthLong match {
      case len if len < 0 => None
      case len => Some(len)
    }

    val contentStream = prepareContentStream(conn, contentEncoding)
    ResponseData(contentType, contentLength, contentStream)
  }

  private def parseContentHeaders(
    uri: Uri,
    conn: HttpURLConnection
  )(implicit
    sr: StatsReceiver
  ): (MediaType, Option[String]) = {
    conn.getResponseCode match {
      case HttpURLConnection.HTTP_OK =>
        sr.scope("status").counter("200").incr()
        val contentEncoding = Option(conn.getHeaderField(Fields.ContentEncoding))
        MediaTypeParser.parse(conn.getHeaderField(Fields.ContentType)) match {
          case Success(contentType) => (contentType, contentEncoding)
          case Failure(error) => {
            logger.error(s"Error while parsing the Content-Type " +
              s"${conn.getHeaderField(Fields.ContentType)} from URI $uri",
              error
            )
            throw error
          }
        }
      case status if RedirectStatuses(status) =>
        sr.scope("status").counter(status.toString).incr()
        // Different forms of redirect, HttpURLConnection won't follow a redirect across schemes

        val loc = Option(conn.getHeaderField("Location")).map(Uri.parse).flatMap(_.schemeOption)
        throw UnsupportedRedirect(List(uri.schemeOption.get), loc).exception
      case status =>
        sr.scope("status").counter(status.toString).incr()
        throw GenericHttpError(uri = uri, clientStatus = HttpResponseStatus.valueOf(status)).exception
    }
  }

  private def prepareContentStream(
    conn: HttpURLConnection,
    contentEncoding: Option[String]
  )(implicit
    sr: StatsReceiver
  ): InputStream = {
    contentEncoding match {
      case Some("gzip") =>
        sr.scope("contentEncoding").counter("gzip").incr()
        new GZIPInputStream(conn.getInputStream)
      case ce @ Some(_) =>
        throw UnsupportedContentEncoding(List("gzip"), ce).exception
      case _ =>
        sr.scope("contentEncoding").counter("plain").incr()
        conn.getInputStream
    }
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
