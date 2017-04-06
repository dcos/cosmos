package com.mesosphere.universe.v3.model

import com.mesosphere.universe
import org.scalatest.prop.TableFor2

object PackagingVersionTestCompanion {

  val validPackagingVersions: TableFor2[universe.v3.model.PackagingVersion, String] = {
    new TableFor2(
      "PackagingVersion" -> "String",
      universe.v3.model.V2PackagingVersion -> "2.0",
      universe.v3.model.V3PackagingVersion -> "3.0"
    )
  }

  val versionStringList =  validPackagingVersions.map(_._2).mkString("[", ", ", "]")

  def renderInvalidVersionMessage(invalidVersion: String): String = {
    s"Expected one of $versionStringList for packaging version, but found [$invalidVersion]"
  }

}
