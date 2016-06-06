package com.mesosphere.cosmos.repository

import cats.data.Xor
import com.mesosphere.cosmos.{AdminRouter, CirceError, UnsupportedContentEncoding, UnsupportedContentType}
import com.mesosphere.cosmos.http.MediaTypeOps._
import com.mesosphere.cosmos.http.{MediaTypeParseError, MediaTypeParser, MediaTypes, RequestSession}
import com.mesosphere.cosmos.internal.model.CosmosInternalRepository
import com.mesosphere.universe.v3.circe.Decoders._
import com.mesosphere.universe.v3.model._
import com.netaporter.uri.Uri
import com.twitter.finagle.stats.{NullStatsReceiver, Stat, StatsReceiver}
import com.twitter.util.Future
import io.circe.parse._

import scala.io.Source
import java.io.InputStream
import java.util.Properties
import java.util.zip.GZIPInputStream

trait UniverseClient extends (Uri => Future[InputStream])


object UniverseClient {
  private[this] final class FnUniverseClient(function: Uri => Future[InputStream])
  extends UniverseClient {
    override def apply(uri: Uri) = function(uri)
  }

  private[this] val defaultUniverseClient = new FnUniverseClient(
    universeUri => Future(universeUri.toURI.toURL.openStream())
  )

  def apply(): UniverseClient = defaultUniverseClient
  def apply(function: Uri => Future[InputStream]): UniverseClient = new FnUniverseClient(function)
}

class UniverseRepositoryFetcher(adminRouter: AdminRouter)(implicit statsReceiver: StatsReceiver = NullStatsReceiver) {

  private[this] val stats = statsReceiver.scope("repositoryFetcher")

  private[this] val cosmosVersion = {
    val props = new Properties()
    val is = this.getClass.getResourceAsStream("/build.properties")
    if (is != null) {
      props.load(is)
      is.close()
    }
    Option(props.getProperty("cosmos.version")).getOrElse("unknown-version")
  }

  def apply(uri: Uri)(implicit session: RequestSession): Future[CosmosInternalRepository] = {
    adminRouter.getDcosVersion().flatMap { dcosVersion =>
      apply(uri, dcosVersion.version)
    }
  }

  private[repository] def apply(uri: Uri, dcosReleaseVersion: DcosReleaseVersion)(implicit session: RequestSession): Future[CosmosInternalRepository] = {
    Stat.timeFuture(stats.stat("fetch")) {
      Future { uri.toURI.toURL.openConnection() }
        .flatMap { conn =>
          // Set headers on request
          conn.setRequestProperty("Accept", MediaTypes.UniverseV3Repository.show)
          conn.setRequestProperty("Accept-Encoding", "gzip")
          conn.setRequestProperty("User-Agent", s"cosmos/$cosmosVersion dcos/${dcosReleaseVersion.show}")
          session.authorization.foreach { auth =>
            conn.setRequestProperty("Authorization", auth.headerValue)
          }

          // Handle the response
          Future {
            val contentType = Option(conn.getHeaderField("Content-Type")).getOrElse(
              throw UnsupportedContentType(
                List(MediaTypes.UniverseV3Repository, MediaTypes.applicationZip)
              )
            )
            val parsedContentType = MediaTypeParser.parse(contentType)
              .handle {
                case MediaTypeParseError(msg, cause) =>
                  throw UnsupportedContentType(
                    List(MediaTypes.UniverseV3Repository, MediaTypes.applicationZip)
                  )
              }
              .get
            val contentEncoding = Option(conn.getHeaderField("Content-Encoding"))
            (parsedContentType, contentEncoding)
          }
            .map { case (contentType, contentEncoding) =>
              val in = contentEncoding match {
                case Some("gzip") => new GZIPInputStream(conn.getInputStream)
                case ce @ Some(_) => throw UnsupportedContentEncoding(List("gzip"), ce)
                case _ => conn.getInputStream
              }

              val repoJsonString = {
                if (contentType.isCompatibleWith(MediaTypes.UniverseV3Repository)) {
                  Source.fromInputStream(in).mkString
                } else if (contentType.isCompatibleWith(MediaTypes.applicationZip)) {
                  ??? //TODO: v2 -> v3 conversion code is wired in here
                } else {
                  throw UnsupportedContentType.forMediaType(
                    List(MediaTypes.UniverseV3Repository, MediaTypes.applicationZip),
                    Some(contentType)
                  )
                }
              }

              decode[Repository](repoJsonString) match {
                case Xor.Left(err)   => throw CirceError(err)
                case Xor.Right(repo) => repo
              }
            }
        }
        .map { repo =>
          CosmosInternalRepository(
            repo.packages.map(asV3Package)
          )
        }
    }
  }

  private[this] def asV3Package(pkgDef: PackageDefinition): V3Package = {
    pkgDef match {
      case v3: V3Package => v3
      case V2Package(
        _, name, version, relVersion, maintainer, description, marathon, tags, selected,
        scm, website, framework, preInNotes, postInNotes, postUnNotes, licenses, resource, config, command
      ) => V3Package(V3PackagingVersion.instance, name, version, relVersion, maintainer, description, tags, selected,
        scm, website, framework, preInNotes, postInNotes, postUnNotes, licenses,
        marathon = Some(marathon), resource = resource, config = config, command = command
      )
    }
  }

}
