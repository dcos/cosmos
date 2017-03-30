package com.mesosphere.cosmos

import cats.syntax.either._
import com.mesosphere.universe.v3.syntax.PackageDefinitionOps._
import java.util.regex.Pattern
import scala.util.matching.Regex

package object search {
  def searchForPackages(
    packages: List[rpc.v1.model.LocalPackage],
    query: Option[String]
  ): List[rpc.v1.model.LocalPackage] = {
    val predicate = query.map { value =>
      if (value.contains("*")) {
        searchRegex(createRegex(value))
      } else {
        searchString(value.toLowerCase())
      }
    } getOrElse { (_: rpc.v1.model.LocalPackage) =>
      true
    }

    packages.filter(predicate)
  }

  private[this] def searchRegex(
    regex: Regex
  ): rpc.v1.model.LocalPackage => Boolean = { pkg =>
    regex.findFirstIn(pkg.packageName).isDefined ||
    pkg.metadata.exists(
      metadata => regex.findFirstIn(metadata.description).isDefined
    ) ||
    pkg.metadata.exists(
      metadata => metadata.tags.exists(tag => regex.findFirstIn(tag.value).isDefined)
    )
  }

  private[this] def searchString(
    query: String
  ): rpc.v1.model.LocalPackage => Boolean = { pkg =>
    pkg.packageName.toLowerCase().contains(query) ||
    pkg.metadata.exists(
      metadata => metadata.description.toLowerCase().contains(query)
    ) ||
    pkg.metadata.exists(
      metadata => metadata.tags.exists(tag => tag.value.toLowerCase().contains(query))
    )
  }

  private[this] def safePattern(query: String): String = {
    query.split("\\*", -1).map{
      case "" => ""
      case v => Pattern.quote(v)
    }.mkString(".*")
  }

  private[this] def createRegex(query: String): Regex = {
    s"""^${safePattern(query)}$$""".r
  }
}
