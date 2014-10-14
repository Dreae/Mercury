package io.mercury.config

import java.io.File
import java.util.Map.Entry
import java.util.regex.Pattern

import com.typesafe.config._
import io.mercury.config.MercuryConfig.SiteConfig
import io.mercury.exceptions.http.NotFoundException

class MercuryConfig(private val conf: Config) {
  val defaultSite = new SiteConfig(ConfigFactory.load("site"))

  val server = conf.getObject("server").toConfig.withFallback(ConfigFactory.load("server"))
  val aggregateSize = this.parseAggregateSize(server.getString("max_file_upload"))
  val sites = server.getList("sites").toArray.map{ case obj: ConfigObject => new SiteConfig(obj.toConfig) }

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
  case class SiteConfig(
                         locations: Option[Map[String, LocationConfig]],
                         name: Option[String],
                         indices: Option[List[String]],
                         root: Option[String],
                         returnDirective: Option[ReturnDirective]) {
    def this(config: Config) = this(
      getLocationMap(config),
      getString(config, "name"),
      getIndexList(config),
      getString(config, "root"),
      getReturnDirective(config)
    )
  }
  case class LocationConfig(regex: Option[Pattern], site: SiteConfig) {
    def this(name: String, config: Config) = this(
      {
        val locSplit =  name.split(" ")
        if(locSplit(1).contains("~")) {
          Some(Pattern.compile(locSplit(2)))
        } else {
          None
        }
      }, new SiteConfig(config)
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

  def getLocationMap(config: Config) = {
    if(!config.hasPath("locations")) None
    else {
      val locations = config.getObject("locations")
      val locConfig = locations.toConfig
      Some(Map(locations.keySet().toArray.map(_.asInstanceOf[String]).map{
        (key) => (key, new LocationConfig(key, locConfig.getObject(key).toConfig))
      }:_*))
    }
  }

  def getIndexList(config: Config) = {
    if(!config.hasPath("index")) None
    else {
      Some(config.getList("index").toArray.map{ case value: ConfigValue => value.unwrapped.asInstanceOf[String] }.toList)
    }
  }
}