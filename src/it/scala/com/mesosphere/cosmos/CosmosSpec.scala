package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.app.{FlagParseException, FlagUsageError, Flags}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.finagle.http.RequestConfig.Yes
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.slf4j.{LoggerFactory, Logger}

/** Mixins used by all Cosmos tests. */
trait CosmosSpec extends Matchers with TableDrivenPropertyChecks {
  private[this] val name: String = getClass.getName.stripSuffix("$")
  protected[this] lazy val logger: Logger = LoggerFactory.getLogger(name)

  // Enable definition of implicit conversion methods; see below
  import scala.language.implicitConversions

  private val failfastOnFlagsNotParsed = false
  private val flag: Flags = new Flags(name, includeGlobal = true, failfastOnFlagsNotParsed)

  flag.parseArgs(args = Array(), allowUndefinedFlags = true) match {
    case Flags.Ok(remainder) =>
    // no-op
    case Flags.Help(usage) =>
      throw FlagUsageError(usage)
    case Flags.Error(reason) =>
      throw FlagParseException(reason)
  }

  protected[this] val adminRouterUri: Uri = dcosUri()
  protected[this] val marathonUri: Uri = adminRouterUri / "marathon"
  protected[this] val mesosUri: Uri = adminRouterUri / "mesos"

  logger.info("Connecting to admin router located at: {}", adminRouterUri)
  /*TODO: Not crazy about this being here, possibly find a better place.*/
  protected[this] lazy val adminRouter: AdminRouter = new AdminRouter(
    new MarathonClient(marathonUri, Services.marathonClient(marathonUri).get),
    new MesosMasterClient(mesosUri, Services.mesosClient(mesosUri).get)
  )

  protected[this] val servicePort: Int = 8081

  protected[this] final def requestBuilder(endpointPath: String): RequestBuilder[Yes, Nothing] = {
    RequestBuilder().url(s"http://localhost:$servicePort/$endpointPath")
  }

}
