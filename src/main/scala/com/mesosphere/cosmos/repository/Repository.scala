package com.mesosphere.cosmos.repository

import com.netaporter.uri.Uri

/** Identifies a structured Zip archive of packages, located at `source` (e.g., DCOS Universe). */
private trait Repository {
  val name: String
  val source: Uri
}
