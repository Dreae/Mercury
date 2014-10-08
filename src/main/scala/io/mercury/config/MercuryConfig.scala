package io.mercury.config

import java.io.File
import collection.JavaConverters._
import java.util.Map.Entry
import java.util.regex.Pattern

import com.typesafe.config._
import io.mercury.exceptions.ConfigParsingException
import io.mercury.exceptions.http.NotFoundException

class MercuryConfig(private val conf: Config) {
  val server = conf.getObject("server").toConfig.withFallback(ConfigFactory.load("server"))
  val defSite = ConfigFactory.load("site")
  val aggregateSize = this.parseAggregateSize(server.getString("max_file_upload"))
  val sites = server.getList("sites").toArray.map {
    case obj: ConfigObject =>
      val siteConf = obj.toConfig.withFallback(defSite)
      val locations = siteConf.getObject("locations").entrySet().toArray
      val parsed = locations.toArray.map{
        case entry: Entry[_,_] =>
          parseLocRegex(entry.getKey.asInstanceOf[String], entry.getValue.asInstanceOf[ConfigValue])
      }
      siteConf.withValue("locations", ConfigValueFactory.fromMap(Map(parsed:_*).asJava))
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
      (obj.getValue.asInstanceOf[ConfigValue].unwrapped.asInstanceOf[String].split(" ").toList, obj.getKey.toString.replace("\"",""))
  }.toList

  val defaultType = server.getString("default_type")

  val listen_threads = conf.getInt("listen_threads")
  val worker_threads = conf.getInt("worker_threads")

  def site(reqSite: String) = {
    val iSites = sites.filter(_.getString("name") == reqSite)
    if(iSites.length == 0) {
      throw new NotFoundException
    } else if(iSites.length > 1) {
      //TODO: Log error
    }
    iSites(0)
  }

  private def parseLocRegex(location: String, locDef: ConfigValue): (String, AnyRef) = {
    val locSplit = location.split(" ")
    if(locSplit(1).contains("~")){
      (
        location,
        configObject2Map(locDef.asInstanceOf[ConfigObject].withValue("__regex__", ConfigValueFactory.fromAnyRef(Pattern.compile(locSplit(2)))))
      )
    } else {
      (location, configObject2Map(locDef.asInstanceOf[ConfigObject]))
    }
  }

  private def configObject2Map(obj: ConfigObject) = {
    val entrySet = obj.entrySet.toArray.map{
      case entry: Entry[_,_] =>
        (entry.getKey.asInstanceOf[String], entry.getValue.asInstanceOf[ConfigValue].unwrapped())
    }
    Map(entrySet:_*).asJava
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
  var config: MercuryConfig = null

  def apply() = config

  def parseConfig(confFile: String) = {
    val default = ConfigFactory.load()
    val conf = ConfigFactory.parseFile(new File("config/mercury.conf"))
    config = new MercuryConfig(conf.withFallback(default))
    config
  }
}