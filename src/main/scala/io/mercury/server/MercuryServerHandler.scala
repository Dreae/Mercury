package io.mercury.server

import com.typesafe.config.Config
import io.mercury.response.StaticContentResponse
import RequestHandler.StaticContent
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._

class MercuryServerHandler(private val sites: Array[Config], private val conf: Map[String, AnyRef]) extends SimpleChannelInboundHandler[FullHttpRequest]{

  override def channelReadComplete(ctx: ChannelHandlerContext) = {
    ctx.flush()
  }

  override def channelRead0(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    val buf = new StringBuilder()

    if(!req.getDecoderResult.isSuccess) {
      sendError(ctx, 400, "Bad Request")
      return
    }

    val reqSite = req.headers.get("HOST").split(":")(0)
    val site = {
      val iSites = sites.filter(_.getString("name") == reqSite)
      if(iSites.length == 0) {
        sendError(ctx, 503, "Service Unavailable")
        return
      } else if(iSites.length > 1) {
        //TODO: Log error
      }
      iSites(0)
    }

    RequestHandler.getRequestHandlerType(site) match {
      case StaticContent(root) =>
        new StaticContentResponse(conf, root).toResponse(req, ctx)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    if(ctx.channel.isActive) {
      cause.printStackTrace()
      sendError(ctx, 500, "Internal Server Error")
    }
  }

  def sendError(ctx: ChannelHandlerContext, code: Int, status: String) = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(code, status))
    response.content.writeBytes("An error has occurred".getBytes("utf-8"))
    response.headers.set("Content-Type", "text/plain; charset=UTF-8")
    finalizeResponse(response)
    writeResponse(response, ctx)
  }

  private def finalizeResponse(response: FullHttpResponse) = {
    response.headers.set("Server", conf("tokens"))
  }

  private def writeResponse(response: FullHttpResponse, ctx: ChannelHandlerContext) = {
    ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
  }
}
