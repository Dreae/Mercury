package io.mercury.server

import io.mercury.config.MercuryConfig
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel

class MercuryServer {
  def run() = {
    val bossGroup = new NioEventLoopGroup(MercuryConfig().listen_threads)
    val workGroup = new NioEventLoopGroup(MercuryConfig().worker_threads)

    val server = MercuryConfig().server

    try {
      val bootstrap = new ServerBootstrap()
      bootstrap.group(bossGroup, workGroup)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new MercuryHttpServerInitializer())

      val ch = bootstrap.bind(server.getInt("listen")).sync().channel()
      ch.closeFuture().sync()
    } finally {
      bossGroup.shutdownGracefully()
      workGroup.shutdownGracefully()
    }
  }
}

object MercuryServer extends App {
  MercuryConfig.parseConfig("config/mercury.conf")
  new MercuryServer().run()
}
