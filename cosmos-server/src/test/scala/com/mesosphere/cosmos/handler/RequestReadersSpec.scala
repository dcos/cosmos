package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.http.{Authorization, MediaType, MediaTypes, RequestSession}
import com.twitter.finagle.http.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Return, Try}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.finch.{Endpoint, Input, Output}
import org.scalatest.FreeSpec
import cats.Eval
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder

final class RequestReadersSpec extends FreeSpec {

  import RequestReadersSpec._

  "The RequestReader built by RequestReaders.noBody should" - {

    behave like baseReader(factory = NoBodyReaderFactory)

  }

  "The RequestReader built by RequestReaders.standard should" - {

    behave like baseReader(factory = StandardReaderFactory)

  }

  def baseReader[Req](factory: RequestReaderFactory[Req]): Unit = {

    "include the Authorization header in the return value if it was included in the request" - {
      "to accurately forward the header's state to other services" in {
        val Return((requestSession, _)) = runReader(authorization = Some("53cr37"))
        assertResult(RequestSession(Some(Authorization("53cr37"))))(requestSession)
      }
    }

    "omit the Authorization header from the return value if it was omitted from the request" - {
      "to accurately forward the header's state to other services" in {
        val Return((requestSession, _)) = runReader(authorization = None)
        assertResult(RequestSession(None))(requestSession)
      }
    }

    "fail if the Accept header is not a value we support" - {
      "(because we can only encode the response to one of the supported formats)" - {
        "the Accept header is missing" in {
          val result = runReader(accept = None)
          assert(result.isThrow)
        }

        "the Accept header cannot be decoded as a MediaType" in {
          val result = runReader(accept = Some("---not-a-media-type---"))
          assert(result.isThrow)
        }

        "the Accept header is not compatible with a MediaType in `produces`" - {
          "where `produces` is empty" in {
            val result = runReader(produces = DispatchingMediaTypedEncoder(Seq.empty))
            assert(result.isThrow)
          }
          "where `produces` contains only incompatible header values" in {
            val result = runReader(accept = Some("text/plain"))
            assert(result.isThrow)
          }
        }
      }
    }

    "if the Accept header has a value we support" - {
      "include the corresponding Content-Type in the return value" - {
        "so that it can be included as a header in the response" in {
          val mediaType = MediaType("application", "json")
          val encoders = Seq(MediaTypedEncoder(implicitly[Encoder[Unit]], mediaType))
          val produces = DispatchingMediaTypedEncoder(encoders)
          val Return((_, responseEncoder)) = runReader(produces = produces)
          assertResult(mediaType)(responseEncoder.mediaType)
        }
      }

      "include the first corresponding response encoder in the return value" - {
        "so that it can be used to correctly encode the response" in {
          val produces = DispatchingMediaTypedEncoder(Seq(
            MediaTypedEncoder(Encoder.instance[Int](_ => 0.asJson), MediaType("text", "plain")),
            MediaTypedEncoder(Encoder.instance[Int](_ => 1.asJson), MediaTypes.applicationJson),
            MediaTypedEncoder(Encoder.instance[Int](_ => 2.asJson), MediaTypes.applicationJson)
          ))
          val Return((_, responseEncoder)) = runReader(produces = produces)
          assertResult(Json.fromInt(1))(responseEncoder.encoder(42))
        }
      }
    }

    def runReader[Res](
      accept: Option[String] = Some(MediaTypes.applicationJson.show),
      authorization: Option[String] = None,
      produces: DispatchingMediaTypedEncoder[Res] = DispatchingMediaTypedEncoder(Seq(
        MediaTypedEncoder(Encoder.instance[Res](_ => Json.Null), MediaTypes.applicationJson)
      ))
    ): Try[(RequestSession, MediaTypedEncoder[Res])] = {
      val request = RequestBuilder()
        .url("http://some.host")
        .setHeader("Accept", accept.toSeq)
        .setHeader("Authorization", authorization.toSeq)
        .setHeader("Content-Type", MediaTypes.applicationJson.show)
        .buildPost(Buf.Utf8("\"null\""))

      val reader = factory(produces)
      val res = reader(Input(request))
      Try(unpack(res.get._2)).map { context => 
        (context.session, context.responseEncoder)
      }
    }

  }

}

object RequestReadersSpec {

  /** This factory trait is needed because the `Req` type is different for each factory function in
    * [[RequestValidators]], but the `Res` type can be different for each test case in `baseReader`.
    *
    * Thus, we cannot just use a factory method, because that would couple `Req` and `Res` together.
    */
  trait RequestReaderFactory[Req] {
    def apply[Res](
      produces: DispatchingMediaTypedEncoder[Res]
    ): Endpoint[EndpointContext[Req, Res]]
  }

  object NoBodyReaderFactory extends RequestReaderFactory[Unit] {
    override def apply[Res](
      produces: DispatchingMediaTypedEncoder[Res]
    ): Endpoint[EndpointContext[Unit, Res]] = {
      RequestValidators.noBody(produces)
    }
  }

  object StandardReaderFactory extends RequestReaderFactory[String] {
    override def apply[Res](
      produces: DispatchingMediaTypedEncoder[Res]
    ): Endpoint[EndpointContext[String, Res]] = {
      RequestValidators.standard(
        accepts = MediaTypedRequestDecoder.apply(MediaTypedDecoder(MediaTypes.applicationJson)),
        produces = produces
      )
    }
  }

  def unpack[A](result: Eval[Future[Output[A]]]): A = {
    val future = result.value
    val output = Await.result(future)
    output.value
  }
}
