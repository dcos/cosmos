package com.mesosphere.cosmos

import com.mesosphere.cosmos.rpc.v1.syntax._
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
    pkg.pkg.map(metadata => regex.findFirstIn(metadata.description).isDefined).getOrElse(false) ||
    pkg.pkg.map(
      metadata => metadata.tags.exists(tag => regex.findFirstIn(tag.value).isDefined)
    ).getOrElse(false)
  }

  private[this] def searchString(
    query: String
  ): rpc.v1.model.LocalPackage => Boolean = { pkg =>
    pkg.packageName.toLowerCase().contains(query) ||
    pkg.pkg.map(metadata => metadata.description.toLowerCase().contains(query)).getOrElse(false) ||
    pkg.pkg.map(
      metadata => metadata.tags.exists(tag => tag.value.toLowerCase().contains(query))
    ).getOrElse(false)
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
