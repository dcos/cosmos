package com.mesosphere.cosmos

import com.twitter.app.{FlagParseException, FlagUsageError, Flags}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.finagle.http.RequestConfig.Yes
import io.circe.{Encoder, Json}
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

  logger.info("Connection to admin router located at: {}", dcosHost())
  /*TODO: Not crazy about this being here, possibly find a better place.*/
  protected[this] lazy val adminRouter: AdminRouter = new AdminRouter(dcosHost(), Services.adminRouterClient(dcosHost()))

  protected[this] val servicePort: Int = 8081

  protected[this] final def requestBuilder(endpointPath: String): RequestBuilder[Yes, Nothing] = {
    RequestBuilder().url(s"http://localhost:$servicePort/$endpointPath")
  }

  // This is an implicit conversion, but it only operates on values for which an `Encoder` instance
  // is defined, which should limit the contexts in which it can be (ab)used.
  protected[this] implicit def valueToJson[A](value: A)(implicit e: Encoder[A]): Json = e(value)

  protected[this] def typedAssertResult[A](expected: A)(actual: A): Unit = {
    assertResult(expected)(actual)
  }

}
