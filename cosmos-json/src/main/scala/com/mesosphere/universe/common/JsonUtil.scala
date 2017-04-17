package com.mesosphere.universe.common

import io.circe.Json
import io.circe.JsonObject
import io.circe.Printer

object JsonUtil {
  val dropNullKeysPrinter: Printer = Printer.noSpaces.copy(dropNullKeys = true)

  def merge(target: JsonObject, fragment: JsonObject): JsonObject = {
    fragment.toList.foldLeft(target) { (updatedTarget, fragmentEntry) =>
      val (fragmentKey, fragmentValue) = fragmentEntry
      val targetValueOpt = updatedTarget(fragmentKey)

      val mergedValue = (targetValueOpt.flatMap(_.asObject), fragmentValue.asObject) match {
        case (Some(targetObject), Some(fragmentObject)) =>
          Json.fromJsonObject(merge(targetObject, fragmentObject))
        case _ => fragmentValue
      }

      updatedTarget.add(fragmentKey, mergedValue)
    }
  }
}
