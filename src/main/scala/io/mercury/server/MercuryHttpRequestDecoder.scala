package io.mercury.server

import io.netty.handler.codec.http.HttpRequestDecoder

class MercuryHttpRequestDecoder() extends HttpRequestDecoder(4096, 8192, 8192)
