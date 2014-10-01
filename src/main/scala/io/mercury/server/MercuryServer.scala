package io.mercury.server

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel

class MercuryServer {
  def run(conf: Config) = {
    val bossGroup = new NioEventLoopGroup(conf.getInt("listen_threads"))
    val workGroup = new NioEventLoopGroup(conf.getInt("worker_threads"))

    val server = conf.getObject("server").toConfig

    try {
      val bootstrap = new ServerBootstrap()
      bootstrap.group(bossGroup, workGroup)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new MercuryHttpServerInitializer(conf))

      val ch = bootstrap.bind(server.getInt("listen")).sync().channel()
      ch.closeFuture().sync()
    } finally {
      bossGroup.shutdownGracefully()
      workGroup.shutdownGracefully()
    }
  }
}

object MercuryServer extends App {
  private val default = ConfigFactory.load()
  private val conf = ConfigFactory.parseFile(new File("config/mercury.conf"))
  new MercuryServer().run(conf.withFallback(default))
}
