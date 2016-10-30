package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.circe.Encoders.exceptionErrorResponse
import com.mesosphere.cosmos.model.ZooKeeperUri
import com.mesosphere.cosmos.rpc
import com.mesosphere.cosmos.storage.installqueue.Install
import com.mesosphere.cosmos.storage.installqueue.PendingOperation
import com.mesosphere.cosmos.storage.installqueue.ProcessorView
import com.mesosphere.cosmos.storage.installqueue.Uninstall
import com.mesosphere.cosmos.storage.installqueue.UniverseInstall
import com.mesosphere.cosmos.zookeeper.Clients
import com.mesosphere.universe
import com.netaporter.uri.Uri
import com.twitter.util.Await
import com.twitter.util.Future
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.test.TestingServer
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.FreeSpec
import org.scalatest.Matchers

final class OperationProcessorSpec extends FreeSpec with Matchers {
  import OperationProcessorSpec._

  private[this] implicit val timer = new com.twitter.util.NullTimer

  "Test success is called on install" in {
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      "test",
      universe.v3.model.PackageDefinition.Version("1.2.3")
    )
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            packageCoordinate,
            Install(
              Uri.parse("http://localhost/some.dcos"),
              universe.v3.model.V3Package(
                universe.v3.model.V3PackagingVersion,
                "test",
                universe.v3.model.PackageDefinition.Version("1.2.3"),
                universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
                "does@not.matter",
                "doesn't matter"
              )
            ),
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
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      "test",
      universe.v3.model.PackageDefinition.Version("1.2.3")
    )
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            packageCoordinate,
            UniverseInstall(
              universe.v3.model.V3Package(
                universe.v3.model.V3PackagingVersion,
                "test",
                universe.v3.model.PackageDefinition.Version("1.2.3"),
                universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
                "does@not.matter",
                "doesn't matter"
              )
            ),
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
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      "test",
      universe.v3.model.PackageDefinition.Version("1.2.3")
    )
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            packageCoordinate,
            Uninstall(
              Some(
                universe.v3.model.V3Package(
                  universe.v3.model.V3PackagingVersion,
                  "test",
                  universe.v3.model.PackageDefinition.Version("1.2.3"),
                  universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
                  "does@not.matter",
                  "doesn't matter"
                )
              )
            ),
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
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      "test",
      universe.v3.model.PackageDefinition.Version("1.2.3")
    )
    val operation = Install(
      Uri.parse("http://localhost/some.dcos"),
      universe.v3.model.V3Package(
        universe.v3.model.V3PackagingVersion,
        "test",
        universe.v3.model.PackageDefinition.Version("1.2.3"),
        universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
        "does@not.matter",
        "doesn't matter"
      )
    )
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            packageCoordinate,
            operation,
            None
          )
        )
      )
    )
    when(
      processorViewMock.failure(
        packageCoordinate,
        operation,
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
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      "test",
      universe.v3.model.PackageDefinition.Version("1.2.3")
    )
    val operation = UniverseInstall(
      universe.v3.model.V3Package(
        universe.v3.model.V3PackagingVersion,
        "test",
        universe.v3.model.PackageDefinition.Version("1.2.3"),
        universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
        "does@not.matter",
        "doesn't matter"
      )
    )
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            packageCoordinate,
            operation,
            None
          )
        )
      )
    )
    when(
      processorViewMock.failure(
        packageCoordinate,
        operation,
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
    val packageCoordinate = rpc.v1.model.PackageCoordinate(
      "test",
      universe.v3.model.PackageDefinition.Version("1.2.3")
    )
    val operation = Uninstall(
      Some(
        universe.v3.model.V3Package(
          universe.v3.model.V3PackagingVersion,
          "test",
          universe.v3.model.PackageDefinition.Version("1.2.3"),
          universe.v3.model.PackageDefinition.ReleaseVersion(0).get,
          "does@not.matter",
          "doesn't matter"
        )
      )
    )
    val processorViewMock = mock(classOf[ProcessorView])
    when(processorViewMock.next()).thenReturn(
      Future.value(
        Some(
          PendingOperation(
            packageCoordinate,
            operation,
            None
          )
        )
      )
    )
    when(
      processorViewMock.failure(
        packageCoordinate,
        operation,
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
  object SucceedingInstaller extends Installer {
    def apply(uri: Uri, pkg: universe.v3.model.PackageDefinition): Future[Unit] = Future.Done
  }

  object SucceedingUniverseInstaller extends UniverseInstaller {
    def apply(pkg: universe.v3.model.PackageDefinition): Future[Unit] = Future.Done
  }

  object SucceedingUninstaller extends Uninstaller {
    def apply(
      pc: rpc.v1.model.PackageCoordinate,
      pkg: Option[universe.v3.model.PackageDefinition]
    ): Future[Unit] = Future.Done
  }

  val installerError = new IllegalArgumentException("Install failed")
  object FailingInstaller extends Installer {
    def apply(uri: Uri, pkg: universe.v3.model.PackageDefinition): Future[Unit] = Future.exception(
      installerError
    )
  }

  val universeInstallerError = new IllegalArgumentException("Universe install failed")
  object FailingUniverseInstaller extends UniverseInstaller {
    def apply(pkg: universe.v3.model.PackageDefinition): Future[Unit] = Future.exception(
      universeInstallerError
    )
  }

  val uninstallerError = new IllegalArgumentException("Uninstall failed")
  object FailingUninstaller extends Uninstaller {
    def apply(
      pc: rpc.v1.model.PackageCoordinate,
      pkg: Option[universe.v3.model.PackageDefinition]
    ): Future[Unit] = Future.exception(
      uninstallerError
    )
  }
}
