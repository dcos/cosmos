package com.mesosphere.cosmos

import cats.data.Xor.Right
import com.mesosphere.cosmos.circe.Decoders._
import com.mesosphere.cosmos.circe.Encoders._
import com.mesosphere.cosmos.http.MediaTypes
import com.mesosphere.cosmos.model._
import com.mesosphere.cosmos.test.CosmosIntegrationTestClient.CosmosClient
import com.mesosphere.universe.{PackageDetailsVersion, ReleaseVersion, UniverseIndexEntry}
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

  val ArangodbPackageIndex = UniverseIndexEntry(
    name = "arangodb",
    currentVersion = PackageDetailsVersion("0.2.1"),
    versions = Map(PackageDetailsVersion("0.2.1") -> ReleaseVersion("0")),
    description = "A distributed free and open-source database with a flexible data model for documents, graphs, and key-values. " +
                  "Build high performance applications using a convenient SQL-like query language or JavaScript extensions.",
    framework = true,
    tags = List("arangodb", "NoSQL", "database", "framework")
  )

  val CassandraPackageIndex = UniverseIndexEntry(
    name = "cassandra",
    currentVersion = PackageDetailsVersion("0.2.0-1"),
    versions = Map(PackageDetailsVersion("0.2.0-1") -> ReleaseVersion("0")),
    description = "Apache Cassandra running on Apache Mesos",
    framework = true,
    tags = List("data", "database", "nosql")
  )

  val MemsqlPackageIndex = UniverseIndexEntry(
    name = "memsql",
    currentVersion = PackageDetailsVersion("0.0.1"),
    versions = Map(PackageDetailsVersion("0.0.1") -> ReleaseVersion("0")),
    description = "MemSQL running on Apache Mesos. This framework provides the ability to create and manage a set of MemSQL clusters, " +
                  "each running with the MemSQL Ops management tool.",
    framework = true,
    tags = List("mysql", "database", "rdbms")
  )

  private val PackageSearchTable = Table(
    ("query", "response"),
    ("aran", List(ArangodbPackageIndex)),
    ("cass", List(CassandraPackageIndex)),
    ("cassan", List(CassandraPackageIndex)),
    ("databas", List(ArangodbPackageIndex, CassandraPackageIndex))
  )

  private val PackageSearchRegexTable = Table(
    ("query", "response"),
    ("cassan*a", List(CassandraPackageIndex)),
    ("c*a", List(CassandraPackageIndex)),
    ("cass*", List(CassandraPackageIndex)),
    ("data*e", List(ArangodbPackageIndex, CassandraPackageIndex))
  )
}
