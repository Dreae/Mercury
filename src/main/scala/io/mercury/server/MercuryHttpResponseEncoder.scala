package io.mercury.server

import io.mercury.config.MercuryConfig
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpResponse, HttpResponseEncoder}

class MercuryHttpResponseEncoder() extends HttpResponseEncoder {
  override def encode(ctx: ChannelHandlerContext, msg: AnyRef, out: java.util.List[AnyRef]) = {
    msg match {
      case response: HttpResponse =>
        response.headers().set("Server", MercuryConfig().token)
      case _ => Unit
    }
    super.encode(ctx, msg, out)
  }
}
