package com.mesosphere.cosmos

import java.io.IOException
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

import com.netaporter.uri.Uri
import com.netaporter.uri.dsl._
import com.twitter.app.{FlagParseException, FlagUsageError, Flags}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.finagle.http.RequestConfig.Yes
import org.scalatest.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.slf4j.{Logger, LoggerFactory}

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
  protected[this] val universeUri: Uri =
    Uri.parse("https://github.com/mesosphere/universe/archive/cli-test-4.zip")

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

  protected[this] final def withTempDirectory(f: Path => Unit): Unit = {
    val tempDir = Files.createTempDirectory("cosmos")
    try { f(tempDir) } finally {
      val visitor = new SimpleFileVisitor[Path] {

        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          Files.delete(file)
          FileVisitResult.CONTINUE
        }

        override def postVisitDirectory(dir: Path, e: IOException): FileVisitResult = {
          Option(e) match {
            case Some(failure) => throw failure
            case _ =>
              Files.delete(dir)
              FileVisitResult.CONTINUE
          }
        }

      }

      val _ = Files.walkFileTree(tempDir, visitor)
    }
  }

}
