package com.mesosphere.cosmos.error

import cats.data.Ior
import com.mesosphere.cosmos.circe.Encoders._
import io.lemonlabs.uri.Uri
import io.circe.JsonObject
import io.circe.syntax._

final case class RepositoryNotPresent(nameOrUri: Ior[String, Uri]) extends CosmosError {
  override def data: Option[JsonObject] = {
    val jsonMap = nameOrUri match {
      case Ior.Both(n, u) => Map("name" -> n.asJson, "uri" -> u.asJson)
      case Ior.Left(n) => Map("name" -> n.asJson)
      case Ior.Right(u) => Map("uri" -> u.asJson)
    }
    Some(JsonObject.fromMap(jsonMap))
  }
  override def message: String = {
    nameOrUri match {
      case Ior.Both(n, u) => s"Neither repository name [$n] nor URI [$u] are present in the list"
      case Ior.Left(n) => s"Repository name [$n] is not present in the list"
      case Ior.Right(u) => s"Repository URI [$u] is not present in the list"
    }
  }
}
