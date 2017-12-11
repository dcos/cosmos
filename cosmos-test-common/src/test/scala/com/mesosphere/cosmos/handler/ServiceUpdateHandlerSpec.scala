package com.mesosphere.cosmos.handler

import com.mesosphere.cosmos.error.CosmosException
import com.mesosphere.cosmos.handler.ServiceUpdateHandler._
import com.mesosphere.cosmos.rpc
import io.circe.syntax._
import org.scalatest.FreeSpec
import org.scalatest.Matchers

class ServiceUpdateHandlerSpec extends FreeSpec with Matchers {

  import ServiceUpdateHandlerSpec._

  "The mergeStoredAndProvided() method" - {
    "should always return provided when replace is true" in {
      val replace = true
      val provided = configA
      mergeStoredAndProvided(None, provided, replace) shouldBe provided
      mergeStoredAndProvided(configB, provided, replace) shouldBe provided
      mergeStoredAndProvided(configC, provided, replace) shouldBe provided
    }

    "should return the merge of stored and provided when" +
      " replace is false, and stored is present" in {
      val replace = false
      val stored = configA
      mergeStoredAndProvided(stored, None, replace) shouldBe stored
      mergeStoredAndProvided(stored, configB, replace) shouldBe configAB
      mergeStoredAndProvided(stored, configC, replace) shouldBe configAC
    }

    "should throw an error when replace is false and there are no stored options" in {
      val replace = false
      val stored = None
      val errorType = "OptionsNotStored"
      val error1 = rpc.v1.model.ErrorResponse(
        intercept[CosmosException](
          mergeStoredAndProvided(stored, None, replace)
        ).error
      )
      error1.`type` shouldBe errorType
      val error2 = rpc.v1.model.ErrorResponse(
        intercept[CosmosException](
          mergeStoredAndProvided(stored, configA, replace)
        ).error
      )
      error2.`type` shouldBe errorType
    }
  }

}

object ServiceUpdateHandlerSpec {

  // scalastyle:off multiple.string.literals
  val configA = Map(
    "a" -> "aa-val",
    "b" -> "ab-val",
    "c" -> "ac-val"
  ).asJson.asObject

  val configB = Map(
    "d" -> "bd-val",
    "e" -> "be-val"
  ).asJson.asObject

  val configC = Map(
    "c" -> "cc-val",
    "d" -> "cd-val",
    "e" -> "ce-val"
  ).asJson.asObject

  val configAB =
    Map(
      "a" -> "aa-val",
      "b" -> "ab-val",
      "c" -> "ac-val",
      "d" -> "bd-val",
      "e" -> "be-val"
    ).asJson.asObject

  val configAC =
    Map(
      "a" -> "aa-val",
      "b" -> "ab-val",
      "c" -> "cc-val",
      "d" -> "cd-val",
      "e" -> "ce-val"
    ).asJson.asObject

  // scalastyle:on multiple.string.literals

}
