package com.mesosphere.cosmos.finagle

import java.io.InputStream

import com.twitter.finagle.{Http, Service, http}
import com.twitter.io.{Buf, Reader}
import com.twitter.util.{Await, Future}
import org.scalatest.FreeSpec

final class FinagleStreamingSpec extends FreeSpec {

  import FinagleStreamingSpec._

  "A Finagle service can read a streamed request body that cannot fit on the heap" in {
    val host = "127.0.0.1"
    val port = ":10101"
    val packageSizeHeader = "Dcos-Package-Content-Length"

    val service = new Service[http.Request, http.Response] {
      def apply(req: http.Request): Future[http.Response] = {
        val packageSize = req.headerMap.get(packageSizeHeader) match {
          case Some(size) => size.toLong
          case _ => Long.MaxValue
        }

        countBytes(req.reader, packageSize).map { case (reqBodySize, maxChunkSize) =>
          val resBody = Reader.fromBuf(Buf.Utf8(s"$reqBodySize $maxChunkSize"))
          http.Response(req.version, http.Status.Ok, resBody)
        }
      }
    }

    val server = Http.server.withStreaming(true).serve(port, service)

    try {
      val reqBodySize = 10 * Runtime.getRuntime.maxMemory()
      val readSize: Long = 200L * 1000L * 1000L

      val values = Iterator.iterate(0L)(_ + 1).takeWhile(_ < reqBodySize)
      val bytes = iteratorToInputStream(values.map(v => (v & 0xFF).toByte))
      val reqBody = Reader.fromStream(bytes)

      val request = http.Request(http.Version.Http11, http.Method.Post, "/", reqBody)
      request.host = host
      request.headerMap.add(packageSizeHeader, readSize.toString)

      val client = Http.newService(host + port)
      val response = Await.result(client(request))

      assertResult(http.Status.Ok)(response.status)

      val Array(reqBodyRead, maxChunkSize) = response.contentString.split(' ').map(_.toLong)
      assertResult(readSize)(reqBodyRead)
      assert(maxChunkSize < 10 * 1000 * 1000)
    } finally {
      Await.result(server.close())
    }
  }

}

object FinagleStreamingSpec {

  def countBytes(reader: Reader, limit: Long): Future[(Long, Int)] = {
    def loop(runningTotal: Long, maxChunkSize: Int): Future[(Long, Int)] = {
      val remaining = limit - runningTotal

      if (remaining <= 0) {
        Future.value((runningTotal, maxChunkSize))
      } else {
        reader.read(math.min(remaining, Int.MaxValue).toInt).flatMap {
          case Some(bytes) =>
            val chunkSize = bytes.length
            loop(runningTotal + chunkSize, math.max(maxChunkSize, chunkSize))
          case _ => Future.value((runningTotal, maxChunkSize))
        }
      }
    }

    loop(0, 0)
  }

  def iteratorToInputStream(iterator: Iterator[Byte]): InputStream = {
    new InputStream {
      override def available(): Int = if (iterator.hasDefiniteSize) iterator.size else Int.MaxValue
      override def read(): Int = if (iterator.hasNext) iterator.next() & 0xFF else -1
    }
  }

}
