package io.mercury.server

import com.typesafe.config.Config
import io.mercury.exceptions.UnknownRequestHandlerException

object RequestHandler {
  abstract class RequestHandlerType
  case class StaticContent(root: String) extends RequestHandlerType
  case class ProxyPass(host: String) extends RequestHandlerType

  def getRequestHandlerType(site: Config): RequestHandlerType = {
    if(site.hasPath("proxy_pass")) {
      new ProxyPass(site.getString("proxy_pass"))
    } else if(site.hasPath("root")) {
      new StaticContent(site.getString("root"))
    } else {
      throw new UnknownRequestHandlerException("Unable to find handler for %s".format(site.getString("name")))
    }
  }
}