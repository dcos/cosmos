package com.mesosphere.cosmos

import org.scalatest.Suites

final class IntegrationTestsSpec extends Suites(
  new ListVersionsSpec,
  new NonSharedServiceDescribeSpec,
  new PackageDescribeSpec,
  new PackageInstallIntegrationSpecBreakEEAgain,
  new PackageListIntegrationSpec,
  new PackageRepositorySpec,
  new PackageSearchSpec,
  new ServiceDescribeSpec,
  new handler.CapabilitiesHandlerSpec,
  new handler.PackageRenderHandlerSpec,
  new handler.RequestErrorsSpec,
  new handler.UninstallHandlerSpec,
  new rpc.v1.model.ErrorResponseSpec
) with IntegrationBeforeAndAfterAll
