package io.mercury.response

import io.netty.channel.ChannelHandlerContext

abstract class MercuryHttpResponder {

  def complete(ctx: ChannelHandlerContext)
}
