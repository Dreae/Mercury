package io.mercury.server

import io.mercury.config.MercuryConfig
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.stream.ChunkedWriteHandler

class MercuryHttpServerInitializer() extends ChannelInitializer[SocketChannel]{

  override def initChannel(ch: SocketChannel) = {
    val p = ch.pipeline()
    p.addLast("codec", new MercuryHttpCodec())
    p.addLast("aggregator", new HttpObjectAggregator(MercuryConfig().aggregateSize))
    p.addLast("writer", new ChunkedWriteHandler())
    p.addLast("handler", new MercuryServerHandler())
  }
}
