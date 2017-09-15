package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.model.OriginHostScheme
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Status
import com.twitter.util.Await
import com.twitter.util.Future
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax._
import io.finch._
import org.scalatest.Assertion
import org.scalatest.FreeSpec
import scala.util.Right

final class EndpointHandlerSpec extends FreeSpec {

  import EndpointHandlerSpec._

  "EndpointHandler.apply(EndpointContext) should" - {

    "pass EndpointContext.requestBody to EndpointHandler.apply(Request)" - {
      "so that the business logic of the EndpointHandler can use it" - {

        "with value of type Int" in {
          val expected = 42
          val context = buildEndpointContext[Int, Int](requestBody = expected)
          val result = (new IdentityHandler[Int])(context)
          val responseBody = extractBody[Int](result)
          assertResult(expected)(responseBody)
        }

        "with value of type String" in {
          val context = buildEndpointContext[String, String](requestBody = "hello world")
          val result = (new IdentityHandler[String])(context)
          val responseBody = extractBody[String](result)
          assertResult("hello world")(responseBody)
        }

      }
    }

    "pass EndpointContext.requestSession to EndpointHandler.apply(Request)" - {
      "so that the business logic of the EndpointHandler can use it" - {

        "with value Some" in {
          val result = callWithRequestSession(RequestSession(
            Some(Authorization("53cr37")),
            OriginHostScheme("localhost", "http"))
          )
          val responseBody = extractBody[Option[String]](result)
          assertResult(Some("53cr37"))(responseBody)
        }
        "with value None" in {
          val result = callWithRequestSession(RequestSession(None, OriginHostScheme("localhost", "http")))
          val responseBody = extractBody[Option[String]](result)
          assertResult(None)(responseBody)
        }

        def callWithRequestSession(session: RequestSession): Future[Output[Json]] = {
          val context = buildEndpointContext[Unit, Option[String]](requestBody = (), session = session)
          RequestSessionHandler(context)
        }

      }
    }

    "use EndpointContext.responseEncoder.encoder to render the response as JSON" - {
      "because all responses must be sent in JSON format" - {

        "response value of type Int" in {
          val expected = 42
          val underlying = Encoder.instance[Unit](_ => expected.asJson)
          val encoder = MediaTypedEncoder(underlying, TestingMediaTypes.any)
          val context = buildEndpointContext[Unit, Unit](requestBody = ())(encoder)
          val result = (new IdentityHandler[Unit])(context)
          val responseBody = extractBody[Int](result)
          assertResult(expected)(responseBody)
        }

        "response value of type String" in {
          val encoder = MediaTypedEncoder(Encoder.instance[Unit](_ => "hello world".asJson), TestingMediaTypes.any)
          val context = buildEndpointContext[Unit, Unit](requestBody = ())(encoder)
          val result = (new IdentityHandler[Unit])(context)
          val responseBody = extractBody[String](result)
          assertResult("hello world")(responseBody)
        }

      }
    }

    "use EndpointContext.responseEncoder.mediaType as the response's Content-Type header value" - {
      "because the encoder has generated a response having that Content-Type" - {

        "with value application/json" in {
          val encoder = MediaTypedEncoder(implicitly[Encoder[Unit]], TestingMediaTypes.applicationJson)
          val context = buildEndpointContext[Unit, Unit](requestBody = ())(encoder)
          val result = (new IdentityHandler[Unit])(context)
          val contentType = extractContentType(result)
          assertResult(TestingMediaTypes.applicationJson.show)(contentType)
        }

        "with value text/plain" in {
          val encoder = MediaTypedEncoder(implicitly[Encoder[Unit]], MediaType("text", "plain"))
          val context = buildEndpointContext[Unit, Unit](requestBody = ())(encoder)
          val result = (new IdentityHandler[Unit])(context)
          val contentType = extractContentType(result)
          assertResult(MediaType("text", "plain").show)(contentType)
        }

      }
    }

    "call EndpointHandler.apply(Request) to process the request and generate a response" - {
      "so that the business logic in the apply method is actually used" - {

        "to reverse the String request body" in {
          assertApply(_.reverse, "dlrow olleh")
        }

        "to uppercase the String request body" in {
          assertApply(_.toUpperCase, "HELLO WORLD")
        }

        def assertApply(fn: String => String, expected: String): Assertion = {
          val context = buildEndpointContext[String, String]("hello world")
          val handler = new EndpointHandler[String, String] {
            override def apply(request: String)(implicit session: RequestSession): Future[String] = {
              Future.value(fn(request))
            }
          }

          val result = handler(context)
          val responseBody = extractBody[String](result)
          assertResult(expected)(responseBody)
        }

      }
    }

    "respond with a 200 status code if the response was generated successfully" in {
      val context = buildEndpointContext[Unit, Unit](requestBody = ())
      val result = (new IdentityHandler[Unit])(context)
      val output = Await.result(result)
      assertResult(Status.Ok)(output.status)
    }

  }

}

object EndpointHandlerSpec {

  final class IdentityHandler[A] extends EndpointHandler[A, A] {
    override def apply(request: A)(implicit session: RequestSession): Future[A] = {
      Future.value(request)
    }
  }

  object RequestSessionHandler extends EndpointHandler[Unit, Option[String]] {
    override def apply(request: Unit)(implicit session: RequestSession): Future[Option[String]] = {
      Future.value(session.authorization.map(_.token))
    }
  }

  def buildEndpointContext[Req, Res](
    requestBody: Req,
    session: RequestSession = RequestSession(None, OriginHostScheme("localhost", "http"))
  )(implicit responseEncoder: MediaTypedEncoder[Res]): EndpointContext[Req, Res] = {
    EndpointContext(requestBody, session, responseEncoder)
  }

  def extractBody[A: Decoder](result: Future[Output[Json]]): A = {
    val output = Await.result(result)
    val Right(body) = output.value.as[A]
    body
  }

  def extractContentType(result: Future[Output[Json]]): String = {
    val output = Await.result(result)
    output.headers(Fields.ContentType)
  }

  implicit def anyMediaTypedEncoder[A](implicit encoder: Encoder[A]): MediaTypedEncoder[A] = {
    MediaTypedEncoder(encoder, TestingMediaTypes.any)
  }

}
