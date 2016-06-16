package com.mesosphere.cosmos

import java.io.{OutputStream, PrintStream}
import java.net.URL
import java.util.Properties
import javassist.CannotCompileException

import org.apache.curator.test.TestingCluster
import sbt._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, TimeoutException}
import scala.util.Random

class CosmosIntegrationTestServer(javaHome: Option[String], itResourceDirs: Seq[File], oneJarPath: File) {
  private val originalProperties: Properties = System.getProperties
  private var process: Option[Process] = None
  private var zkCluster: Option[TestingCluster] = None

  def setup(logger: Logger): Unit = {
    try {
      initCuratorTestJavassist()
      val cluster = new TestingCluster(1)
      cluster.start()
      zkCluster = Some(cluster)
    } catch {
      case cce: CannotCompileException =>
        // ignore, this appears to be thrown by some runtime bytcode stuff that doesn't actually seem to break things
      case t: Throwable =>
        logger.info(s"caught throwable: ${t.toString}")
    }

    val zkUri = zkCluster.map { c =>
      val connectString = c.getConnectString
      val baseZNode = Random.alphanumeric.take(10).mkString
      s"zk://$connectString/$baseZNode"
    }.get

    val java = javaHome
      .map(_ + "/bin/java")
      .orElse(systemProperty("java.home").map(jre => s"$jre/bin/java"))
      .getOrElse("java")

    val dcosUri = systemProperty("com.mesosphere.cosmos.dcosUri").get
    System.setProperty(
      "com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient.uri",
      "http://localhost:7070"
    )

    val pathSeparator = System.getProperty("path.separator")
    val classpath =
      s"${itResourceDirs.map(_.getCanonicalPath).mkString("", pathSeparator, pathSeparator)}" +
        s"${oneJarPath.getCanonicalPath}"

    val cmd = Seq(
      java,
      "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005",
      "-classpath",
      classpath,
      "com.simontuffs.onejar.Boot",
      s"-com.mesosphere.cosmos.zookeeperUri=$zkUri",
      s"-com.mesosphere.cosmos.dcosUri=$dcosUri"
    )

    logger.info("Starting cosmos with command: " + cmd.mkString(" "))

    val run = Process(cmd).run(new ProcessLogger() {
      override def buffer[T](f: => T): T = logger.buffer(f)
      override def error(s: => String): Unit = logger.info("<<cosmos-server>> " + s)
      override def info(s: => String): Unit = logger.info("<<cosmos-server>> " + s)
    })
    val fExitValue = Future(run.exitValue())
    process = Some(run)
    try {
      waitUntilTrue(60.seconds) {
        if (fExitValue.isCompleted) {
          throw new IllegalStateException("Cosmos Server has terminated.")
        }
        canConnectTo(logger, new URL("http://localhost:9990/admin/ping"))
      }
    } catch {
      case t: Throwable =>
        cleanup()
        throw t
    }
  }

  def cleanup(): Unit = {
    System.setProperties(originalProperties)
    process.foreach(_.destroy())
    zkCluster.foreach(_.close())
  }

  private[this] def systemProperty(key: String): Option[String] = {
    Option(System.getProperty(key))
  }

  /**
    * True if a connection could be made to the given URL
    */
  private[this] def canConnectTo(logger: Logger, url: URL): Boolean = {
    try {
      logger.info("waiting for url: " + url)
      url.openConnection()
        .getInputStream
        .close()
      true
    } catch {
      case _: Exception => false
    }
  }

  /**
    * Polls the given action until it returns true, or throws a TimeoutException
    * if it does not do so within 'timeout'
    */
  private[this] def waitUntilTrue(timeout: Duration)(action: => Boolean): Unit = {
    val startTimeMillis = System.currentTimeMillis()
    while (!action) {
      if ((System.currentTimeMillis() - startTimeMillis).millis > timeout) {
        throw new TimeoutException()
      }
      Thread.sleep(1000)
    }
  }

  /**
    * This is being done to work around the fact that ByteCodeRewrite has a static {} block in it's class
    * that logs an exception to stdout via e.printStackTrace
    */
  private[this] def initCuratorTestJavassist(): Unit = {
    val origStdOut = System.out
    val origStdErr = System.err
    //noinspection ConvertExpressionToSAM
    val devNull = new PrintStream(new OutputStream {
      override def write(b: Int): Unit = { /*do Nothing*/ }
    })
    System.setOut(devNull)
    System.setErr(devNull)
    org.apache.curator.test.ByteCodeRewrite.apply()
    System.setOut(origStdOut)
    System.setErr(origStdErr)
  }
}
