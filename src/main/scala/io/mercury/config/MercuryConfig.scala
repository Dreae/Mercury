package io.mercury.config

import java.io.{File, RandomAccessFile}
import java.nio.channels.FileChannel
import java.util.Map.Entry

import com.typesafe.config._
import io.mercury.config.MercuryConfig.SiteConfig
import io.mercury.exceptions.http.NotFoundException
import io.mercury.logging.MercuryLogger

import scala.util.matching.Regex

class MercuryConfig(private val conf: Config) {
  val server = conf.getObject("server").toConfig.withFallback(ConfigFactory.load("server"))
  val aggregateSize = this.parseAggregateSize(server.getString("max_file_upload"))

  val access_log = MercuryConfig.getFileChannel(conf, "access_log").getOrElse(new RandomAccessFile("logs/access.log", "rw").getChannel)
  val error_log = MercuryConfig.getFileChannel(conf, "error_log").getOrElse(new RandomAccessFile("logs/error.log", "rw").getChannel)

  val defaultSite = new SiteConfig(ConfigFactory.load("site"), access_log, error_log)

  val sites = server.getList("sites").toArray.map{ case obj: ConfigObject => new SiteConfig(obj.toConfig, access_log, error_log) }

  val token = server.getString("server_tokens") match {
    case "product" =>
      "Mercury"
    case other =>
      other
  }

  val mimeTypes = conf.getObject("types").toConfig.entrySet.toArray.map {
    case obj: Entry[_, _] =>
      (obj.getValue.asInstanceOf[ConfigValue].unwrapped.asInstanceOf[String].split(" ").toList, obj.getKey.toString.replace("\"",""))
  }.toList

  val defaultType = server.getString("default_type")

  val listen_threads = conf.getInt("listen_threads")
  val worker_threads = conf.getInt("worker_threads")

  def site(reqSite: String) = {
    val iSites = sites.filter((site) => if(site.name.isDefined) site.name.get == reqSite else reqSite == "127.0.0.1")
    if(iSites.length == 0) {
      throw new NotFoundException
    } else if(iSites.length > 1) {
      //TODO: Log error
    }
    iSites(0)
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

object MercuryConfig {
  type SiteConfigType = (Option[Map[String, LocationConfig]],
                          Option[String],
                          Option[List[String]],
                          Option[String],
                          Option[ReturnDirective],
                          MercuryLogger,
                          Option[Map[String, String]])
  case class SiteConfig(
                         locations: Option[Map[String, LocationConfig]],
                         name: Option[String],
                         indices: Option[List[String]],
                         root: Option[String],
                         returnDirective: Option[ReturnDirective],
                         logger: MercuryLogger,
                         headers: Option[Map[String, String]]) {
    def this(args: SiteConfigType) = this(args._1, args._2, args._3, args._4, args._5, args._6, args._7)

    def this(config: Config, defAccessLog: FileChannel, defErrorLog: FileChannel) = this(
      {
        val access_log = getFileChannel(config, "access_log", create = true).getOrElse(defAccessLog)
        val error_log = getFileChannel(config, "error_log", create = true).getOrElse(defErrorLog)
        (
          getLocationMap(config, access_log, error_log),
          getString(config, "name"),
          getIndexList(config),
          getString(config, "root"),
          getReturnDirective(config),
          new MercuryLogger(access_log, error_log),
          getHeaderMap(config)
        )
      }
    )
  }
  case class LocationConfig(regex: Option[Regex], site: SiteConfig) {
    def this(name: String, config: Config, defAccessLog: FileChannel, defErrorLog: FileChannel) = this(
      {
        val locSplit =  name.split(" ")
        if(locSplit(1).contains("~")) {
          Some(locSplit(2).r)
        } else {
          None
        }
      }, new SiteConfig(config, defAccessLog, defErrorLog)
    )
  }
  case class ReturnDirective(code: Int, status: String)

  var config: MercuryConfig = null

  def apply() = config

  def parseConfig(confFile: String) = {
    val default = ConfigFactory.load()
    val conf = ConfigFactory.parseFile(new File("config/mercury.conf"))
    config = new MercuryConfig(conf.withFallback(default))
    config
  }

  def getInt(config: Config, path: String) = {
    if(!config.hasPath(path)) None
    else Some(config.getInt(path))
  }

  def getBoolean(config: Config, path: String) = {
    if(!config.hasPath(path)) None
    else Some(config.getBoolean(path))
  }

  def getString(config: Config, path: String)= {
    if(!config.hasPath(path)) None
    else Some(config.getString(path))
  }

  def getReturnDirective(config: Config) = {
    if(!config.hasPath("return")) None
    else {
      val split = config.getString("return").split(" ", 2)
      Some(ReturnDirective(split(0).toInt, split(1)))
    }
  }

  def getLocationMap(config: Config, access_log: FileChannel, error_log: FileChannel) = {
    if(!config.hasPath("locations")) None
    else {
      val locations = config.getObject("locations")
      Some(Map(locations.keySet().toArray.map(_.asInstanceOf[String]).map{
        (key) => (key, new LocationConfig(key, locations.get(key).asInstanceOf[ConfigObject].toConfig, access_log, error_log))
      }:_*))
    }
  }

  def getHeaderMap(config: Config) = {
    if(!config.hasPath("headers")) None
    else {
      val headers = config.getObject("headers")
      Some(Map(headers.keySet().toArray.map(_.asInstanceOf[String]).map {
        (key) => (key, headers.get(key).unwrapped.asInstanceOf[String])
      }:_*))
    }
  }

  def getIndexList(config: Config) = {
    if(!config.hasPath("index")) None
    else {
      Some(config.getList("index").toArray.map{ case value: ConfigValue => value.unwrapped.asInstanceOf[String] }.toList)
    }
  }

  def getFileChannel(config: Config, path: String, create: Boolean = false): Option[FileChannel] = {
    if(!config.hasPath(path)) None
    else {
      val file = new File(config.getString(path))
      if(!file.exists() && create) {
        file.mkdirs()
        file.createNewFile()
      }
      Some(new RandomAccessFile(file.getAbsolutePath, "rw").getChannel)
    }
  }
}