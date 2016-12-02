package com.mesosphere.cosmos

import _root_.io.circe.jawn._
import cats.data.Xor.Right
import com.mesosphere.cosmos.http.CosmosRequests
import com.mesosphere.cosmos.rpc.v1.circe.Decoders._
import com.mesosphere.cosmos.rpc.v1.model.SearchRequest
import com.mesosphere.cosmos.rpc.v1.model.SearchResponse
import com.mesosphere.cosmos.rpc.v1.model.SearchResult
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe
import com.twitter.finagle.http._
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

final class PackageSearchSpec extends FreeSpec {

  import PackageSearchSpec._

  "The package search endpoint can successfully find packages" - {
    "by term" in {
      forAll (PackageSearchTable) { (query, expectedResponse) =>
        searchAndAssert(
          query=query,
          status=Status.Ok,
          expectedResponse=expectedResponse
        )
      }
    }

    "by regex match" in {
      forAll (PackageSearchRegexTable) { (query, expectedResponse) =>
        searchAndAssert(
          query=query,
          status=Status.Ok,
          expectedResponse=expectedResponse
        )
      }
    }
  }

  private[cosmos] def searchAndAssert(
    query: String,
    status: Status,
    expectedResponse: SearchResponse
  ): Unit = {
    val request = CosmosRequests.packageSearch(SearchRequest(Some(query)))
    val response = CosmosClient.submit(request)

    assertResult(status)(response.status)
    val Right(actualResponse) = decode[SearchResponse](response.contentString)
    assertResult(expectedResponse)(actualResponse)
  }
}

private object PackageSearchSpec extends TableDrivenPropertyChecks {

  val ArangodbSearchResult = SearchResult(
    name = "arangodb",
    currentVersion = universe.v3.model.PackageDefinition.Version("0.3.0"),
    versions = Map(
      universe.v3.model.PackageDefinition.Version("0.2.1") -> universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
      universe.v3.model.PackageDefinition.Version("0.3.0") -> universe.v3.model.PackageDefinition.ReleaseVersion(1).get),
    description = "A distributed free and open-source database with a flexible data model for documents, graphs, and key-values. " +
      "Build high performance applications using a convenient SQL-like query language or JavaScript extensions.",
    framework = true,
    tags = List("arangodb", "NoSQL", "database")
      .map(universe.v3.model.PackageDefinition.Tag(_).get),
    selected = Some(true),
    images = Some(universe.v3.model.Images(
      iconSmall = Some("https://raw.githubusercontent.com/arangodb/arangodb-dcos/master/icons/arangodb_small.png"),
      iconMedium = Some("https://raw.githubusercontent.com/arangodb/arangodb-dcos/master/icons/arangodb_medium.png"),
      iconLarge = Some("https://raw.githubusercontent.com/arangodb/arangodb-dcos/master/icons/arangodb_large.png"),
      screenshots = None
    ))
  )

  val CassandraSearchResult = SearchResult(
    name = "cassandra",
    currentVersion = universe.v3.model.PackageDefinition.Version("1.0.6-2.2.5"),
    versions = Map(
      // scalastyle:off magic.number
      universe.v3.model.PackageDefinition.Version("0.2.0-1") -> universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
      universe.v3.model.PackageDefinition.Version("0.2.0-2") -> universe.v3.model.PackageDefinition.ReleaseVersion(1).get,
      universe.v3.model.PackageDefinition.Version("1.0.5-2.2.5") -> universe.v3.model.PackageDefinition.ReleaseVersion(7).get,
      universe.v3.model.PackageDefinition.Version("1.0.2-2.2.5") -> universe.v3.model.PackageDefinition.ReleaseVersion(4).get,
      universe.v3.model.PackageDefinition.Version("2.2.5-0.2.0") -> universe.v3.model.PackageDefinition.ReleaseVersion(3).get,
      universe.v3.model.PackageDefinition.Version("1.0.6-2.2.5") -> universe.v3.model.PackageDefinition.ReleaseVersion(8).get,
      universe.v3.model.PackageDefinition.Version("1.0.4-2.2.5") -> universe.v3.model.PackageDefinition.ReleaseVersion(5).get
      // scalastyle:on magic.number
    ),
    description = "Apache Cassandra running on DC/OS",
    framework = true,
    tags = List("data", "database", "nosql").map(universe.v3.model.PackageDefinition.Tag(_).get),
    selected = Some(true),
    images = Some(universe.v3.model.Images(
      iconSmall = Some("https://downloads.mesosphere.com/cassandra-mesos/assets/cassandra-small.png"),
      iconMedium = Some("https://downloads.mesosphere.com/cassandra-mesos/assets/cassandra-medium.png"),
      iconLarge = Some("https://downloads.mesosphere.com/cassandra-mesos/assets/cassandra-large.png"),
      screenshots = None
    ))
  )

  val CrateSearchResult = SearchResult(
    name = "crate",
    currentVersion = universe.v3.model.PackageDefinition.Version("0.1.0"),
    versions = Map(universe.v3.model.PackageDefinition.Version("0.1.0") -> universe.v3.model.PackageDefinition.ReleaseVersion(0).get),
    description = "A Mesos Framework that allows running and resizing one or multiple Crate database clusters.",
    framework = true,
    tags = List(
      "database",
      "nosql"
    ).map(universe.v3.model.PackageDefinition.Tag(_).get),
    selected = None,
    images = Some(universe.v3.model.Images(
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-crate-small.png"),
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-crate-medium.png"),
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-crate-large.png"),
      None
    ))
  )

  val MemsqlSearchResult = SearchResult(
    name = "memsql",
    currentVersion = universe.v3.model.PackageDefinition.Version("0.0.1"),
    versions = Map(universe.v3.model.PackageDefinition.Version("0.0.1") -> universe.v3.model.PackageDefinition.ReleaseVersion(0).get),
    description =
      "MemSQL running on Apache Mesos. This framework provides the ability to create and manage " +
        "a set of MemSQL clusters, each running with the MemSQL Ops management tool.",
    framework = true,
    tags = List("mysql", "database", "rdbms").map(universe.v3.model.PackageDefinition.Tag(_).get),
    selected = None,
    images = Some(universe.v3.model.Images(
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-memsql-small.png"),
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-memsql-medium.png"),
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-memsql-large.png"),
      None
    ))
  )

  val MysqlSearchResult = SearchResult(
    name = "mysql",
    currentVersion = universe.v3.model.PackageDefinition.Version("5.7.12"),
    versions = Map(universe.v3.model.PackageDefinition.Version("5.7.12") -> universe.v3.model.PackageDefinition.ReleaseVersion(1).get),
    description =
      "MySQL is the world's most popular open source database. With its proven performance, " +
        "reliability and ease-of-use, MySQL has become the leading database choice for " +
        "web-based applications, covering the entire range from personal projects and websites, " +
        "via e-commerce and information services, all the way to high profile web properties " +
        "including Facebook, Twitter, YouTube, Yahoo! and many more.",
    framework = false,
    tags = List("database", "mysql", "sql").map(universe.v3.model.PackageDefinition.Tag(_).get),
    selected = None,
    images = None
  )

  val RiakSearchResult = SearchResult(
    name = "riak",
    currentVersion = universe.v3.model.PackageDefinition.Version("0.1.1"),
    versions = Map(universe.v3.model.PackageDefinition.Version("0.1.1") -> universe.v3.model.PackageDefinition.ReleaseVersion(0).get),
    description = "A distributed NoSQL key-value data store that offers high availability, fault tolerance, operational simplicity, and scalability.",
    framework = true,
    tags = List(
      "database",
      "riak",
      "NoSql"
    ).map(universe.v3.model.PackageDefinition.Tag(_).get),
    selected = None,
    images = Some(universe.v3.model.Images(
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-riak-small.png"),
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-riak-medium.png"),
      Some("https://downloads.mesosphere.com/universe/assets/icon-service-riak-large.png"),
      Some(List(
        "http://riak-tools.s3.amazonaws.com/riak-mesos/riak-mesos-screenshot.png"
      ))
    ))
  )


  private val PackageSearchTable = Table(
    ("query", "response"),
    ("aran",    SearchResponse(List(ArangodbSearchResult))),
    ("cass",    SearchResponse(List(CassandraSearchResult))),
    ("cassan",  SearchResponse(List(CassandraSearchResult))),
    ("databas", SearchResponse(List(ArangodbSearchResult, CassandraSearchResult, CrateSearchResult, MemsqlSearchResult, MysqlSearchResult, RiakSearchResult)))
  )

  private val PackageSearchRegexTable = Table(
    ("query", "response"),
    ("cassan*a",  SearchResponse(List(CassandraSearchResult))),
    ("c*a",       SearchResponse(List(CassandraSearchResult))),
    ("cass*",     SearchResponse(List(CassandraSearchResult))),
    ("data*e",    SearchResponse(List(ArangodbSearchResult, CassandraSearchResult, CrateSearchResult, MemsqlSearchResult, MysqlSearchResult, RiakSearchResult)))
  )
}
