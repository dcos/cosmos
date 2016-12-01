package com.mesosphere.cosmos.rpc.v1.circe

import com.mesosphere.cosmos.finch.DispatchingMediaTypedBodyParser
import com.mesosphere.cosmos.rpc.MediaTypes
import com.mesosphere.cosmos.rpc.v1.circe.MediaTypedRequestDecoders._
import com.mesosphere.cosmos.rpc.v1.model.AddRequest
import com.mesosphere.cosmos.rpc.v1.model.UploadAddRequest
import com.mesosphere.universe.{MediaTypes => UMediaTypes}
import com.twitter.util.Return

object MediaTypedBodyParsers {

  implicit val packageAddBodyParser: DispatchingMediaTypedBodyParser[AddRequest] = {
    DispatchingMediaTypedBodyParser(
      MediaTypes.AddRequest -> DispatchingMediaTypedBodyParser.parserFromDecoder[AddRequest],
      UMediaTypes.PackageZip -> (bodyBytes => Return(UploadAddRequest(bodyBytes)))
    )
  }

}
