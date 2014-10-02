package io.mercury.server

import com.typesafe.config.{ConfigValue, Config, ConfigObject}
import io.mercury.exceptions.ConfigParsingException
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder, HttpObjectAggregator, HttpServerCodec}
import io.netty.handler.stream.ChunkedWriteHandler
import java.util.Map.Entry

class MercuryHttpServerInitializer(conf: Config) extends ChannelInitializer[SocketChannel]{
  val server = conf.getObject("server").toConfig
  val aggregateSize = this.parseAggregateSize(server.getString("max_file_upload"))
  val sites = server.getList("sites").toArray.map {
    case obj: ConfigObject =>
      obj.toConfig
    case _ =>
      throw new ConfigParsingException("Config is incorrectly formatted")
  }
  val token = server.getString("server_tokens") match {
    case "product" =>
      "Mercury"
    case other =>
      other
  }

  val mimeTypes = conf.getObject("types").toConfig.entrySet.toArray.map {
    case obj: Entry[_, _] =>
      (obj.getValue.asInstanceOf[ConfigValue].unwrapped.asInstanceOf[String].split(" ").toList, obj.getKey.toString.replace("\"", ""))
  }.toList

  val defaultType = server.getString("default_type")

  val handlerConf = Map(
    "tokens" -> token,
    "types" -> mimeTypes,
    "default_type" -> defaultType
  )

  override def initChannel(ch: SocketChannel) = {
    val p = ch.pipeline()
    p.addLast("codec", new MercuryHttpCodec(handlerConf))
    p.addLast("aggregator", new HttpObjectAggregator(aggregateSize))
    p.addLast("writer", new ChunkedWriteHandler())
    p.addLast("handler", new MercuryServerHandler(sites, handlerConf))
  }

  implicit class IntToBytes(x: Int) {
    def Kibibytes = 1024 * x
    def Mebibytes = 1024.Kibibytes * x
    def Gibibytes = 1024.Mebibytes * x
  }

  private def parseAggregateSize(size: String) = {
    size.last.toLower match {
      case 'k' =>
        size.dropRight(1).toInt.Kibibytes
      case 'm' =>
        size.dropRight(1).toInt.Mebibytes
      case 'g' =>
        size.dropRight(1).toInt.Gibibytes
    }
  }
}
