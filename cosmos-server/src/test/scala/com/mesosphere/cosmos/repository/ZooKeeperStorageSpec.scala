package com.mesosphere.cosmos.repository

import cats.data.Ior
import com.mesosphere.cosmos.model.PackageRepository
import com.netaporter.uri.dsl._
import org.scalatest.FreeSpec

class ZooKeeperStorageSpec extends FreeSpec {

  val real = PackageRepository("real", "http://real.real")
  val fake = PackageRepository("fake", "http://fake.fake")
  val realFake = PackageRepository("real", "http://fake.fake")

  "ZooKeeperStorage" - {
    "getPredicate should" - {
      "work when" - {
        "only name is specified" in {
          val predicate = ZooKeeperStorage.getPredicate(Ior.Left("real"))
          assert(predicate(real))
          assert(!predicate(fake))
          assert(predicate(realFake))
        }
        "only uri is specified" in {
          val predicate = ZooKeeperStorage.getPredicate(Ior.Right("http://real.real"))
          assert(predicate(real))
          assert(!predicate(fake))
          assert(!predicate(realFake))
        }
        "both name and uri are specified" in {
          val predicate = ZooKeeperStorage.getPredicate(Ior.Both("real", "http://real.real"))
          assert(predicate(real))
          assert(!predicate(fake))
          assert(!predicate(realFake))
        }
      }
    }
  }
}
