package io.mercury.server

import java.net.URLDecoder
import java.util.Map.Entry
import java.util.regex.Pattern
import com.typesafe.config.{ConfigObject, ConfigValue, Config}
import io.mercury.config.MercuryConfig
import io.mercury.exceptions.UnknownRequestHandlerException
import io.netty.handler.codec.http.FullHttpRequest

import scala.annotation.tailrec
import scala.util.Try

object RequestHandler {
  abstract class RequestHandlerType
  case class StaticContent(root: String) extends RequestHandlerType
  case class ProxyPass(host: String) extends RequestHandlerType

  @tailrec
  def getRequestHandlerType(site: Config, req: FullHttpRequest): RequestHandlerType = {
    val locObj = site.getObject("locations")
    val locations = locObj.entrySet().toArray.map(_.asInstanceOf[Entry[String, ConfigValue]])
    val location = parseLocations(locations,  URLDecoder.decode(req.getUri, "utf-8"))
    if(location != null) {
      req.setUri(location._1)
      getRequestHandlerType(
        locObj.toConfig
        .getObject(location._2).toConfig
        .withFallback(site.withoutPath("locations").withFallback(MercuryConfig().defSite))
        , req
      )
    } else {
      if(site.hasPath("proxy_pass")) {
        new ProxyPass(site.getString("proxy_pass"))
      } else if(site.hasPath("root")) {
        new StaticContent(site.getString("root"))
      } else {
        throw new UnknownRequestHandlerException("Unable to find handler for %s".format(site.getString("name")))
      }
    }
  }

  @tailrec
  def parseLocations(locations: Array[Entry[String, ConfigValue]], uri: String): (String, String) = {
    if(locations.isEmpty) {
      return null
    }
    val locSplit = locations.head.getKey.split(" ")
    if(locSplit(0) != "location") {
      //TODO: Log error
      parseLocations(locations.tail, uri)
    } else {
      locSplit(1) match {
        case "~" =>
          val pattern = locations.head.getValue.asInstanceOf[ConfigObject].get("__regex__").asInstanceOf[Pattern]
          if(pattern.matcher(uri).matches()) {
            (uri.substring(locSplit(1).length), locations.head.getKey)
          } else {
            parseLocations(locations.tail, uri)
          }
        case prefix =>
          if(Try(uri.substring(0, prefix.length) == prefix).getOrElse(false)) {
            (uri.substring(locSplit(1).length), locations.head.getKey)
          } else {
            parseLocations(locations.tail, uri)
          }
      }
    }
  }
}