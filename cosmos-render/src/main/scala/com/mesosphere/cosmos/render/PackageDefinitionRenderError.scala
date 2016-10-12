package com.mesosphere.cosmos.render

import com.mesosphere.cosmos.jsonschema.JsonSchema.ValidationErrors
import io.circe.{DecodingFailure, ParsingFailure}

import scala.util.control.NoStackTrace

sealed abstract class PackageDefinitionRenderError(val causedBy: Throwable = null) extends RuntimeException(causedBy)

final case class OptionsValidationFailure(validationErrors: ValidationErrors) extends PackageDefinitionRenderError
final case class InvalidLabelSchema(cause: DecodingFailure) extends PackageDefinitionRenderError(cause)
final case class RenderedTemplateNotJson(cause: ParsingFailure = null) extends PackageDefinitionRenderError(cause)
object MissingMarathonV2AppTemplate extends PackageDefinitionRenderError with NoStackTrace
object OptionsNotAllowed extends PackageDefinitionRenderError with NoStackTrace
