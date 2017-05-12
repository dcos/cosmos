package io.github.benwhitehead

import com.mesosphere.cosmos.Flaggables._
import com.twitter.app.GlobalFlag
import java.net.InetSocketAddress
import java.nio.file.Path

package finch {
  // scalastyle:off object.name
  object httpInterface extends GlobalFlag[Option[InetSocketAddress]](
    Some(new InetSocketAddress("127.0.0.1", 7070)), //scalastyle:ignore magic.number
    "The TCP Interface and port for the http server {[<hostname/ip>]:port}. (Set to " +
    "empty value to disable)"
  )

  object httpsInterface extends GlobalFlag[InetSocketAddress](
    "The TCP Interface and port for the https server {[<hostname/ip>]:port}. Requires " +
    s"-${certificatePath.name} and -${keyPath.name} to be set. (Set to empty value to disable)"
  )

  object certificatePath extends GlobalFlag[Path](
    "Path to PEM format SSL certificate file"
  )

  object keyPath extends GlobalFlag[Path](
    "Path to SSL Key file"
  )
  // scalastyle:on object.name
}
