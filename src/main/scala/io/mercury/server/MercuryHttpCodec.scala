package io.mercury.server

import io.netty.channel.CombinedChannelDuplexHandler

class MercuryHttpCodec(private val conf: Map[String, AnyRef])
  extends CombinedChannelDuplexHandler[MercuryHttpRequestDecoder, MercuryHttpResponseEncoder](
    new MercuryHttpRequestDecoder(conf),
    new MercuryHttpResponseEncoder(conf)
  )