package io.mercury.response

import io.mercury.server.MercuryIO
import io.netty.handler.codec.http._

class ReturnDirectiveHandler(code: Int, status: String, req: HttpRequest) extends MercuryHttpResponder {

  override def complete() = {
    req.headers.set("Connection", "close")
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(code, status))
    val IO = new MercuryIO()
    IO >> response
  }

}
