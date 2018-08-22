package com.mesosphere.cosmos

import org.scalatest.Suites

final class DcosIntegrationTestsSpec extends Suites(
  new AdminRouterClientSpec,
  new ServicesIntegrationSpec,
  new repository.UniverseClientSpec
)
