package com.mesosphere.cosmos

import cats.data.Xor.Right
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.rpc.v1.model.{SearchRequest, SearchResponse, SearchResult}
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe.v2.model.{Images, PackageDetailsVersion, ReleaseVersion}
import com.twitter.finagle.http._
import com.twitter.io.Buf
import io.circe.parse._
import io.circe.syntax._
import org.scalatest.FreeSpec
import org.scalatest.prop.TableDrivenPropertyChecks

final class PackageSearchSpec extends FreeSpec {

  import PackageSearchSpec._

  "The package search endpoint" - {
    "can successfully find packages" in {
      forAll (PackageSearchTable) { (query, expectedResponse) =>
        searchAndAssert(
          query=query,
          status=Status.Ok,
          expectedResponse=SearchResponse(expectedResponse)
        )
      }
    }

    "can successfully find packages by regex match" in {
      forAll (PackageSearchRegexTable) { (query, expectedResponse) =>
        searchAndAssert(
          query=query,
          status=Status.Ok,
          expectedResponse=SearchResponse(expectedResponse)
        )
      }
    }
  }

  private[cosmos] def searchAndAssert(
    query: String,
    status: Status,
    expectedResponse: SearchResponse
  ): Unit = {
    val request = CosmosClient.requestBuilder("package/search")
      .addHeader("Content-Type", MediaTypes.SearchRequest.show)
      .addHeader("Accept", MediaTypes.SearchResponse.show)
      .buildPost(Buf.Utf8(SearchRequest(Some(query)).asJson.noSpaces))
    val response = CosmosClient(request)
    assertResult(status)(response.status)
    val Right(actualResponse) = decode[SearchResponse](response.contentString)
    assertResult(expectedResponse)(actualResponse)
  }
}

private object PackageSearchSpec extends TableDrivenPropertyChecks {

  val ArangodbSearchResult = SearchResult(
    name = "arangodb",
    currentVersion = PackageDetailsVersion("0.3.0"),
    versions = Map(PackageDetailsVersion("0.2.1") -> ReleaseVersion("0"), PackageDetailsVersion("0.3.0") -> ReleaseVersion("1")),
    description = "A distributed free and open-source database with a flexible data model for documents, graphs, and key-values. " +
      "Build high performance applications using a convenient SQL-like query language or JavaScript extensions.",
    framework = true,
    tags = List("arangodb", "NoSQL", "database", "framework"),
    selected = Some(true),
    images = Some(Images(
      iconSmall = "https://raw.githubusercontent.com/arangodb/arangodb-dcos/master/icons/arangodb_small.png",
      iconMedium = "https://raw.githubusercontent.com/arangodb/arangodb-dcos/master/icons/arangodb_medium.png",
      iconLarge = "https://raw.githubusercontent.com/arangodb/arangodb-dcos/master/icons/arangodb_large.png",
      screenshots = None
    ))
  )

  val CassandraSearchResult = SearchResult(
    name = "cassandra",
    currentVersion = PackageDetailsVersion("0.2.0-2"),
    versions = Map(
      PackageDetailsVersion("0.2.0-1") -> ReleaseVersion("0"),
      PackageDetailsVersion("0.2.0-2") -> ReleaseVersion("1")
    ),
    description = "Apache Cassandra running on Apache Mesos",
    framework = true,
    tags = List("data", "database", "nosql"),
    selected = Some(true),
    images = Some(Images(
      iconSmall = "https://downloads.mesosphere.com/cassandra-mesos/assets/cassandra-small.png",
      iconMedium = "https://downloads.mesosphere.com/cassandra-mesos/assets/cassandra-medium.png",
      iconLarge = "https://downloads.mesosphere.com/cassandra-mesos/assets/cassandra-large.png",
      screenshots = None
    ))
  )

  val CrateSearchResult = SearchResult(
    name = "crate",
    currentVersion = PackageDetailsVersion("0.1.0"),
    versions = Map(PackageDetailsVersion("0.1.0") -> ReleaseVersion("0")),
    description = "A Mesos Framework that allows running and resizing one or multiple Crate database clusters.",
    framework = true,
    tags = List(
      "database",
      "distributed",
      "nosql"
    ),
    selected = Some(false),
    images = Some(Images(
      "https://cdn.crate.io/web/2.0/img/crate-mesos/crate-small.png",
      "https://cdn.crate.io/web/2.0/img/crate-mesos/crate-medium.png",
      "https://cdn.crate.io/web/2.0/img/crate-mesos/crate-large.png",
      None
    ))
  )

  val MemsqlSearchResult = SearchResult(
    name = "memsql",
    currentVersion = PackageDetailsVersion("0.0.1"),
    versions = Map(PackageDetailsVersion("0.0.1") -> ReleaseVersion("0")),
    description = "MemSQL running on Apache Mesos. This framework provides the ability to create and manage a set of MemSQL clusters, each running with the MemSQL Ops management tool.",
    framework = true,
    tags = List("mysql", "database", "rdbms"),
    selected = Some(false),
    images = None
  )

  val RiakSearchResult = SearchResult(
    name = "riak",
    currentVersion = PackageDetailsVersion("0.1.1"),
    versions = Map(PackageDetailsVersion("0.1.1") -> ReleaseVersion("0")),
    description = "A distributed NoSQL key-value data store that offers high availability, fault tolerance, operational simplicity, and scalability.",
    framework = true,
    tags = List(
      "mesosphere",
      "framework",
      "database",
      "riak"
    ),
    selected = Some(false),
    images = Some(Images(
      "http://riak-tools.s3.amazonaws.com/riak-mesos/riak-mesos-small.png",
      "http://riak-tools.s3.amazonaws.com/riak-mesos/riak-mesos-medium.png",
      "http://riak-tools.s3.amazonaws.com/riak-mesos/riak-mesos-large.png",
      Some(List(
        "http://riak-tools.s3.amazonaws.com/riak-mesos/riak-mesos-screenshot.png"
      ))
    ))
  )


  private val PackageSearchTable = Table(
    ("query", "response"),
    ("aran", List(ArangodbSearchResult)),
    ("cass", List(CassandraSearchResult)),
    ("cassan", List(CassandraSearchResult)),
    ("databas", List(ArangodbSearchResult, CassandraSearchResult, CrateSearchResult, MemsqlSearchResult, RiakSearchResult))
  )

  private val PackageSearchRegexTable = Table(
    ("query", "response"),
    ("cassan*a", List(CassandraSearchResult)),
    ("c*a", List(CassandraSearchResult)),
    ("cass*", List(CassandraSearchResult)),
    ("data*e", List(ArangodbSearchResult, CassandraSearchResult, CrateSearchResult, MemsqlSearchResult, RiakSearchResult))
  )
}
