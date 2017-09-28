package com.mesosphere

import com.mesosphere.cosmos.http.MediaType
import com.netaporter.uri.Uri
import io.circe.testing.instances._
import java.nio.ByteBuffer
import java.util.UUID
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Gen.Choose
import org.scalacheck.Shapeless._
import org.scalacheck.derive.MkArbitrary
import scala.util.Success
import scala.util.Try

object Generators {

  import com.mesosphere.Generators.Implicits._

  val genPackageName: Gen[String] = {
    val maxPackageNameLength = 64
    val genPackageNameChar = Gen.oneOf(Gen.numChar, Gen.alphaLowerChar)
    maxSizedString(maxPackageNameLength, genPackageNameChar)
  }

  def nonNegNum[A](implicit C: Choose[A], N: Numeric[A]): Gen[A] = {
    Gen.sized(size => Gen.chooseNum(N.zero, N.fromInt(size)))
  }

  private val genSemVer: Gen[universe.v3.model.SemVer] = {
    val maxStringLength = 10
    val genNumbers = nonNegNum[Long]
    val genPreReleases = for {
      seqSize <- Gen.chooseNum(0, 3)
      preReleases <- Gen.containerOfN[Seq, Either[String, Long]](
        seqSize,
        Gen.oneOf(
          genNumbers.map(Right(_)),
          Generators.nonEmptyMaxSizedString(
            maxStringLength,
            Gen.alphaLowerChar
          ).map(Left(_))
        )
      )
    } yield preReleases

    val genBuild = Generators.maxSizedString(maxStringLength, Gen.alphaLowerChar).map {
      string => if (string.isEmpty) None else Some(string)
    }

    for {
      major <- genNumbers
      minor <- genNumbers
      patch <- genNumbers
      preReleases <- genPreReleases
      build <- genBuild
    } yield universe.v3.model.SemVer(major, minor, patch, preReleases, build)
  }

  val genVersion: Gen[universe.v3.model.Version] = {
    genSemVer.map(_.toString).map(universe.v3.model.Version(_))
  }

  val genVersionSpecification: Gen[universe.v3.model.VersionSpecification] = {
    val genExact = genVersion.map(universe.v3.model.ExactVersion)
    Gen.frequency((1, universe.v3.model.AnyVersion), (20, genExact))
  }

  private val genReleaseVersion: Gen[universe.v3.model.ReleaseVersion] = for {
    num <- Gen.posNum[Long]
  } yield universe.v3.model.ReleaseVersion(num)

  def genUpgradesFrom(
    requiredVersion: Option[universe.v3.model.Version]
  ): Gen[Option[List[universe.v3.model.VersionSpecification]]] = {
    requiredVersion match {
      case Some(required) =>
        for {
          leftVersions <- Gen.listOf(genVersionSpecification)
          rightVersions <- Gen.listOf(genVersionSpecification)
        } yield Some(leftVersions ++ (universe.v3.model.ExactVersion(required) :: rightVersions))
      case _ =>
        Gen.listOf(genVersionSpecification).flatMap {
          case vs if vs.isEmpty => Gen.oneOf(None, Some(Nil))
          case vs => Gen.const(Some(vs))
        }
    }
  }

  def genV3Package(genName: Gen[String] = genPackageName): Gen[universe.v3.model.V3Package] = {
    for {
      name <- genName
      version <- genVersion
      releaseVersion <- genReleaseVersion
      maintainer <- Gen.alphaStr
      description <- Gen.alphaStr
    } yield universe.v3.model.V3Package(
      name=name,
      version=version,
      releaseVersion=releaseVersion,
      maintainer=maintainer,
      description=description
    )
  }

  def genV2Package(genName: Gen[String] = genPackageName): Gen[universe.v3.model.V2Package] = {
    for {
      v3 <- genV3Package(genName)
      marathonTemplate <- genByteBuffer
    } yield {
      universe.v3.model.V2Package(
        name = v3.name,
        version = v3.version,
        releaseVersion = v3.releaseVersion,
        maintainer = v3.maintainer,
        description = v3.description,
        marathon = universe.v3.model.Marathon(marathonTemplate)
      )
    }
  }

  def genV4Package(
    genName: Gen[String] = genPackageName,
    genUpgrades: Gen[Option[List[universe.v3.model.VersionSpecification]]] = genUpgradesFrom(requiredVersion = None)
  ): Gen[universe.v4.model.V4Package] = {
    for {
      upgradesFrom <- genUpgrades
      downgradesTo <- Gen.option(Gen.listOf(genVersionSpecification))
      v3 <- genV3Package(genName)
    } yield {
      universe.v4.model.V4Package(
        name = v3.name,
        version = v3.version,
        releaseVersion = v3.releaseVersion,
        maintainer = v3.maintainer,
        description = v3.description,
        upgradesFrom = upgradesFrom,
        downgradesTo = downgradesTo
      )
    }
  }

  def genV3ResourceTestData() : Gen[(collection.immutable.Set[String],
    universe.v3.model.Assets,
    universe.v3.model.Images,
    universe.v3.model.Cli)] = {
    for {
      v2 <- genV2ResourceTestData()
      windowsCli <- arbitrary[Uri].map(_.toString)
      linuxCli <- arbitrary[Uri].map(_.toString)
      darwinCli <- arbitrary[Uri].map(_.toString)

    } yield {
      val cli = universe.v3.model.Cli(Some(universe.v3.model.Platforms(
        Some(universe.v3.model.Architectures(universe.v3.model.Binary("p", windowsCli, List.empty))),
        Some(universe.v3.model.Architectures(universe.v3.model.Binary("q", linuxCli, List.empty))),
        Some(universe.v3.model.Architectures(universe.v3.model.Binary("r", darwinCli, List.empty)))
      )))
      val expectedSet = v2._1 + windowsCli + linuxCli + darwinCli
      (expectedSet, v2._2, v2._3, cli)
    }
  }

  // scalastyle:off magic.number
  def genV2ResourceTestData() : Gen[(collection.immutable.Set[String],
    universe.v3.model.Assets,
    universe.v3.model.Images
    )] = {
    for {
      numberOfUrls <- Gen.chooseNum(0, 10)
      listOfUrls <- Gen.containerOfN[List, String](numberOfUrls, arbitrary[Uri].toString)
      iconSmall <- arbitrary[Uri].map(_.toString)
      iconMedium <- arbitrary[Uri].map(_.toString)
      iconLarge <- arbitrary[Uri].map(_.toString)
      screenshots <- Gen.containerOfN[List, String](numberOfUrls, arbitrary[Uri].toString)
    } yield {
      val assets = universe.v3.model.Assets(uris = Some(
        listOfUrls.zipWithIndex.map { case ((k, v)) =>
          (v.toString, k)
        }.toMap
      ),
        None
      )
      val expectedSet = iconSmall ::
        iconMedium ::
        iconLarge ::
        (listOfUrls ++ screenshots)
      val images = universe.v3.model.Images(Some(iconSmall), Some(iconMedium), Some(iconLarge), Some(screenshots))
      (expectedSet.toSet, assets, images)
    }
  }
  // scalastyle:on magic.number

  /* This is just here to tell you that you need to update the generator below, when you
   * add a new packaging version. This is a little hacky but worth the error
   */
  def checkPackageDefinitionExhaustiveness(
    pkgDef: universe.v4.model.PackageDefinition
  ): Gen[universe.v4.model.PackageDefinition] = {
    pkgDef match {
      case _: universe.v3.model.V2Package => ???
      case _: universe.v3.model.V3Package => ???
      case _: universe.v4.model.V4Package => ???
    }
  }

  def genPackageDefinition(
    genName: Gen[String] = genPackageName,
    genUpgrades: Gen[Option[List[universe.v3.model.VersionSpecification]]] = genUpgradesFrom(requiredVersion = None)
  ): Gen[universe.v4.model.PackageDefinition] = {
    Gen.oneOf(genV2Package(genName), genV3Package(genName), genV4Package(genName, genUpgrades))
  }

  /* This is just here to tell you that you need to update the generator below,
   * when you add a new packaging version. This is a little hacky but worth the error
   */
  def checkExhaustiveness(
    supportedPackage: universe.v4.model.SupportedPackageDefinition
  ): Gen[universe.v4.model.SupportedPackageDefinition] = {
    supportedPackage match {
      case _: universe.v3.model.V3Package => ???
      case _: universe.v4.model.V4Package => ???
    }
  }

  val genSupportedPackageDefinition: Gen[universe.v4.model.SupportedPackageDefinition] = {
    Gen.oneOf(genV4Package(), genV3Package())
  }

  private val genByteBuffer: Gen[ByteBuffer] = arbitrary[Array[Byte]].map(ByteBuffer.wrap)

  private def maxSizedString(maxSize: Int, genChar: Gen[Char]): Gen[String] = for {
    size <- Gen.chooseNum(0, maxSize)
    array <- Gen.containerOfN[Array, Char](size, genChar)
  } yield new String(array)

  private def nonEmptyMaxSizedString(maxSize: Int, genChar: Gen[Char]): Gen[String] = for {
    size <- Gen.chooseNum(1, maxSize)
    array <- Gen.containerOfN[Array, Char](size, genChar)
  } yield new String(array)

  private def genNonEmptyString(genChar: Gen[Char]): Gen[String] = {
    Gen.nonEmptyContainerOf[Array, Char](genChar).map(new String(_))
  }

  private val genTag: Gen[universe.v3.model.Tag] = {
    val genTagChar = arbitrary[Char].suchThat(!_.isWhitespace)
    val genTagString = genNonEmptyString(genTagChar)
    genTagString.map(universe.v3.model.Tag(_))
  }

  private val genUri: Gen[Uri] = {
    arbitrary[String]
      .map(s => Try(Uri.parse(s)))
      .flatMap {
        case Success(uri) => uri
        case _ => Gen.fail    // URI parsing almost always succeeds, so this should be fine
      }
  }

  private val genDcosReleaseVersionVersion: Gen[universe.v3.model.DcosReleaseVersion.Version] = {
    nonNegNum[Int].map(universe.v3.model.DcosReleaseVersion.Version(_))
  }

  private val genDcosReleaseVersionSuffix: Gen[universe.v3.model.DcosReleaseVersion.Suffix] = {
    genNonEmptyString(Gen.alphaNumChar).map(universe.v3.model.DcosReleaseVersion.Suffix(_))
  }

  // TODO package-add: Make this more general
  private val genMediaType: Gen[MediaType] = {
    val genTry = for {
      typePart <- Gen.alphaStr.map(_.toLowerCase)
      subTypePart <- Gen.alphaStr.map(_.toLowerCase)
    } yield Try(MediaType(typePart, subTypePart))

    genTry.flatMap {
      case Success(mediaType) => mediaType
      case _ => Gen.fail
    }
  }

  object Implicits {

    implicit val arbTag: Arbitrary[universe.v3.model.Tag] = Arbitrary(genTag)

    implicit val arbUri: Arbitrary[Uri] = Arbitrary(genUri)

    implicit val arbDcosReleaseVersionVersion:
      Arbitrary[universe.v3.model.DcosReleaseVersion.Version] = {
      Arbitrary(genDcosReleaseVersionVersion)
    }

    implicit val arbDcosReleaseVersionSuffix:
      Arbitrary[universe.v3.model.DcosReleaseVersion.Suffix] = {
      Arbitrary(genDcosReleaseVersionSuffix)
    }

    implicit val arbByteBuffer: Arbitrary[ByteBuffer] = Arbitrary(genByteBuffer)

    implicit val arbV3Package: Arbitrary[universe.v3.model.V3Package] = Arbitrary(genV3Package())

    implicit val arbPackageDefinition: Arbitrary[universe.v4.model.PackageDefinition] = {
      Arbitrary(genPackageDefinition())
    }

    implicit val arbSupportedPackageDefinition: Arbitrary[universe.v4.model.SupportedPackageDefinition] = {
      Arbitrary(genSupportedPackageDefinition)
    }

    implicit val arbUuid: Arbitrary[UUID] = Arbitrary(Gen.uuid)

    implicit val arbSemVer: Arbitrary[universe.v3.model.SemVer] = Arbitrary(genSemVer)

    implicit val arbMetadata: Arbitrary[universe.v4.model.Metadata] = derived

    implicit val arbVersion: Arbitrary[universe.v3.model.Version] = Arbitrary(genVersion)

    implicit val arbMediaType: Arbitrary[MediaType] = Arbitrary(genMediaType)

    def derived[A: MkArbitrary]: Arbitrary[A] = implicitly[MkArbitrary[A]].arbitrary

  }

}
