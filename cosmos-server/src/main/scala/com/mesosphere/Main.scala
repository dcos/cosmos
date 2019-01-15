package com.mesosphere
import io.circe.syntax._

object Main {
  import io.circe.Decoder
  import io.circe.Encoder
  import io.circe.generic.semiauto.deriveEncoder
  import io.circe.generic.semiauto.deriveDecoder

  /**
   * Conforms to: https://universe.mesosphere.com/v3/schema/repo
   */
  case class Repo(packages: List[String])

  object Repo {

    //new String("asdak®".getBytes(Charset.forName("US-ASCII")))
    implicit val stringEncoder: Decoder[String] = Decoder.decodeString.map(x => org.apache.commons.lang.StringEscapeUtils.escapeJava(x))
    implicit val decodeRepository: Decoder[Repo] = deriveDecoder[Repo]
    implicit val encodeRepository: Encoder[Repo] = deriveEncoder[Repo]
  }

  def main(args: Array[String]): Unit = {
    print(org.apache.commons.lang.StringEscapeUtils.escapeJava("asdak®"))
    val intsJson = List(1, 2, 3).asJson
    print(intsJson)
    val ii = Repo(List("asdak®", "asdasd", "asdasd")).asJson
    print(ii.as[Repo])
  }
}
