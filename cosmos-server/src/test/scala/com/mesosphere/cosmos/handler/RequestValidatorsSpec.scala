package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.circe.{DispatchingMediaTypedEncoder, MediaTypedDecoder, MediaTypedEncoder}
import com.mesosphere.cosmos.http._
import com.twitter.finagle.http.RequestBuilder
import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Return, Try}
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.finch.{Endpoint, Input, Output}
import org.scalatest.FreeSpec
import cats.Eval
import com.mesosphere.cosmos.finch.MediaTypedRequestDecoder

final class RequestValidatorsSpec extends FreeSpec {

  import RequestValidatorsSpec._

  "The RequestValidator built by RequestValidators.noBody should" - {

    behave like baseValidator(factory = NoBodyReaderFactory)

  }

  "The RequestValidator built by RequestValidators.standard should" - {

    behave like baseValidator(factory = StandardReaderFactory)

  }

  def baseValidator[Req](factory: RequestReaderFactory[Req]): Unit = {

    "include the Authorization header in the return value if it was included in the request" - {
      "to accurately forward the header's state to other services" in {
        val Return((requestSession, _)) = evaluateEndpoint(authorization = Some("53cr37"))
        assertResult(RequestSession(Some(Authorization("53cr37"))))(requestSession)
      }
    }

    "omit the Authorization header from the return value if it was omitted from the request" - {
      "to accurately forward the header's state to other services" in {
        val Return((requestSession, _)) = evaluateEndpoint(authorization = None)
        assertResult(RequestSession(None))(requestSession)
      }
    }

    "fail if the Accept header is not a value we support" - {
      "(because we can only encode the response to one of the supported formats)" - {
        "the Accept header is missing" in {
          val result = evaluateEndpoint(accept = None)
          assert(result.isThrow)
        }

        "the Accept header cannot be decoded as a MediaType" in {
          val result = evaluateEndpoint(accept = Some("---not-a-media-type---"))
          assert(result.isThrow)
        }

        "the Accept header is not compatible with a MediaType in `produces`" - {
          "where `produces` is empty" in {
            val result = evaluateEndpoint(produces = DispatchingMediaTypedEncoder(Set.empty[MediaTypedEncoder[String]]))
            assert(result.isThrow)
          }
          "where `produces` contains only incompatible header values" in {
            val result = evaluateEndpoint(accept = Some("text/plain"))
            assert(result.isThrow)
          }
        }
      }
    }

    "if the Accept header has a value we support" - {
      "include the corresponding Content-Type in the return value" - {
        "so that it can be included as a header in the response" in {
          val mediaType = MediaType("application", "json")
          val encoders = Set(MediaTypedEncoder(implicitly[Encoder[Unit]], mediaType))
          val produces = DispatchingMediaTypedEncoder(encoders)
          val (_, responseEncoder) = evaluateEndpoint(accept = Some(mediaType.show), produces = produces).get
          assertResult(mediaType)(responseEncoder.mediaType)
        }
      }

      "include the first corresponding response encoder in the return value" - {
        "so that it can be used to correctly encode the response" in {
          val produces = DispatchingMediaTypedEncoder(Set(
            MediaTypedEncoder(Encoder.instance[Int](_ => 0.asJson), MediaType("text", "plain")),
            MediaTypedEncoder(Encoder.instance[Int](_ => 1.asJson), MediaTypes.applicationJson)
          ))
          val (_, responseEncoder) = evaluateEndpoint(produces = produces).get
          assertResult(Json.fromInt(1))(responseEncoder.encoder(42))
        }
      }
    }

    "if the Accept header has multiple values we support" - {
      "include the highest quality Content-Type in the return value" in {
        val v1String = MediaTypes.V1InstallResponse.show + ";q=0.8"
        val v2String = MediaTypes.V2InstallResponse.show + ";q=0.9"
        val expected = MediaTypes.V2InstallResponse

        val accept = CompoundMediaTypeParser.parse(Seq(v1String, v2String).mkString(",")).get
        val encoders = Set(
          MediaTypedEncoder(implicitly[Encoder[Unit]], MediaTypes.V1InstallResponse),
          MediaTypedEncoder(implicitly[Encoder[Unit]], MediaTypes.V2InstallResponse)
        )
        val produces = DispatchingMediaTypedEncoder(encoders)
        val (_, responseEncoder) = evaluateEndpoint(accept = Some(accept.show), produces = produces).get
        val actual = responseEncoder.mediaType
        assertResult(expected)(actual)
      }
    }

    def evaluateEndpoint[Res](
      accept: Option[String] = Some(MediaTypes.applicationJson.show),
      authorization: Option[String] = None,
      produces: DispatchingMediaTypedEncoder[Res] = DispatchingMediaTypedEncoder(Set(
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

object RequestValidatorsSpec {

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
