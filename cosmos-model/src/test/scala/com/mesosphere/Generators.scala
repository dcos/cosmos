package com.mesosphere

import com.mesosphere.universe.v3.model.Marathon
import com.mesosphere.universe.v3.model.PackageDefinition
import com.mesosphere.universe.v3.model.SemVer
import com.mesosphere.universe.v3.model.V2Package
import com.mesosphere.universe.v3.model.V3Package
import java.nio.ByteBuffer
import org.scalacheck.Gen

object Generators {

  val genSemVer: Gen[SemVer] = {
    val maxNumber = 999999999L
    val maxStringLength = 10
    val genNumbers = Gen.chooseNum(0, maxNumber)
    val genPreReleases = for {
      seqSize <- Gen.chooseNum(0, 3)
      preReleases <- Gen.containerOfN[Seq, Either[String, Long]](
        seqSize,
        Gen.oneOf(
          genNumbers.map(Right(_)),
          Generators.maxSizedString(
            maxStringLength,
            Gen.alphaLowerChar
          ).filter(_.nonEmpty).map(Left(_))
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
    } yield SemVer(major, minor, patch, preReleases, build)
  }

  val genPackageName: Gen[String] = {
    val maxPackageNameLength = 64
    Generators.maxSizedString(maxPackageNameLength, Gen.oneOf(Gen.numChar, Gen.alphaLowerChar))
  }

  val genVersion: Gen[PackageDefinition.Version] = for {
    semver <- genSemVer
  } yield PackageDefinition.Version(semver.toString)

  val genReleaseVersion: Gen[PackageDefinition.ReleaseVersion] = for {
    num <- Gen.posNum[Long]
  } yield PackageDefinition.ReleaseVersion(num).get

  val genV3Package: Gen[V3Package] = for {
    name <- genPackageName
    version <- genVersion
    releaseVersion <- genReleaseVersion
    maintainer <- Gen.alphaStr
    description <- Gen.alphaStr
  } yield V3Package(
    name=name,
    version=version,
    releaseVersion=releaseVersion,
    maintainer=maintainer,
    description=description
  )

  val genV2Package: Gen[V2Package] = for {
    v3package <- genV3Package
  } yield V2Package(
    name=v3package.name,
    version=v3package.version,
    releaseVersion=v3package.releaseVersion,
    maintainer=v3package.maintainer,
    description=v3package.description,
    marathon=Marathon(ByteBuffer.allocate(0))
  )

  val genPackageDefinition: Gen[PackageDefinition] = Gen.oneOf(genV3Package, genV2Package)

  def maxSizedString(maxSize: Int, genChar: Gen[Char]): Gen[String] = for {
    size <- Gen.chooseNum(0, maxSize)
    array <- Gen.containerOfN[Array, Char](size, genChar)
  } yield new String(array)

}
