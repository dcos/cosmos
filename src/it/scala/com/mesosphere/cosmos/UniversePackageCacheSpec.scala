package com.mesosphere.cosmos

import java.nio.file.NoSuchFileException

import com.netaporter.uri.Uri
import org.scalatest.FreeSpec

final class UniversePackageCacheSpec extends FreeSpec {

  import UniversePackageCacheSpec._

  "A UniversePackageCache" - {
    "when close() is called before the bundle has been downloaded" - {
      "should not throw an exception" in {
        IntegrationTests.withTempDirectory { dataDir =>
          val cache = UniversePackageCache(CacheName, CacheUri, dataDir)

          cache.close()
        }
      }
    }
  }

}

object UniversePackageCacheSpec {

  private val CacheName: String = "Universe"
  private val CacheUri: Uri =
    Uri.parse("https://github.com/mesosphere/universe/archive/cli-test-4.zip")

}
