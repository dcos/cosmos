package com.mesosphere.cosmos.model

import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

final class ZooKeeperUriSpec extends FreeSpec with TableDrivenPropertyChecks {

  import ZooKeeperUriSpec._

  "parse()" - {

    "invalid uris" in {
      forAll(InvalidUris) { stringToParse =>
        assert(ZooKeeperUri.parse(stringToParse).isLeft)
      }
    }

    "valid uris" in {
      forAll(ValidUris) { (stringToParse, connectString, path) =>
        val Right(zkUri) = ZooKeeperUri.parse(stringToParse)
        assertResult(connectString)(zkUri.connectString)
        assertResult(path)(zkUri.path)
      }
    }

  }

  "toString" - {
    "valid uris" in {
      forAll(ValidUris) { (stringToParse, _, _) =>
        val Right(uri) = ZooKeeperUri.parse(stringToParse)
        assertResult(stringToParse)(uri.toString)
      }
    }
  }

}

object ZooKeeperUriSpec extends TableDrivenPropertyChecks {

  val InvalidUris = Table(
    "string uri",
    "",
    "host1",
    "host1:2181",
    "/path/to/znode",
    "zk://",
    "zk://host1",
    "zk://host1/",
    "zk://host1:2181,host2:2181,host3:2181/invalid//path",
    "zk://host1:port1/valid/path"
  )

  val ValidUris = Table(
    ("string uri", "connection string", "path"),
    ("zk://host1/z", "host1", "/z"),
    ("zk://host1:2181/path", "host1:2181", "/path"),
    ("zk://foo:12,bar:34,baz:56/path/to/znode", "foo:12,bar:34,baz:56", "/path/to/znode")
  )

}
