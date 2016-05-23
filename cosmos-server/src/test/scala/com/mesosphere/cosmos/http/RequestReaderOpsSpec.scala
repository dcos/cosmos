package com.mesosphere.cosmos.http

import cats.data.Xor
import com.mesosphere.cosmos.UnitSpec
import com.mesosphere.cosmos.http.FinchExtensions.RequestReaderOps
import com.twitter.finagle.http.Request
import com.twitter.util.{Await, Future, Return, Throw}
import io.finch.Error.NotValid
import io.finch.{RequestReader, items}
import io.finch.items.RequestItem

final class RequestReaderOpsSpec extends UnitSpec {

  import RequestReaderOpsSpec._

  "RequestReaderOps.convert[B](A => String Xor B) should" - {

    "forward the success value from the function argument" in {
      val result = RequestReader.value(true).convert(exampleFn)(DummyRequest)
      assertResult(Return(()))(Await.result(result.liftToTry))
    }

    "wrap the failure message from the function argument in an exception" in {
      val result = RequestReader.value(false).convert(exampleFn)(DummyRequest)
      val Throw(NotValid(_, rule)) = Await.result(result.liftToTry)
      assertResult("failure")(rule)
    }

    "include the item from the base reader in failures" in {
      val baseReader = new RequestReader[Boolean] {
        val item: RequestItem = items.BodyItem
        def apply(req: Request): Future[Boolean] = Future.value(false)
      }

      val result = baseReader.convert(exampleFn)(DummyRequest)
      val Throw(NotValid(item, _)) = Await.result(result.liftToTry)
      assertResult(items.BodyItem)(item)
    }
  }

}

object RequestReaderOpsSpec {
  val DummyRequest: Request = Request("http://some.host")
  private def exampleFn(a: Boolean): String Xor Unit = if (a) Xor.Right(()) else Xor.Left("failure")
}
