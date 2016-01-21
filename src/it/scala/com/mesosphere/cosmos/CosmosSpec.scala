package com.mesosphere.cosmos

import com.netaporter.uri.Uri
import com.netaporter.uri.Uri
import com.twitter.app.{FlagParseException, FlagUsageError, Flags}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.finagle.http.RequestConfig.Yes
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks

/** Mixins used by all Cosmos tests. */
trait CosmosSpec extends Matchers with TableDrivenPropertyChecks {
  private val name: String = getClass.getName.stripSuffix("$")
  protected[this] lazy val logger = org.slf4j.LoggerFactory.getLogger(name)

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

  protected[this] val adminRouterHost: Uri = dcosHost()

  logger.info("Connection to admin router located at: {}", adminRouterHost)
  /*TODO: Not crazy about this being here, possibly find a better place.*/
  protected[this] lazy val adminRouter: AdminRouter =
    new AdminRouter(adminRouterHost, Services.adminRouterClient(adminRouterHost).get)

  protected[this] val servicePort: Int = 8081

  protected[this] final def requestBuilder(endpointPath: String): RequestBuilder[Yes, Nothing] = {
    RequestBuilder().url(s"http://localhost:$servicePort/$endpointPath")
  }

}
