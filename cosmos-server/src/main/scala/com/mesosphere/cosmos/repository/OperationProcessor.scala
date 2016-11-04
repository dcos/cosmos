package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.circe.Encoders.exceptionErrorResponse
import com.mesosphere.cosmos.storage.installqueue.Install
import com.mesosphere.cosmos.storage.installqueue.PendingOperation
import com.mesosphere.cosmos.storage.installqueue.ProcessorView
import com.mesosphere.cosmos.storage.installqueue.Uninstall
import com.mesosphere.cosmos.storage.installqueue.UniverseInstall
import com.twitter.util.Future
import com.twitter.util.Return
import com.twitter.util.Throw

final class OperationProcessor private (
  processorView: ProcessorView,
  installer: Installer,
  universeInstaller: UniverseInstaller,
  uninstaller: Uninstaller
) {

  def apply(): Future[Unit] = {
    processorView.next().flatMap {
      case Some(pending) =>
        applyOperation(pending).transform {
          case Return(()) =>
            processorView.success(pending.packageCoordinate)
          case Throw(err) =>
            processorView.failure(
              pending.packageCoordinate,
              pending.operation,
              exceptionErrorResponse(err)
            )
        }
      case None =>
        // Nothing to do. Just return.
        Future.Done
    }
  }

  private[this] def applyOperation(pending: PendingOperation): Future[Unit] = {
    pending.operation match {
      case Install(uri, pkg) => installer(uri, pkg)
      case UniverseInstall(pkg) => universeInstaller(pkg)
      case Uninstall(pkg) => uninstaller(pending.packageCoordinate, pkg)
    }
  }
}

object OperationProcessor {
  def apply(
    processorView: ProcessorView,
    installer: Installer,
    universeInstaller: UniverseInstaller,
    uninstaller: Uninstaller
  ): OperationProcessor = {
    new OperationProcessor(processorView, installer, universeInstaller, uninstaller)
  }
}
