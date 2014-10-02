package io.mercury.server

import io.netty.handler.codec.http.HttpRequestDecoder

class MercuryHttpRequestDecoder(conf: Map[String, AnyRef]) extends HttpRequestDecoder(4096, 8192, 8192)
