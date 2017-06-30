package com.mesosphere.cosmos.render

import scala.util.control.NoStackTrace

sealed abstract class PackageDefinitionRenderError(causedBy: Option[Throwable] = None)
  extends RuntimeException(causedBy.orNull)

//final case class OptionsValidationFailure(validationErrors: ValidationErrors) extends PackageDefinitionRenderError
//final case class InvalidLabelSchema(cause: DecodingFailure) extends PackageDefinitionRenderError(Some(cause))
//final case class RenderedTemplateNotJson(cause: ParsingFailure) extends PackageDefinitionRenderError(Some(cause))
//object RenderedTemplateNotJsonObject extends PackageDefinitionRenderError
object MissingMarathonV2AppTemplate extends PackageDefinitionRenderError with NoStackTrace
//object OptionsNotAllowed extends PackageDefinitionRenderError with NoStackTrace
