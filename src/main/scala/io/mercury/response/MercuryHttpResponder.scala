package io.mercury.response

import io.netty.channel.ChannelHandlerContext

abstract class MercuryHttpResponder {

  def complete: (ChannelHandlerContext => Any)
}
