package com.mesosphere.cosmos

import com.mesosphere.cosmos.handler.ServiceUpdateSpec
import org.scalatest.Suites

final class IntegrationTestsSpec extends Suites(
  new ListVersionsSpec,
  new PackageDescribeSpec,
  new PackageInstallIntegrationSpec,
  new PackageListIntegrationSpec,
  new PackageRepositorySpec,
  new PackageSearchSpec,
  new ServiceDescribeSpec,
  new ServiceUpdateSpec,
  new handler.CapabilitiesHandlerSpec,
  new handler.PackageRenderHandlerSpec,
  new handler.RequestErrorsSpec,
  new handler.ResourceProxyHandlerIntegrationSpec,
  new handler.UninstallHandlerSpec,
  new rpc.v1.model.ErrorResponseSpec
) with IntegrationBeforeAndAfterAll
