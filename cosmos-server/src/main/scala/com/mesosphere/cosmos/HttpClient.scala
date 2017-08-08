package com.mesosphere.cosmos

import com.mesosphere.cosmos.error.UnsupportedContentEncoding
import com.mesosphere.cosmos.error.UnsupportedRedirect
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeParser
import com.netaporter.uri.Uri
import com.twitter.finagle.http.Fields
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.util.zip.GZIPInputStream

object HttpClient {

  def fetch[A](
    uri: Uri,
    statsReceiver: StatsReceiver,
    headers: (String, String)*
  )(
    processBody: (MediaType, InputStream) => A
  ): Future[Either[Error, A]] = {
    implicit val sr: StatsReceiver = statsReceiver

    Future(uri.toURI.toURL.openConnection())
      .handle {
        case t @ (_: IllegalArgumentException | _: MalformedURLException | _: URISyntaxException) =>
          throw UriSyntax(t)
      }
      .flatMap { case conn: HttpURLConnection =>
        headers.foreach { case (name, value) => conn.setRequestProperty(name, value) }

        val (contentType, contentEncoding) = parseContentHeaders(uri, conn)
        val contentStream = prepareContentStream(conn, contentEncoding)

        Future(Right(processBody(contentType, contentStream)))
          .ensure(contentStream.close())
          .ensure(conn.disconnect())
      }
      .handle { case e: IOException => throw UriConnection(e) }
      .handle { case e: Error => Left(e) }
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
        // TODO proxy Handle error cases
        val contentType = MediaTypeParser.parseUnsafe(conn.getHeaderField(Fields.ContentType))
        val contentEncoding = Option(conn.getHeaderField(Fields.ContentEncoding))
        (contentType, contentEncoding)
      case status if RedirectStatuses(status) =>
        sr.scope("status").counter(status.toString).incr()
        // Different forms of redirect, HttpURLConnection won't follow a redirect across schemes
        val loc = Option(conn.getHeaderField("Location")).map(Uri.parse).flatMap(_.scheme)
        throw UnsupportedRedirect(List(uri.scheme.get), loc).exception
      case status =>
        sr.scope("status").counter(status.toString).incr()
        throw UnexpectedStatus(status)
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

  sealed trait Error extends Exception
  final case class UriSyntax(cause: Throwable) extends Error
  final case class UriConnection(cause: IOException) extends Error
  final case class UnexpectedStatus(clientStatus: Int) extends Error

}
