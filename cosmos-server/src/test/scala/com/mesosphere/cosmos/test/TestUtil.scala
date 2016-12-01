package com.mesosphere.cosmos.test

import com.mesosphere.cosmos._
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.storage.LocalObjectStorage
import com.mesosphere.cosmos.storage.ObjectStorage
import com.mesosphere.universe
import com.mesosphere.universe.test.TestingPackages
import com.mesosphere.universe.v3.model.PackageDefinition.ReleaseVersion
import com.mesosphere.universe.v3.model._
import com.twitter.bijection.Conversion.asMethod
import java.io.IOException
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

object TestUtil {

  def withObjectStorage(testCode: ObjectStorage => Unit): Unit = {
    implicit val stats = com.twitter.finagle.stats.NullStatsReceiver

    val path = Files.createTempDirectory("cosmos-dlpc-")
    try {
      testCode(LocalObjectStorage(path))
    } finally deleteRecursively(path)
  }

  def deleteRecursively(path: Path): Unit = {
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

    val _ = Files.walkFileTree(path, visitor)
  }

  implicit val Anonymous = RequestSession(None)

  val MinimalV2ModelDescribeResponse = rpc.v2.model.DescribeResponse(
    packagingVersion = TestingPackages.MinimalV3ModelV2PackageDefinition.packagingVersion,
    name = TestingPackages.MinimalV3ModelV2PackageDefinition.name,
    version = TestingPackages.MinimalV3ModelV2PackageDefinition.version,
    maintainer = TestingPackages.MinimalV3ModelV2PackageDefinition.maintainer,
    description = TestingPackages.MinimalV3ModelV2PackageDefinition.description
  )

  val MaximalV2ModelDescribeResponse = rpc.v2.model.DescribeResponse(
    packagingVersion = TestingPackages.MaximalV3ModelV3PackageDefinition.packagingVersion,
    name = TestingPackages.MaximalV3ModelV3PackageDefinition.name,
    version = TestingPackages.MaximalV3ModelV3PackageDefinition.version,
    maintainer = TestingPackages.MaximalV3ModelV3PackageDefinition.maintainer,
    description = TestingPackages.MaximalV3ModelV3PackageDefinition.description,
    tags = TestingPackages.MaximalV3ModelV3PackageDefinition.tags,
    selected = TestingPackages.MaximalV3ModelV3PackageDefinition.selected.getOrElse(false),
    scm = TestingPackages.MaximalV3ModelV3PackageDefinition.scm,
    website = TestingPackages.MaximalV3ModelV3PackageDefinition.website,
    framework = TestingPackages.MaximalV3ModelV3PackageDefinition.framework.getOrElse(false),
    preInstallNotes = TestingPackages.MaximalV3ModelV3PackageDefinition.preInstallNotes,
    postInstallNotes = TestingPackages.MaximalV3ModelV3PackageDefinition.postInstallNotes,
    postUninstallNotes = TestingPackages.MaximalV3ModelV3PackageDefinition.postUninstallNotes,
    licenses = TestingPackages.MaximalV3ModelV3PackageDefinition.licenses,
    minDcosReleaseVersion = TestingPackages.MaximalV3ModelV3PackageDefinition.minDcosReleaseVersion,
    marathon = TestingPackages.MaximalV3ModelV3PackageDefinition.marathon,
    resource = TestingPackages.MaximalV3ModelV3PackageDefinition.resource,
    config = TestingPackages.MaximalV3ModelV3PackageDefinition.config,
    command = TestingPackages.MaximalV3ModelV3PackageDefinition.command
  )

  val MaximalInstalledPackageInformation  = rpc.v1.model.InstalledPackageInformation(
    packageDefinition = rpc.v1.model.InstalledPackageInformationPackageDetails(
      packagingVersion = universe.v2.model.PackagingVersion("3.0"),
      name = TestingPackages.MaximalV2ModelPackageDetails.name,
      version = TestingPackages.MaximalV2ModelPackageDetails.version,
      maintainer = TestingPackages.MaximalV2ModelPackageDetails.maintainer,
      description = TestingPackages.MaximalV2ModelPackageDetails.description,
      tags = TestingPackages.MaximalV2ModelPackageDetails.tags,
      selected = TestingPackages.MaximalV2ModelPackageDetails.selected,
      scm = TestingPackages.MaximalV2ModelPackageDetails.scm,
      website = TestingPackages.MaximalV2ModelPackageDetails.website,
      framework = TestingPackages.MaximalV2ModelPackageDetails.framework,
      preInstallNotes = TestingPackages.MaximalV2ModelPackageDetails.preInstallNotes,
      postInstallNotes = TestingPackages.MaximalV2ModelPackageDetails.postInstallNotes,
      postUninstallNotes = TestingPackages.MaximalV2ModelPackageDetails.postUninstallNotes,
      licenses = TestingPackages.MaximalV2ModelPackageDetails.licenses
    ),
    resourceDefinition = Some(TestingPackages.MaximalV2Resource)
  )

  def nameAndRelease(pkg: PackageDefinition): (String, ReleaseVersion) = pkg match {
    case v2: V2Package => (v2.name, v2.releaseVersion)
    case v3: V3Package => (v3.name, v3.releaseVersion)
  }
}
