package com.mesosphere.universe.common

import io.circe.Json
import io.circe.JsonObject
import io.circe.Printer

object JsonUtil {
  val dropNullKeysPrinter: Printer = Printer.noSpaces.copy(dropNullKeys = true)

  /** Merges two JSON objects into one JSON object.
   *
   *  This methods returns a new object which overrides the fields in `target` with the fields
   *  in `fragment`:
   *    1. If a field in `target` also exists in `fragment` and the values are both objects then
   *       it recursively merges the two values.
   *    2. If a field in `target` also exists in `fragment` but they are not both objects then
   *       it overrides the value from the `target` field with the value from the `fragment`
   *       field.
   *    3. If a field exists in `target` but not in `fragment` then it keeps the field and value
   *       from `target`.
   *    4. If a field doesn't exists in `target` but it exists in `fragment` then it keeps the
   *       field and value from `fragment`.
   */
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
