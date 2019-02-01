package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.circe.Decoders.decode
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.rpc
import com.mesosphere.error.ResultOps
import com.mesosphere.universe
import com.mesosphere.universe.v3.model.Version
import com.mesosphere.universe.v4.model.PackageDefinition
import com.twitter.finagle.http.Fields
import io.lemonlabs.uri.Uri
import java.net.HttpURLConnection
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.io.Source

// scalastyle:off
object ResourceProxyHandlerExhaustiveTest {

  private lazy val logger = org.slf4j.LoggerFactory.getLogger(getClass)

  val packagesToInvestigate = new java.util.concurrent.ConcurrentHashMap[String, List[String]]()

  val useCustomPackages = false
  val customPackages = Map(
    ("kafka-zookeeper", List(200, 300)),
    ("minio", List(11, 12)),
    ("confluent-zookeeper", List(200)),
    ("couchbase", List(0)),
    ("traefik", List(3, 4)),
    ("grafana", List(6))
  )

  private def describe(dcosCmd : String, name: String, version: Version): PackageDefinition = {

    /**
     * Usual environments that can be tested:
     * - vpn (we can use tunnel-cli to connect to dc/os vpn)
     * - clusters with custom certs/proxies
     * - install CLI on the master (or any cluster node)
     */
    val response = executeCmd(s"$dcosCmd package describe $name --package-version=${version.toString}")
    decode[rpc.v3.model.DescribeResponse](response).getOrThrow.`package`
  }

  private def executeCmd(cmd: String): String = {
    //Assume this succeeds and return the stdout
    import scala.language.postfixOps
    import sys.process._
    logger.debug(s"[$cmd]")
    synchronized(cmd!!)
  }

  def printFailed(): Unit = {
    logger.info("=================================")
    if (packagesToInvestigate.size() > 0) {
      packagesToInvestigate.entrySet().forEach { x =>
        logger.info(x.getKey)
        logger.info(x.getValue.mkString ("\n - "))
      }
    }
  }

  /**
   * - The repo parameter can be changed to verify multiple versions of universe repo's
   *   against multiple repos of cosmos
   * - Based on given params, this class should be able to run against a cosmos running locally or remotely.
   */
  def main(args: Array[String]): Unit = {
    val dcosCmd = "/usr/local/bin/dcos"
    val customRepo = "https://downloads.mesosphere.com/universe/repo/repo-up-to-1.13.json"

    val pwd = executeCmd(s"$dcosCmd --version") // If this autoexits, update the dcosCmd path.
    logger.info(pwd)
    val auth = executeCmd(s"$dcosCmd config show core.dcos_acs_token")
    logger.info(s"loaded token $auth")
    val token = Authorization(s"token=${auth.trim}")
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
      printFailed()
    }})
    val packagesToIterate = fetchUniverseRepo(customRepo)
      .packages
      .filter(x => if (useCustomPackages) customPackages.contains(x.name) && customPackages(x.name).contains(x.releaseVersion.value) else true)
      .sortBy(id)
    val totalCount = packagesToIterate.length

    val pool = Executors.newFixedThreadPool(8)
    val remainingCounter = new AtomicInteger(totalCount)
    packagesToIterate.foreach { pkg =>
      pool.submit(new Runnable {
        override def run(): Unit = {
          val idx = id(pkg)
          logger.info(s"Processing [${remainingCounter.getAndDecrement()} / $idx] | FAIL:${packagesToInvestigate.size}")
          val describedPackage = describe(dcosCmd, pkg.name, pkg.version)
          val actualUrls = collectUrls(pkg)
          val proxiedUrls = collectUrls(describedPackage)
          val actualFailedUrls = verifyURLs(actualUrls, None)
          val proxyFailedUrls = verifyURLs(proxiedUrls, Some(token))
          if (actualFailedUrls.length < proxyFailedUrls.length) {
            logger.info(s"Actual and proxy differ for $idx : \n - $proxyFailedUrls \n - $actualFailedUrls")
            packagesToInvestigate.put(idx, proxyFailedUrls)
          }
          ()
        }
      })
    }
    pool.shutdown()
  }

  private def id(pkg : PackageDefinition) = s"${pkg.name}-${pkg.releaseVersion.value}-${pkg.version.toString}"

  private def fetchUniverseRepo(repo : String) : universe.v4.model.Repository = {
    val conn = Uri.parse(repo).toJavaURI.toURL.openConnection()
    decode[universe.v4.model.Repository](
      Source.fromInputStream(conn.getInputStream).mkString
    ).getOrThrow
  }

  private def collectUrls(pkg : PackageDefinition): List[String] = {
    val urls = new ListBuffer[String]()

    // Add all the asset urls
    pkg.assets.foreach(_.uris.foreach(x => urls.appendAll(x.values)))

    // Add all the images & screenshots
    pkg.images.foreach { i =>
      i.iconSmall.foreach(urls.append(_))
      i.iconMedium.foreach(urls.append(_))
      i.iconLarge.foreach(urls.append(_))
      i.screenshots.foreach(urls.appendAll(_))
    }

    // Add all CLI binaries
    pkg.cli
      .flatMap(_.binaries.map(x => List(x.windows, x.linux, x.darwin).flatMap(_.map(_.`x86-64`.url))))
      .foreach(urls.appendAll(_))
    urls.toList
  }

  private def verifyURLs(
    urls : List[String],
    authToken : Option[Authorization]
  ) : List[String] = {
    // Return the set of URLs that have failed.
    val failedUrls = new ListBuffer[String]()

    urls.foreach { u =>
      try {
        val conn = Uri.parse(u).toJavaURI.toURL.openConnection()

        authToken.foreach(x => {
          conn.setRequestProperty(Fields.Authorization, x.headerValue)
        })
        Some(conn).foreach { case c : HttpURLConnection =>
          val code = c.getResponseCode
          if (!(200 <= code && code <= 300)) {
            val error = Source.fromInputStream(c.getInputStream).mkString
            logger.info(error)
            failedUrls.append(s"$code - $u - $error")
          }
        }
      } catch {
        case exception: Exception =>
          logger.error(s"Failed to fetch URL $u", exception)
          failedUrls.append(s"$u - ${exception.getMessage}")
      }
    }
    failedUrls.toList
  }
}
