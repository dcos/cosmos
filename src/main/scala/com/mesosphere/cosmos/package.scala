package com.mesosphere.cosmos

import cats.data.Xor.{Left, Right}
import cats.data.{NonEmptyList, Xor}
import cats.std.list._
import com.twitter.finagle.http.Status
import com.twitter.util.Future
import io.circe.Json
import io.circe.syntax._
import io.finch.Output

object `package` extends FileUploadPatch {

  implicit final class FutureOps[A](val fut: Future[A]) extends AnyVal {

    def flatMapXor[B, C, D](f: C => Future[Xor[B, D]])(
      implicit ev: A => Xor[B, C]
    ): Future[Xor[B, D]] = {
      fut.flatMap { a =>
        ev(a).map(f) match {
          case Right(fd) => fd
          case Left(err) => Future.value(Left(err))
        }
      }
    }
  }

  type CosmosResult[A] = Xor[NonEmptyList[CosmosError], A]

  def errorNel(error: CosmosError): NonEmptyList[CosmosError] = NonEmptyList(error)

  def successOutput(message: String): Output[Json] = {
    Output.payload(Map("message" -> message).asJson)
  }

  def failureOutput(
    errors: NonEmptyList[CosmosError], status: Status = Status.BadRequest
  ): Output[Json] = {
    Output.payload(Map("errors" -> errors.unwrap).asJson, status)
  }

}
