package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.RepoNameOrUriMissing
import com.mesosphere.cosmos.model.PackageRepository
import com.netaporter.uri.dsl._
import com.twitter.util.{Throw, Return}
import org.scalatest.FreeSpec

class ZooKeeperStorageSpec extends FreeSpec {

  val real = PackageRepository("real", "http://real.real")
  val fake = PackageRepository("fake", "http://fake.fake")
  val realFake = PackageRepository("real", "http://fake.fake")

  "ZooKeeperStorage" - {
    "getPredicate should" - {
      "work when" - {
        "only name is specified" in {
          val Return(predicate) = ZooKeeperStorage.getPredicate(Some("real"), None)
          assert(predicate(real))
          assert(!predicate(fake))
          assert(predicate(realFake))
        }
        "only uri is specified" in {
          val Return(predicate) = ZooKeeperStorage.getPredicate(None, Some("http://real.real"))
          assert(predicate(real))
          assert(!predicate(fake))
          assert(!predicate(realFake))
        }
        "both name and uri are specified" in {
          val Return(predicate) = ZooKeeperStorage.getPredicate(Some("real"), Some("http://real.real"))
          assert(predicate(real))
          assert(!predicate(fake))
          assert(!predicate(realFake))
        }
      }
      "throw an error when neither name or uri are specified" in {
          ZooKeeperStorage.getPredicate(None, None) match {
            case Throw(e: RepoNameOrUriMissing) => //pass
            case _ => fail()
          }
      }
    }
  }
}
