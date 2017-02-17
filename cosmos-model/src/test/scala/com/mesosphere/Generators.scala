package com.mesosphere

import com.netaporter.uri.PathPart
import java.nio.ByteBuffer
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen

object Generators {

  val genSemVer: Gen[universe.v3.model.SemVer] = {
    val maxNumber = 999999999L
    val maxStringLength = 10
    val genNumbers = Gen.chooseNum(0, maxNumber)
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

  val genPackageName: Gen[String] = {
    val maxPackageNameLength = 64
    Generators.maxSizedString(maxPackageNameLength, Gen.oneOf(Gen.numChar, Gen.alphaLowerChar))
  }

  val genVersion: Gen[universe.v3.model.PackageDefinition.Version] = {
    genSemVer.map(_.toString).map(universe.v3.model.PackageDefinition.Version)
  }

  val genReleaseVersion: Gen[universe.v3.model.PackageDefinition.ReleaseVersion] = for {
    num <- Gen.posNum[Long]
  } yield universe.v3.model.PackageDefinition.ReleaseVersion(num).get

  val genV3Package: Gen[universe.v3.model.V3Package] = for {
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

  val genV2Package: Gen[universe.v3.model.V2Package] = for {
    v3package <- genV3Package
    template <- genByteBuffer
  } yield universe.v3.model.V2Package(
    name=v3package.name,
    version=v3package.version,
    releaseVersion=v3package.releaseVersion,
    maintainer=v3package.maintainer,
    description=v3package.description,
    marathon=universe.v3.model.Marathon(template)
  )

  val genPackageDefinition: Gen[universe.v3.model.PackageDefinition] = {
    Gen.oneOf(genV3Package, genV2Package)
  }

  def maxSizedString(maxSize: Int, genChar: Gen[Char]): Gen[String] = for {
    size <- Gen.chooseNum(0, maxSize)
    array <- Gen.containerOfN[Array, Char](size, genChar)
  } yield new String(array)

  def nonEmptyMaxSizedString(maxSize: Int, genChar: Gen[Char]): Gen[String] = for {
    size <- Gen.chooseNum(1, maxSize)
    array <- Gen.containerOfN[Array, Char](size, genChar)
  } yield new String(array)

  def genNonEmptyString(genChar: Gen[Char]): Gen[String] = {
    Gen.nonEmptyContainerOf[Array, Char](genChar).map(new String(_))
  }

  val genTag: Gen[universe.v3.model.PackageDefinition.Tag] = {
    val genTagChar = arbitrary[Char].suchThat(!_.isWhitespace)
    val genTagString = genNonEmptyString(genTagChar)
    genTagString.map(universe.v3.model.PackageDefinition.Tag(_).get)
  }

  val genPathPart: Gen[PathPart] = Gen.resultOf(PathPart.apply _)

  val genDcosReleaseVersionVersion: Gen[universe.v3.model.DcosReleaseVersion.Version] = {
    Gen.sized(Gen.chooseNum(0, _)).map(universe.v3.model.DcosReleaseVersion.Version(_))
  }

  val genDcosReleaseVersionSuffix: Gen[universe.v3.model.DcosReleaseVersion.Suffix] = {
    genNonEmptyString(Gen.alphaNumChar).map(universe.v3.model.DcosReleaseVersion.Suffix(_))
  }

  val genByteBuffer: Gen[ByteBuffer] = arbitrary[Array[Byte]].map(ByteBuffer.wrap)

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

  }

}
