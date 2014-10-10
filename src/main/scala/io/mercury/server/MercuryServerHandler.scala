package io.mercury.server

import java.net.URLDecoder
import java.util.Map.Entry
import java.util.regex.Pattern

import com.typesafe.config.{Config, ConfigObject, ConfigValue}
import io.mercury.config.MercuryConfig
import io.mercury.exceptions.UnknownRequestHandlerException
import io.mercury.exceptions.http.HttpException
import io.mercury.response.{ReturnDirectiveHandler, StaticContentResponse}
import io.mercury.server.MercuryServerHandler.{CompleteResponse, StaticContent}
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._

import scala.annotation.tailrec
import scala.util.Try

class MercuryServerHandler() extends SimpleChannelInboundHandler[FullHttpRequest]{

  override def channelReadComplete(ctx: ChannelHandlerContext) = {
    ctx.flush()
  }

  override def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    if(!req.getDecoderResult.isSuccess) {
      sendError(ctx, 400, "Bad Request")
      return
    }

    val reqSite = req.headers.get("HOST").split(":")(0)

    val site = MercuryConfig().site(reqSite)

    MercuryServerHandler.getRequestHandlerType(site, req) match {
      case StaticContent(root) =>
        new StaticContentResponse(root, site, req).complete(ctx)
      case CompleteResponse(code, status) =>
        new ReturnDirectiveHandler(code, status).complete(ctx)
    }
    complete(req, ctx)
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    cause match {
      case HttpException(status, msg) =>
        sendError(ctx, status, msg)
      case _ =>
        cause.printStackTrace()
        if(ctx.channel.isActive) {
          sendError(ctx, 500, "Internal Server Error")
        }
    }
  }

  private def complete(req: HttpRequest, ctx: ChannelHandlerContext) = {
    if(!HttpHeaders.isKeepAlive(req) && ctx.channel.isActive) {
      ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    }
  }

  def sendError(ctx: ChannelHandlerContext, code: Int, status: String) = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(code, status))
    response.content.writeBytes("An error has occurred".getBytes("utf-8"))
    response.headers.set("Content-Type", "text/plain; charset=UTF-8")
    writeResponse(response, ctx)
  }

  private def writeResponse(response: FullHttpResponse, ctx: ChannelHandlerContext) = {
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }
}

object MercuryServerHandler {
  val HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz"

  abstract class RequestHandlerType
  case class StaticContent(root: String) extends RequestHandlerType
  case class ProxyPass(host: String) extends RequestHandlerType
  case class CompleteResponse(code: Int, str: String) extends RequestHandlerType

  @tailrec
  def getRequestHandlerType(site: Config, req: FullHttpRequest): RequestHandlerType = {
    val locObj = site.getObject("locations")
    val locations = locObj.entrySet().toArray.map(_.asInstanceOf[Entry[String, ConfigValue]])
    val location = parseLocations(locations,  URLDecoder.decode(req.getUri, "utf-8"), req.getMethod.name.toLowerCase)
    if(location != null) {
      req.setUri(location._1)
      getRequestHandlerType(
        locObj.toConfig
          .getObject(location._2).toConfig
          .withFallback(site.withoutPath("locations").withFallback(MercuryConfig().defSite))
        , req
      )
    } else {
      if(site.hasPath("return")) {
        val returnSplit = site.getString("return").split(" ", 2)
        new CompleteResponse(returnSplit(0).toInt, returnSplit(1))
      } else if(site.hasPath("proxy_pass")) {
        new ProxyPass(site.getString("proxy_pass"))
      } else if(site.hasPath("root")) {
        new StaticContent(site.getString("root"))
      } else {
        throw new UnknownRequestHandlerException("Unable to find handler for %s".format(site.getString("name")))
      }
    }
  }

  @tailrec
  def parseLocations(locations: Array[Entry[String, ConfigValue]], uri: String, method: String): (String, String) = {
    if(locations.isEmpty) {
      return null
    }
    val locSplit = locations.head.getKey.split(" ")
    if(locSplit(0) != "any" && method != locSplit(0)) {
      //TODO: Log error
      parseLocations(locations.tail, uri, method)
    } else {
      locSplit(1) match {
        case "~" =>
          val pattern = locations.head.getValue.asInstanceOf[ConfigObject].get("__regex__").asInstanceOf[Pattern]
          if(pattern.matcher(uri).matches()) {
            (uri.substring(locSplit(1).length), locations.head.getKey)
          } else {
            parseLocations(locations.tail, uri, method)
          }
        case prefix =>
          if(Try(uri.substring(0, prefix.length) == prefix).getOrElse(false)) {
            (uri.substring(locSplit(1).length), locations.head.getKey)
          } else {
            parseLocations(locations.tail, uri, method)
          }
      }
    }
  }
}