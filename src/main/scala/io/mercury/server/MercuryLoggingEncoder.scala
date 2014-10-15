package io.mercury.server

import java.util

import io.mercury.logging.MercuryLogger.MercuryLoggingMonad
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageEncoder

class MercuryLoggingEncoder extends MessageToMessageEncoder[MercuryLoggingMonad]{
  override def encode(ctx: ChannelHandlerContext, msg: MercuryLoggingMonad, out: util.List[AnyRef]): Unit = {
    msg.site.logger.logAccess(ctx.channel().remoteAddress().toString, msg.req, msg.resp)
    out.add(msg.resp)
  }
}
