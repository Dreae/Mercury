package io.mercury.server

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpResponse, HttpResponseEncoder}

class MercuryHttpResponseEncoder(conf: Map[String, AnyRef]) extends HttpResponseEncoder {
  override def encode(ctx: ChannelHandlerContext, msg: AnyRef, out: java.util.List[AnyRef]) = {
    msg match {
      case response: HttpResponse =>
        response.headers().set("Server", conf("tokens"))
      case _ => Unit
    }
    super.encode(ctx, msg, out)
  }
}
