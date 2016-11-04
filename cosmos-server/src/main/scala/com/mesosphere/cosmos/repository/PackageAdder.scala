package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.cosmos.storage.PackageObjectStorage
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Future

final class PackageAdder private (
  tempObjectStorage: ObjectStorage,
  packageObjectStorage: PackageObjectStorage
) {

  def apply(
    uri: Uri,
    pkg: universe.v3.model.PackageDefinition
  ): Future[Unit] = {
    /* NOTE: We could just grab the PackageDefinition from the parameter but we
     * should start exercising the need to read zipfiles from the object storage
     */
    // TODO: We need to check that the package is not already installed.
    // Put the PackageDefinition in the package object storage.
    packageObjectStorage.writePackageDefinition(pkg)
  }
}

object PackageAdder {
  def apply(
    tempObjectStorage: ObjectStorage,
    packageObjectStorage: PackageObjectStorage
  ): PackageAdder = new PackageAdder(tempObjectStorage, packageObjectStorage)

  import com.mesosphere.cosmos.circe.Decoders.decode
  import com.mesosphere.cosmos.storage.LocalObjectStorage
  import com.netaporter.uri.dsl._
  import com.twitter.bijection.Conversion.asMethod
  import com.twitter.io.StreamIO
  import com.twitter.util.Await
  import java.io.InputStream
  import java.nio.file.Files
  import java.nio.file.Paths
  import java.nio.file.StandardOpenOption
  import java.util.zip.ZipInputStream

  def test(): Unit = {
    implicit val stats = com.twitter.finagle.stats.NullStatsReceiver
    val tempStorage = LocalObjectStorage(Paths.get("/tmp/cosmos/tempObjectStorage"))

    val adder = PackageAdder(
      tempStorage,
      PackageObjectStorage(LocalObjectStorage(Paths.get("/tmp/cosmos/packageObjectStorage")))
    )

    val pkgPath = Paths.get("/home/jose/work/cosmos/some-package.dcos")

    Await.result {
      adder(
        "http://ignored",
        readPackageDefinition(Files.newInputStream(pkgPath, StandardOpenOption.READ))
      )
    }
  }

  private[this] def readPackageDefinition(
    inputStream: InputStream
  ): universe.v3.model.V3Package = {
    val zipStream = new ZipInputStream(inputStream)
    val metadata = Iterator.continually {
      // Note: this closure is not technically pure. The variable zipStream is mutated here.
      Option(zipStream.getNextEntry())
    }.takeWhile(_.isDefined)
      .flatten
      .filter(_.getName == "metadata.json")
      .map { _ =>
        // Note: this closure is not technically pure. The variable zipStream is muted here.
        decode[universe.v3.model.Metadata](
          new String(StreamIO.buffer(zipStream).toByteArray)
        ).as[universe.v3.model.V3Package]
      }

    metadata.next()
  }
}
