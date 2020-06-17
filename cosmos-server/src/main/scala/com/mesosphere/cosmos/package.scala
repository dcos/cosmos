package com.mesosphere

import com.mesosphere.cosmos.model.ZooKeeperUri
import com.mesosphere.error.ResultOps
import io.lemonlabs.uri.Uri
import com.twitter.app.GlobalFlag
import com.twitter.conversions.storage._
import com.twitter.conversions.time._
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.filter.CommonLogFormatter
import com.twitter.finagle.http.filter.LoggingFilter
import com.twitter.util.Duration
import com.twitter.util.ScheduledThreadPoolTimer
import com.twitter.util.StorageUnit
import com.twitter.util.Timer
import java.net.InetSocketAddress
import java.nio.file.Path

package object cosmos {
  implicit val globalTimer: Timer = new ScheduledThreadPoolTimer()

  def trimContentForPrinting(content: String) : String = {
    val maxLimit = maxClientResponseSize().bytes
    if (content.length < maxLimit) {
      content
    } else {
      s"${content.substring(0, (maxLimit.toInt-1)/2)}..." +
        s"...${content.substring(content.length - (maxLimit.toInt-1)/2)}"
    }
  }

  object CustomLoggingFilter extends LoggingFilter[Request](
    log = com.twitter.logging.Logger("access"),
    formatter = new CommonLogFormatter {
      override def format(request: Request, response: Response, responseTime: Duration): String = {
        val remoteAddr = request.remoteAddress.getHostAddress

        val contentLength = response.length
        val contentLengthStr = if (contentLength > 0) s"${contentLength.toString}B" else "-"

        val builder = new StringBuilder
        builder.append(remoteAddr)
        builder.append(" - \"")
        builder.append(escape(request.method.toString))
        builder.append(' ')
        builder.append(escape(request.uri))
        builder.append(' ')
        builder.append(escape(request.version.toString))
        builder.append("\" ")
        builder.append(response.statusCode.toString)
        builder.append(' ')
        builder.append(contentLengthStr)
        builder.append(' ')
        builder.append(responseTime.inMillis)
        builder.append("ms \"")
        builder.append(escape(request.userAgent.getOrElse("-")))
        builder.append('"')

        if (response.statusCode / 100 != 2) {
          val headersMap = request.headerMap
          headersMap.get(Fields.Authorization) match {
            case Some(_) => headersMap.put(Fields.Authorization, "********")
            case None => ()
          }
          builder.append(s" Headers : (${headersMap.map(_.productIterator.mkString(":")).mkString(", ")})")
        }

        builder.toString
      }
    }
  )
}

/* A flag's name is the fully-qualified classname. GlobalFlag doesn't support package object. We
 * must instead use regular package declarations
 */
package cosmos {
  // scalastyle:off object.name
  object dcosUri extends GlobalFlag[Uri](
    s"The URI where the DCOS Admin Router is located. If this flag is set, " +
    s"${mesosMasterUri.name} and ${marathonUri.name} will be ignored"
  )

  object adminRouterUri extends GlobalFlag[Uri](
    Uri.parse("http://master.mesos"),
    "The URI where AdminRouter can be found"
  )

  object marathonUri extends GlobalFlag[Uri](
    Uri.parse("http://master.mesos:8080"),
    "The URI where marathon can be found"
  )

  object mesosMasterUri extends GlobalFlag[Uri](
    Uri.parse("http://leader.mesos:5050"),
    "The URI where the leading Mesos master can be found"
  )

  object zookeeperUri extends GlobalFlag[ZooKeeperUri](
    ZooKeeperUri.parse("zk://127.0.0.1:2181/cosmos").getOrThrow,
    "The ZooKeeper connection string"
  )

  object httpInterface extends GlobalFlag[Option[InetSocketAddress]](
    Some(new InetSocketAddress("127.0.0.1", 7070)), //scalastyle:ignore magic.number
    "The TCP Interface and port for the http server {[<hostname/ip>]:port}. (Set to " +
    "empty value to disable)"
  )

  object httpsInterface extends GlobalFlag[InetSocketAddress](
    "The TCP Interface and port for the https server {[<hostname/ip>]:port}. Requires " +
    s"-${certificatePath.name} and -${keyPath.name} to be set. (Set to empty value to disable)"
  )

  object certificatePath extends GlobalFlag[Path](
    "Path to PEM format SSL certificate file"
  )

  object keyPath extends GlobalFlag[Path](
    "Path to SSL Key file"
  )

  object maxClientResponseSize extends GlobalFlag[StorageUnit](
    40.megabytes,
    "Maximum size for the response for requests initiated by Cosmos in Megabytes"
  )

  object retryDuration extends GlobalFlag[Duration](
    5.seconds,
    "Duration for retrying a failed upstream (HTTP) request"
  )

  object maxRetryCount extends GlobalFlag[Int](
    3,
    "Maximum number of retries on HTTP upstreams (repositories & /resource endpoints)"
  )
  // scalastyle:on object.name
}
