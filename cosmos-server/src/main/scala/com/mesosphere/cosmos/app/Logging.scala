package com.mesosphere.cosmos.app

import com.twitter.app.App
import java.util.logging.LogManager
import java.util.logging.Level
import org.slf4j.bridge.SLF4JBridgeHandler

trait Logging { self: App =>
  init {
    // Turn off Java util logging so that slf4j can configure it
    LogManager.getLogManager.getLogger("").getHandlers.toList.foreach { l =>
      l.setLevel(Level.OFF)
    }
    org.slf4j.LoggerFactory.getLogger("slf4j-logging").debug("Installing SLF4JLogging")
    SLF4JBridgeHandler.install()
  }

  onExit {
    org.slf4j.LoggerFactory.getLogger("slf4j-logging").debug("Uninstalling SLF4JLogging")
    SLF4JBridgeHandler.uninstall()
  }
}
