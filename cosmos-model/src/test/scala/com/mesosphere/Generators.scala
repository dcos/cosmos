package com.mesosphere

import com.netaporter.uri.PathPart
import java.nio.ByteBuffer
import java.util.UUID
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.Gen.Choose

object Generators {

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

  private val genVersion: Gen[universe.v3.model.PackageDefinition.Version] = {
    genSemVer.map(_.toString).map(universe.v3.model.PackageDefinition.Version)
  }

  private val genReleaseVersion: Gen[universe.v3.model.PackageDefinition.ReleaseVersion] = for {
    num <- Gen.posNum[Long]
  } yield universe.v3.model.PackageDefinition.ReleaseVersion(num).get

  val genV3Package: Gen[universe.v3.model.V3Package] = {
    val maxPackageNameLength = 64
    val genPackageNameChar = Gen.oneOf(Gen.numChar, Gen.alphaLowerChar)
    val genPackageName = maxSizedString(maxPackageNameLength, genPackageNameChar)

    for {
      name <- genPackageName
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

  private val genTag: Gen[universe.v3.model.PackageDefinition.Tag] = {
    val genTagChar = arbitrary[Char].suchThat(!_.isWhitespace)
    val genTagString = genNonEmptyString(genTagChar)
    genTagString.map(universe.v3.model.PackageDefinition.Tag(_).get)
  }

  private val genPathPart: Gen[PathPart] = Gen.resultOf(PathPart.apply _)

  private val genDcosReleaseVersionVersion: Gen[universe.v3.model.DcosReleaseVersion.Version] = {
    nonNegNum[Int].map(universe.v3.model.DcosReleaseVersion.Version(_))
  }

  private val genDcosReleaseVersionSuffix: Gen[universe.v3.model.DcosReleaseVersion.Suffix] = {
    genNonEmptyString(Gen.alphaNumChar).map(universe.v3.model.DcosReleaseVersion.Suffix(_))
  }

  object Implicits {

    implicit val arbTag: Arbitrary[universe.v3.model.PackageDefinition.Tag] = Arbitrary(genTag)

    implicit val arbPathPart: Arbitrary[PathPart] = Arbitrary(genPathPart)

    implicit val arbDcosReleaseVersionVersion:
      Arbitrary[universe.v3.model.DcosReleaseVersion.Version] = {
      Arbitrary(genDcosReleaseVersionVersion)
    }

    implicit val arbDcosReleaseVersionSuffix:
      Arbitrary[universe.v3.model.DcosReleaseVersion.Suffix] = {
      Arbitrary(genDcosReleaseVersionSuffix)
    }

    implicit val arbByteBuffer: Arbitrary[ByteBuffer] = Arbitrary(genByteBuffer)

    implicit val arbV3Package: Arbitrary[universe.v3.model.V3Package] = Arbitrary(genV3Package)

    implicit val arbUuid: Arbitrary[UUID] = Arbitrary(Gen.uuid)

    implicit val arbSemVer: Arbitrary[universe.v3.model.SemVer] = Arbitrary(genSemVer)

  }

}
