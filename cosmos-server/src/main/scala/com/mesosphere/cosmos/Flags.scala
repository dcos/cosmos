package com.mesosphere.cosmos

import com.mesosphere.cosmos.Flaggables._
import com.mesosphere.cosmos.model.ZooKeeperUri
import com.netaporter.uri.Uri
import com.twitter.app.GlobalFlag
import com.twitter.conversions.storage._
import com.twitter.util.StorageUnit

// scalastyle:off object.name
object dcosUri extends GlobalFlag[Uri](
  s"The URI where the DCOS Admin Router is located. If this flag is set, " +
    s"${mesosMasterUri.name} and ${marathonUri.name} will be ignored"
)

object adminRouterUri extends GlobalFlag[Uri](
  Uri.parse("http://master.mesos"),
  "The URI where AdminRouter can be found"
)

object marathonUri extends GlobalFlag[Uri](
  Uri.parse("http://master.mesos:8080"),
  "The URI where marathon can be found"
)

object mesosMasterUri extends GlobalFlag[Uri](
  Uri.parse("http://leader.mesos:5050"),
  "The URI where the leading Mesos master can be found"
)

object zookeeperUri extends GlobalFlag[ZooKeeperUri](
  ZooKeeperUri.parse("zk://127.0.0.1:2181/cosmos").get(),
  "The ZooKeeper connection string"
)

object packageStorageUri extends GlobalFlag[Option[ObjectStorageUri]](
  None,
  "The URI where packages are stored"
)

object stagedPackageStorageUri extends GlobalFlag[Option[ObjectStorageUri]](
  None,
  "The URI where packages are staged before permanent storage"
)

object maxClientResponseSize extends GlobalFlag[StorageUnit](
  5.megabytes,
  "Maximum size for the response for requests initiated by Cosmos in Megabytes"
)

// scalastyle:on object.name
