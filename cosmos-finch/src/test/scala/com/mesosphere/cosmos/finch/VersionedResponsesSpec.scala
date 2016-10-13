package com.mesosphere.cosmos.finch

import cats.Eval
import com.mesosphere.cosmos.finch.TestingMediaTypes._
import com.mesosphere.cosmos.http.{MediaType, RequestSession}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Try}
import io.circe.Json
import io.circe.generic.semiauto
import io.circe.syntax._
import io.finch.{/, Endpoint, Input, Output, post}
import org.scalatest.FreeSpec

final class VersionedResponsesSpec extends FreeSpec {

  import VersionedResponsesSpec._

  "The Accept header determines the version of the response to send" - {

    "Foo version" in {
      val input = buildInput(Foo.encoder.mediaType, "\"42\"")
      val result = FoobarEndpoint(input)
      val jsonBody = extractBody(result)
      assertResult(Json.obj("whole" -> 42.asJson))(jsonBody)
    }

    "Bar version" in {
      val input = buildInput(Bar.encoder.mediaType, "\"3.14159\"")
      val result = FoobarEndpoint(input)
      val jsonBody = extractBody(result)
      assertResult(Json.obj("decimal" -> 3.14159.asJson))(jsonBody)
    }

  }

}

object VersionedResponsesSpec {

  final case class FoobarResponse(foo: Int, bar: Double)

  final case class Foo(whole: Int)
  final case class Bar(decimal: Double)

  object Foo {
    val encoder: MediaTypedEncoder[FoobarResponse] = MediaTypedEncoder(
      encoder = semiauto.deriveEncoder[Foo].contramap(foobar => Foo(foobar.foo)),
      mediaType = versionedJson(1)
    )
  }

  object Bar {
    val encoder: MediaTypedEncoder[FoobarResponse] = MediaTypedEncoder(
      encoder = semiauto.deriveEncoder[Bar].contramap(foobar => Bar(foobar.bar)),
      mediaType = versionedJson(2)
    )
  }

  def versionedJson(version: Int): MediaType = {
    applicationJson.copy(parameters = Map("version" -> s"v$version"))
  }

  val endpointPath: Seq[String] = Seq("package", "foobar")

  def buildInput(acceptHeader: MediaType, body: String): Input = {
    val request = RequestBuilder()
      .url(s"http://some.host/${endpointPath.mkString("/")}")
      .setHeader("Accept", acceptHeader.show)
      .setHeader("Content-Type", applicationJson.show)
      .buildPost(Buf.Utf8(body))

    Input(request)
  }

  def extractBody[A](result: Option[(Input, Eval[Future[Output[A]]])]): A = {
    val Some((_, eval)) = result
    Await.result(eval.value).value
  }

  implicit val stringRichDecoder: MediaTypedRequestDecoder[String] =
    MediaTypedRequestDecoder(MediaTypedDecoder(applicationJson))
  implicit val foobarResponseEncoder: DispatchingMediaTypedEncoder[FoobarResponse] =
    DispatchingMediaTypedEncoder(Set(Foo.encoder, Bar.encoder))

  object FoobarHandler extends EndpointHandler[String, FoobarResponse] {

    override def apply(request: String)(implicit session: RequestSession): Future[FoobarResponse] = {
      val asInt = Try(request.toInt).getOrElse(0)
      val asDouble = Try(request.toDouble).getOrElse(0.0)
      Future(FoobarResponse(asInt, asDouble))
    }

  }

  val FoobarEndpoint: Endpoint[Json] = FinchExtensions.route(post(/), FoobarHandler)(RequestValidators.standard)

}
