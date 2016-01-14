package com.mesosphere.cosmos

import com.twitter.app.{FlagParseException, FlagUsageError, Flags}
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import io.finch.test.ServiceIntegrationSuite
import org.scalatest.fixture

abstract class IntegrationSpec
  extends fixture.FlatSpec
  with ServiceIntegrationSuite
  with CosmosSpec {

  private val failfastOnFlagsNotParsed = false
  private val name: String = getClass.getName.stripSuffix("$")
  private val flag: Flags = new Flags(name, includeGlobal = true, failfastOnFlagsNotParsed)

  def createService: Service[Request, Response] = {
    flag.parseArgs(args = Array(), allowUndefinedFlags = true) match {
      case Flags.Ok(remainder) =>
        // no-op
      case Flags.Help(usage) =>
        throw FlagUsageError(usage)
      case Flags.Error(reason) =>
        throw FlagParseException(reason)
    }

    val adminRouterUri = dcosHost()
    val dcosClient = Services.adminRouterClient(adminRouterUri)
    val adminRouter = new AdminRouter(adminRouterUri, dcosClient)
    new Cosmos(PackageCache.empty, new MarathonPackageRunner(adminRouter)).service
  }

  protected[this] final override val servicePort: Int = port

}
