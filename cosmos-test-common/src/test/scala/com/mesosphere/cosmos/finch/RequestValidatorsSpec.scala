package com.mesosphere.cosmos.finch

import cats.data.NonEmptyList
import com.mesosphere.Generators.Implicits._
import com.mesosphere.cosmos.http.Authorization
import com.mesosphere.cosmos.http.Get
import com.mesosphere.cosmos.http.HttpRequest
import com.mesosphere.cosmos.http.HttpRequestMethod
import com.mesosphere.cosmos.http.Post
import com.mesosphere.cosmos.http.RawRpcPath
import com.mesosphere.cosmos.http.RequestSession
import com.mesosphere.cosmos.httpInterface
import com.mesosphere.cosmos.rpc.MediaTypes._
import com.mesosphere.http.CompoundMediaTypeParser
import com.mesosphere.http.MediaType
import com.mesosphere.util.UrlSchemeHeader
import com.twitter.finagle.http.Fields
import com.twitter.finagle.http.Fields
import com.twitter.io.Buf
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
import io.finch.Output
import io.finch.items.HeaderItem
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.Assertion
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
      validator(HttpRequest.toFinchInput(request)).awaitValueUnsafe().get
    }

  }

  "The RequestValidator built by RequestValidators.standard should" - {

    behave like baseValidator(factory = StandardReaderFactory)

    "fail if the Content-Type header is missing" in {
      val genTestCases = for {
        expectedContentTypeHeader <- arbitrary[MediaType]
        data <- genTestData(expectedContentTypeHeader, actualContentTypeHeader = None)
      } yield data

      forAll (genTestCases) { data =>
        assertMissingContentType(data.validator, data.request)
      }
    }

    "fail if the Content-Type header cannot be parsed as a MediaType" in {
      val genTestCases = for {
        expectedContentTypeHeader <- arbitrary[MediaType]
        actualContentTypeHeader <- Gen.alphaStr
        data <- genTestData(expectedContentTypeHeader, Some(actualContentTypeHeader))
      } yield data

      forAll (genTestCases) { data =>
        assertUnparseableContentType(data.validator, data.request)
      }
    }

    "include the Content-Type in the session if it was successfully parsed" in {
      val genTestCases = for {
        expectedContentTypeHeader <- arbitrary[MediaType]
        actualContentTypeHeader = HttpRequest.toHeader(expectedContentTypeHeader)
        data <- genTestData(expectedContentTypeHeader, actualContentTypeHeader)
      } yield data

      forAll (genTestCases) { data =>
        assertValidContentType(data.contentType, data.validator, data.request)
      }
    }

    "fail if the Content-Type header doesn't match what the validator expects" in {
      val genTestCases = for {
        expectedContentTypeHeader <- arbitrary[MediaType]
        actualContentTypeHeader <- arbitrary[MediaType].map(HttpRequest.toHeader)
        data <- genTestData(expectedContentTypeHeader, actualContentTypeHeader)
      } yield data

      forAll (genTestCases) { data =>
        assertMismatchedContentType(data.contentType, data.validator, data.request)
      }
    }

    def genTestData(
      expectedContentTypeHeader: MediaType,
      actualContentTypeHeader: Option[String]
    ): Gen[TestData[Json, Json]] = {
      val mediaTypedDecoder = MediaTypedDecoder[Json](expectedContentTypeHeader)
      val accepts = MediaTypedRequestDecoder(mediaTypedDecoder)

      for {
        acceptHeader <- arbitrary[MediaType]
        headers = HttpRequest.collectHeaders(
          Fields.Accept -> HttpRequest.toHeader(acceptHeader),
          Fields.ContentType -> actualContentTypeHeader,
          Fields.Host -> Some(httpInterface.toString()),
          UrlSchemeHeader -> Some("http")
        )
        produces = DispatchingMediaTypedEncoder[Json](acceptHeader)
        validator = RequestValidators.standard(accepts, produces)
        request <- genRequest(Post(Buf.Utf8("{}")))(headers)
      } yield TestData(expectedContentTypeHeader, validator, request)
    }
  }

  def assertMissingContentType[Req, Res](
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Assertion = {
    validateOutput(validator, request) should matchPattern {
      case Throw(NotPresent(HeaderItem(Fields.ContentType))) =>
    }
  }

  def assertUnparseableContentType[Req, Res](
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Assertion = {
    whenever (MediaType.parse(request.headers(Fields.ContentType)).isFailure) {
      // Work around for DCOS_OSS-2289
      validateOutput(validator, request) should matchPattern {
        case Throw(NotParsed(HeaderItem(Fields.ContentType), _, _)) =>
        case Throw(NotValid(HeaderItem(Fields.ContentType), _)) =>
      }
    }
  }

  def assertValidContentType[Req, Res](
    expectedContentType: MediaType,
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Assertion = {
    assertResult(expectedContentType) {
      val Return(output) = validateOutput(validator, request)
      val RequestSession(_, _, Some(contentType)) = output.value.session
      contentType
    }
  }

  def assertMismatchedContentType[Req, Res](
    expectedContentType: MediaType,
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Assertion = {
    val expected = HttpRequest.toHeader(expectedContentType)
    val actual = request.headers.get(Fields.ContentType)

    whenever (expected != actual) {
      validateOutput(validator, request) should matchPattern {
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
        val RequestSession(Some(Authorization(auth)), _, _) = context.session
        assertResult("53cr37")(auth)
      }
    }

    "omit the Authorization header from the return value if it was omitted from the request" - {
      "to accurately forward the header's state to other services" in {
        val Return(context) = factory.validate(authorization = None)
        val RequestSession(auth, _, _) = context.session
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
          assertResult(NonEmptyList.of(mediaType))(context.responseEncoder.mediaTypes)
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
        assertResult(NonEmptyList.of(expected))(context.responseEncoder.mediaTypes)
      }
    }

  }

}

object RequestValidatorsSpec {

  def genRequest(method: HttpRequestMethod)(
    genHeaders: Gen[Map[String, String]]
  ): Gen[HttpRequest] = {
    for {
      path <- genPath
      headers <- genHeaders
    } yield HttpRequest(RawRpcPath(path), headers, method)
  }

  val genPath: Gen[String] = {
    Gen.listOf(Gen.frequency((10, Gen.alphaNumChar), Gen.freqTuple((1, '/')))).map(_.mkString)
  }

  def validateOutput[Req, Res](
    validator: Endpoint[EndpointContext[Req, Res]],
    request: HttpRequest
  ): Try[Output[EndpointContext[Req, Res]]] = {
    validator(HttpRequest.toFinchInput(request)).awaitOutput().get
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
      val reader = this(produces)
      reader(HttpRequest.toFinchInput(buildRequest(accept, authorization))).awaitValue().get
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
        HttpRequest.collectHeaders(
          Fields.Accept -> accept,
          Fields.Authorization -> authorization,
          Fields.Host -> Some(httpInterface.toString()),
          UrlSchemeHeader -> Some("http")
        )
      HttpRequest(RawRpcPath("/what/ever"), headers, Get())
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
        Fields.Host -> Some(httpInterface.toString()),
        UrlSchemeHeader -> Some("http"),
        Fields.ContentType -> HttpRequest.toHeader(TestingMediaTypes.applicationJson)
      )
      HttpRequest(RawRpcPath("/what/ever"), headers, Post(Buf.Utf8(Json.Null.noSpaces)))
    }

  }

}
