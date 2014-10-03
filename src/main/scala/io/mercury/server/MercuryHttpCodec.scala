package io.mercury.server

import io.netty.channel.CombinedChannelDuplexHandler

class MercuryHttpCodec()
  extends CombinedChannelDuplexHandler[MercuryHttpRequestDecoder, MercuryHttpResponseEncoder](
    new MercuryHttpRequestDecoder(),
    new MercuryHttpResponseEncoder()
  )
