package com.mesosphere.cosmos.repository

import com.mesosphere.cosmos.model.PackageRepository
import com.mesosphere.cosmos.repository.DefaultRepositories._
import com.netaporter.uri.dsl._
import com.twitter.util.Return
import io.circe.ParsingFailure
import org.scalatest.FreeSpec

class DefaultRepositoriesSpec extends FreeSpec {

  val repo1 = PackageRepository("repo1", "http://someplace/repo1")
  val repo2 = PackageRepository("repo2", "http://someplace/repo2")
  val repo3 = PackageRepository("repo3", "http://someplace/repo3")

  "DefaultRepositories for" - {

    "empty-list.json should" - {
      val emptyList = new DefaultRepositories("/com/mesosphere/cosmos/repository/empty-list.json")

      "property load" in {
        val expected = List()
        assertResult(expected)(emptyList.getOrThrow)
      }

    }

    "repos.json should" - {
      val repos = new DefaultRepositories("/com/mesosphere/cosmos/repository/repos.json")

      "property load" in {
        val expected = List(repo1, repo2, repo3)
        assertResult(expected)(repos.getOrThrow)
      }

    }

    "malformed.json should" - {
      val malformed = new DefaultRepositories("/com/mesosphere/cosmos/repository/malformed.json")

      "collect an error" in {
        val Return(xor) = malformed.get()
        assert(xor.isLeft)
      }

      "throw an error for a getOrThrow()" in {
        try { val _ = malformed.getOrThrow }
        catch {
          case ParsingFailure(msg, cause) => // expected
        }
      }

      "return a default for a getOrElse()" in {
        val actual = malformed.getOrElse(List(repo1))
        val expected = List(repo1)
        assertResult(expected)(actual)
      }

    }

    "non-existent resource should" - {

      "throw IllegalStateException" in {
        try {
          val _ = new DefaultRepositories("/does/not/exist").getOrThrow
        } catch {
          case ies: IllegalStateException => // expected
        }
      }

    }

    "default-repositories.json should load" in {
      val defaults = DefaultRepositories().getOrThrow
      assertResult(List(PackageRepository("default", "http://default")))(defaults)
    }

  }

}
