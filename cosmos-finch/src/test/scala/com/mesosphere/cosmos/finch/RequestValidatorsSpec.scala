package com.mesosphere.cosmos.finch

import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.CompoundMediaTypeParser
import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.http.HttpRequestBody
import com.mesosphere.cosmos.http.MediaType
import com.mesosphere.cosmos.http.MediaTypeSpec
import com.mesosphere.cosmos.http.Monolithic
import com.mesosphere.cosmos.http.NoBody
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.rpc.MediaTypes._
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Method
import com.twitter.io.Buf
import com.twitter.util.Await
import com.twitter.util.Return
import com.twitter.util.Throw
import com.twitter.util.Try
import io.circe.Encoder
import io.circe.Json
import io.circe.syntax._
import io.finch.Endpoint
import io.finch.Error.NotParsed
import io.finch.Error.NotPresent
import io.finch.Error.NotValid
import io.finch.Input
import io.finch.Output
import io.finch.items.HeaderItem
import org.scalacheck.Gen
import org.scalatest.FreeSpec
import org.scalatest.Matchers
import org.scalatest.prop.PropertyChecks
import scala.util.Random

final class RequestValidatorsSpec extends FreeSpec with Matchers with PropertyChecks {

  import RequestValidatorsSpec._

  "The RequestValidator built by RequestValidators.noBody should" - {

    behave like baseValidator(factory = NoBodyReaderFactory)

    "always set requestBody to () for valid requests" in {
      val (request, validator) = forSuccessfulValidation[Int]
      val context = validate(request, validator)
      assertResult(())(context.requestBody)
    }

    "always set session.contentType to None for valid requests" in {
      val (request, validator) = forSuccessfulValidation[String]
      val context = validate(request, validator)
      assertResult(None)(context.session.contentType)
    }

    def buildProduces[Res](mediaType: MediaType): DispatchingMediaTypedEncoder[Res] = {
      val encoder = Encoder.instance[Res](_ => Json.Null)
      val mediaTypedEncoder = MediaTypedEncoder(encoder, mediaType)
      DispatchingMediaTypedEncoder(Set(mediaTypedEncoder))
    }

    def forSuccessfulValidation[Res]: (HttpRequest, Endpoint[EndpointContext[Unit, Res]]) = {
      val accept = TestingMediaTypes.applicationJson
      val request = NoBodyReaderFactory.buildRequest(accept = Some(accept.show), authorization = None)
      val validator = NoBodyReaderFactory(buildProduces[Res](accept))
      (request, validator)
    }

    def validate[Req, Res](
      request: HttpRequest,
      validator: Endpoint[EndpointContext[Req, Res]]
    ): EndpointContext[Req, Res] = {
      val Some((_, eval)) = validator(Input(HttpRequest.toFinagle(request)))
      Await.result(eval.value).value
    }

  }

  "The RequestValidator built by RequestValidators.standard should" - {

    behave like baseValidator(factory = StandardReaderFactory)

    "fail if the Content-Type header is missing" in {
      val genTestCases = for {
        expectedContentTypeHeader <- MediaTypeSpec.genMediaType
        data <- genTestData(expectedContentTypeHeader, actualContentTypeHeader = None)
      } yield data

      forAll (genTestCases) { data =>
        assertMissingContentType(data.validator, data.request)
      }
    }

    "fail if the Content-Type header cannot be parsed as a MediaType" in {
      val genTestCases = for {
        expectedContentTypeHeader <- MediaTypeSpec.genMediaType
        actualContentTypeHeader <- Gen.alphaStr
        data <- genTestData(expectedContentTypeHeader, Some(actualContentTypeHeader))
      } yield data

      forAll (genTestCases) { data =>
        assertUnparseableContentType(data.validator, data.request)
      }
    }

    "include the Content-Type in the session if it was successfully parsed" in {
      val genTestCases = for {
        expectedContentTypeHeader <- MediaTypeSpec.genMediaType
        actualContentTypeHeader = HttpRequest.toHeader(expectedContentTypeHeader)
        data <- genTestData(expectedContentTypeHeader, actualContentTypeHeader)
      } yield data

      forAll (genTestCases) { data =>
        assertValidContentType(data.contentType, data.validator, data.request)
      }
    }

    "fail if the Content-Type header doesn't match what the validator expects" in {
      val genTestCases = for {
        expectedContentTypeHeader <- MediaTypeSpec.genMediaType
        actualContentTypeHeader <- MediaTypeSpec.genMediaType.map(HttpRequest.toHeader)
        data <- genTestData(expectedContentTypeHeader, actualContentTypeHeader)
      } yield data

      forAll (genTestCases) { data =>
        assertMismatchedContentType(data.contentType, data.validator, data.request)
      }
    }

    // TODO package-add: Tests for body validation

    def genTestData(
      expectedContentTypeHeader: MediaType,
      actualContentTypeHeader: Option[String]
    ): Gen[TestData[Json, Json]] = {
      val mediaTypedDecoder = MediaTypedDecoder[Json](expectedContentTypeHeader)
      val accepts = MediaTypedRequestDecoder(mediaTypedDecoder)

      for {
        acceptHeader <- MediaTypeSpec.genMediaType
        headers = HttpRequest.collectHeaders(
          Fields.Accept -> HttpRequest.toHeader(acceptHeader),
          Fields.ContentType -> actualContentTypeHeader
        )
        produces = DispatchingMediaTypedEncoder[Json](acceptHeader)
        validator = RequestValidators.standard(accepts, produces)
        request <- genRequest(headers, Monolithic(Buf.Utf8("{}")))
      } yield TestData(expectedContentTypeHeader, validator, request)
    }

  }

  def assertMissingContentType[Req, Res](
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Unit = {
    validate(validator, request) should matchPattern {
      case Throw(NotPresent(HeaderItem(Fields.ContentType))) =>
    }
  }

  def assertUnparseableContentType[Req, Res](
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Unit = {
    whenever (MediaType.parse(request.headers(Fields.ContentType)).isThrow) {
      validate(validator, request) should matchPattern {
        case Throw(NotParsed(HeaderItem(Fields.ContentType), _, _)) =>
        case Throw(NotValid(HeaderItem(Fields.ContentType), _)) =>
      }
    }
  }

  def assertValidContentType[Req, Res](
    expectedContentType: MediaType,
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Unit = {
    assertResult(expectedContentType) {
      val Return(output) = validate(validator, request)
      val RequestSession(_, Some(contentType)) = output.value.session
      contentType
    }
  }

  def assertMismatchedContentType[Req, Res](
    expectedContentType: MediaType,
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Unit = {
    val expected = HttpRequest.toHeader(expectedContentType)
    val actual = request.headers.get(Fields.ContentType)

    whenever (expected != actual) {
      validate(validator, request) should matchPattern {
        case Throw(NotValid(HeaderItem(Fields.ContentType), _)) =>
      }
    }
  }

  def baseValidator[Req](factory: RequestReaderFactory[Req]): Unit = {

    behave like baseValidatorAuthorization(factory)

    behave like baseValidatorBadAccept(factory)

    behave like baseValidatorSingleAccept(factory)

    behave like baseValidatorMultiAccept(factory)

  }

  def baseValidatorAuthorization[Req](factory: RequestReaderFactory[Req]): Unit = {

    "include the Authorization header in the return value if it was included in the request" - {
      "to accurately forward the header's state to other services" in {
        val Return(context) = factory.validate(authorization = Some("53cr37"))
        val RequestSession(Some(Authorization(auth)), _) = context.session
        assertResult("53cr37")(auth)
      }
    }

    "omit the Authorization header from the return value if it was omitted from the request" - {
      "to accurately forward the header's state to other services" in {
        val Return(context) = factory.validate(authorization = None)
        val RequestSession(auth, _) = context.session
        assertResult(None)(auth)
      }
    }

  }

  def baseValidatorBadAccept[Req](factory: RequestReaderFactory[Req]): Unit = {

    "fail if the Accept header is not a value we support" - {
      "(because we can only encode the response to one of the supported formats)" - {
        "the Accept header is missing" in {
          val result = factory.validate(accept = None)
          assert(result.isThrow)
        }

        "the Accept header cannot be decoded as a MediaType" in {
          val result = factory.validate(accept = Some("---not-a-media-type---"))
          assert(result.isThrow)
        }

        "the Accept header is not compatible with a MediaType in `produces`" - {
          "where `produces` is empty" in {
            val produces = DispatchingMediaTypedEncoder(Set.empty[MediaTypedEncoder[String]])
            val result = factory.validate(produces = produces)
            assert(result.isThrow)
          }
          "where `produces` contains only incompatible header values" in {
            val result = factory.validate(accept = Some("text/plain"))
            assert(result.isThrow)
          }
        }
      }
    }

  }

  def baseValidatorSingleAccept[Req](factory: RequestReaderFactory[Req]): Unit = {

    "if the Accept header has a value we support" - {
      "include the corresponding Content-Type in the return value" - {
        "so that it can be included as a header in the response" in {
          val mediaType = TestingMediaTypes.applicationJson
          val encoders = Set(MediaTypedEncoder(implicitly[Encoder[Unit]], mediaType))
          val produces = DispatchingMediaTypedEncoder(encoders)
          val result = factory.validate(accept = Some(mediaType.show), produces = produces)
          val Return(context) = result
          assertResult(mediaType)(context.responseEncoder.mediaType)
        }
      }

      "include the first corresponding response encoder in the return value" - {
        "so that it can be used to correctly encode the response" in {
          val produces = DispatchingMediaTypedEncoder(Set(
            MediaTypedEncoder(Encoder.instance[Int](_ => 0.asJson), MediaType("text", "plain")),
            MediaTypedEncoder(Encoder.instance[Int](_ => 1.asJson), TestingMediaTypes.applicationJson)
          ))
          val Return(context) = factory.validate(produces = produces)
          assertResult(Json.fromInt(1))(context.responseEncoder.encoder(Random.nextInt()))
        }
      }
    }

  }

  def baseValidatorMultiAccept[Req](factory: RequestReaderFactory[Req]): Unit = {

    "if the Accept header has multiple values we support" - {
      "include the highest quality Content-Type in the return value" in {
        val v1String = V1InstallResponse.show + ";q=0.8"
        val v2String = V2InstallResponse.show + ";q=0.9"
        val expected = V2InstallResponse

        val accept = CompoundMediaTypeParser.parse(Seq(v1String, v2String).mkString(",")).get
        val encoders = Set(
          MediaTypedEncoder(implicitly[Encoder[Unit]], V1InstallResponse),
          MediaTypedEncoder(implicitly[Encoder[Unit]], V2InstallResponse)
        )
        val produces = DispatchingMediaTypedEncoder(encoders)
        val result = factory.validate(accept = Some(accept.show), produces = produces)
        val Return(context) = result
        assertResult(expected)(context.responseEncoder.mediaType)
      }
    }

  }

}

object RequestValidatorsSpec {

  def genRequest(
    genHeaders: Gen[Map[String, String]],
    genBody: Gen[HttpRequestBody]
  ): Gen[HttpRequest] = {
    for {
      method <- genMethod
      path <- genPath
      headers <- genHeaders
      body <- genBody
    } yield HttpRequest(method, path, headers, body)
  }

  val genMethod: Gen[Method] = Gen.alphaStr.map(Method(_))

  val genPath: Gen[String] = {
    Gen.listOf(Gen.frequency((10, Gen.alphaNumChar), Gen.freqTuple((1, '/')))).map(_.mkString)
  }

  def validate[Req, Res](
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Try[Output[EndpointContext[Req, Res]]] = {
    val Some((_, eval)) = validator(Input(HttpRequest.toFinagle(request)))
    Await.result(eval.value.liftToTry)
  }

  case class TestData[Req, Res](
    contentType: MediaType,
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  )

  /** This factory trait is needed because the `Req` type is different for each factory function in
    * [[RequestValidators]], but the `Res` type can be different for each test case in `baseReader`.
    *
    * Thus, we cannot just use a factory method, because that would couple `Req` and `Res` together.
    */
  trait RequestReaderFactory[Req] {

    def apply[Res](
      produces: DispatchingMediaTypedEncoder[Res]
    ): Endpoint[EndpointContext[Req, Res]]

    def buildRequest(
      accept: Option[String],
      authorization: Option[String]
    ): HttpRequest

    final def validate[Res](
      accept: Option[String] = Some(TestingMediaTypes.applicationJson.show),
      authorization: Option[String] = None,
      produces: DispatchingMediaTypedEncoder[Res] = DispatchingMediaTypedEncoder(Set(
        MediaTypedEncoder(Encoder.instance[Res](_ => Json.Null), TestingMediaTypes.applicationJson)
      ))
    ): Try[EndpointContext[Req, Res]] = {
      val request = HttpRequest.toFinagle(buildRequest(accept, authorization))
      val reader = this(produces)
      val Some((_, eval)) = reader(Input(request))
      Await.result(eval.value.liftToTry).map(_.value)
    }

  }

  object NoBodyReaderFactory extends RequestReaderFactory[Unit] {

    override def apply[Res](
      produces: DispatchingMediaTypedEncoder[Res]
    ): Endpoint[EndpointContext[Unit, Res]] = {
      RequestValidators.noBody(produces)
    }

    override def buildRequest(
      accept: Option[String],
      authorization: Option[String]
    ): HttpRequest = {
      val headers =
        HttpRequest.collectHeaders(Fields.Accept -> accept, Fields.Authorization -> authorization)
      HttpRequest(Method.Get, "what/ever", headers, NoBody)
    }

  }

  object StandardReaderFactory extends RequestReaderFactory[Json] {

    override def apply[Res](
      produces: DispatchingMediaTypedEncoder[Res]
    ): Endpoint[EndpointContext[Json, Res]] = {
      RequestValidators.standard(
        accepts = MediaTypedRequestDecoder(MediaTypedDecoder(TestingMediaTypes.applicationJson)),
        produces = produces
      )
    }

    override def buildRequest(
      accept: Option[String],
      authorization: Option[String]
    ): HttpRequest = {
      val headers = HttpRequest.collectHeaders(
        Fields.Accept -> accept,
        Fields.Authorization -> authorization,
        Fields.ContentType -> HttpRequest.toHeader(TestingMediaTypes.applicationJson)
      )
      HttpRequest(Method.Post, "what/ever", headers, Monolithic(Buf.Utf8(Json.Null.noSpaces)))
    }

  }

}
