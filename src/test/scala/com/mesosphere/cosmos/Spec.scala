package com.mesosphere.cosmos

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import io.finch.test.ServiceIntegrationSuite
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{Matchers, fixture}

abstract class Spec
  extends fixture.FlatSpec
  with Matchers
  with ServiceIntegrationSuite
  with TableDrivenPropertyChecks {

  def createService: Service[Request, Response] = {
    Cosmos.service
  }

}
