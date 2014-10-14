package io.mercury.response

import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext}
import io.netty.handler.codec.http._

class ReturnDirectiveHandler(code: Int, status: String) extends MercuryHttpResponder {

  override def complete(ctx: ChannelHandlerContext) = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(code, status))
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }

}
