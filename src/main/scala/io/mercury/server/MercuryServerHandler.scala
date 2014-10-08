package io.mercury.server

import io.mercury.config.MercuryConfig
import io.mercury.exceptions.http.HttpException
import io.mercury.response.StaticContentResponse
import io.mercury.server.RequestHandler.StaticContent
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._

class MercuryServerHandler() extends SimpleChannelInboundHandler[FullHttpRequest]{

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

    val site = MercuryConfig().site(reqSite)

    RequestHandler.getRequestHandlerType(site, req) match {
      case StaticContent(root) =>
        new StaticContentResponse(root, site).toResponse(req, ctx)
    }
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
