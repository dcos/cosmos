package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.circe.Encoders.exceptionErrorResponse
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage.installqueue.ProcessorView
import com.mesosphere.cosmos.storage.v1.model.Install
import com.mesosphere.cosmos.storage.v1.model.PendingOperation
import com.mesosphere.cosmos.storage.v1.model.Uninstall
import com.mesosphere.cosmos.storage.v1.model.UniverseInstall
import com.mesosphere.universe
import com.twitter.util.Await
import com.twitter.util.Future
import java.util.UUID
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.scalatest.FreeSpec
import org.scalatest.Matchers

final class OperationProcessorSpec extends FreeSpec with Matchers {
  import OperationProcessorSpec._

  "Test success is called on install" in {
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            Install(UUID.fromString("cbe2bca5-a7b2-4d35-ac03-438570b82d8a"), pkg),
            None
          )
        )
      )
    )
    when(processorViewMock.success(packageCoordinate)).thenReturn(Future.Done)

    val processor = OperationProcessor(
      processorViewMock,
      SucceedingInstaller,
      FailingUniverseInstaller,
      FailingUninstaller
    )

    Await.result(processor())
  }

  "Test success is called on universe install" in {
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            UniverseInstall(pkg),
            None
          )
        )
      )
    )
    when(processorViewMock.success(packageCoordinate)).thenReturn(Future.Done)

    val processor = OperationProcessor(
      processorViewMock,
      FailingInstaller,
      SucceedingUniverseInstaller,
      FailingUninstaller
    )

    Await.result(processor())
  }

  "Test success is called on uninstall" in {
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            Uninstall(pkg),
            None
          )
        )
      )
    )
    when(processorViewMock.success(packageCoordinate)).thenReturn(Future.Done)

    val processor = OperationProcessor(
      processorViewMock,
      FailingInstaller,
      FailingUniverseInstaller,
      SucceedingUninstaller
    )

    Await.result(processor())
  }

  "Test failure is called on failing install" in {
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            Install(UUID.fromString("2d903d9e-3f67-4b12-8c99-1ea774d67b45"), pkg),
            None
          )
        )
      )
    )
    when(
      processorViewMock.failure(
        packageCoordinate,
        exceptionErrorResponse(installerError)
      )
    ).thenReturn(Future.Done)

    val processor = OperationProcessor(
      processorViewMock,
      FailingInstaller,
      SucceedingUniverseInstaller,
      SucceedingUninstaller
    )

    Await.result(processor())
  }

  "Test failure is called on failing universe install" in {
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            UniverseInstall(pkg),
            None
          )
        )
      )
    )
    when(
      processorViewMock.failure(
        packageCoordinate,
        exceptionErrorResponse(universeInstallerError)
      )
    ).thenReturn(Future.Done)

    val processor = OperationProcessor(
      processorViewMock,
      SucceedingInstaller,
      FailingUniverseInstaller,
      SucceedingUninstaller
    )

    Await.result(processor())
  }

  "Test failure is called on failing uninstall" in {
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            Uninstall(pkg),
            None
          )
        )
      )
    )
    when(
      processorViewMock.failure(
        packageCoordinate,
        exceptionErrorResponse(uninstallerError)
      )
    ).thenReturn(Future.Done)

    val processor = OperationProcessor(
      processorViewMock,
      SucceedingInstaller,
      SucceedingUniverseInstaller,
      FailingUninstaller
    )

    Await.result(processor())
  }

  "Test nothing is called when there are no pending operation" in {
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(Future.value(None))

    val processor = OperationProcessor(
      processorViewMock,
      SucceedingInstaller,
      SucceedingUniverseInstaller,
      SucceedingUninstaller
    )

    Await.result(processor())
  }
}

object OperationProcessorSpec {
  val (packageCoordinate, pkg) = {
    val name = "test"
    val version = universe.v3.model.Version("1.2.3")

    val packageCoordinate = rpc.v1.model.PackageCoordinate(name, version)

    val pkg = universe.v3.model.V3Package(
      universe.v3.model.V3PackagingVersion,
      name,
      version,
      universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
      "does@not.matter",
      "doesn't matter"
    )

    (packageCoordinate, pkg)
  }

  val SucceedingInstaller: Installer = (_, _) => Future.Done
  val SucceedingUniverseInstaller: UniverseInstaller = _ => Future.Done
  val SucceedingUninstaller: Uninstaller = _ => Future.Done

  val installerError = new IllegalArgumentException("Install failed")
  val FailingInstaller: Installer = (_, _) => Future.exception(installerError)

  val universeInstallerError = new IllegalArgumentException("Universe install failed")
  val FailingUniverseInstaller: UniverseInstaller = _ => Future.exception(universeInstallerError)

  val uninstallerError = new IllegalArgumentException("Uninstall failed")
  val FailingUninstaller: Uninstaller = _ => Future.exception(uninstallerError)
}
