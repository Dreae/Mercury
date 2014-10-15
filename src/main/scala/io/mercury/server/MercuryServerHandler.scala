package io.mercury.server

import java.net.URLDecoder
import java.util.concurrent._
import java.util.regex.Pattern

import com.typesafe.config.ConfigObject
import io.mercury.config.MercuryConfig
import io.mercury.config.MercuryConfig.{LocationConfig, SiteConfig}
import io.mercury.exceptions.http.HttpException
import io.mercury.response.{ReturnDirectiveHandler, StaticContentResponse}
import io.mercury.server.MercuryServerHandler.{CompleteResponse, StaticContent}
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}

class MercuryServerHandler() extends SimpleChannelInboundHandler[FullHttpRequest]{

  implicit val ec = MercuryServerHandler.ec

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

    process(
      MercuryServerHandler.getRequestHandlerType(site, req) match {
        case StaticContent(root) =>
          new StaticContentResponse(root, site, req).complete
        case CompleteResponse(code, status) =>
          new ReturnDirectiveHandler(code, status).complete
      }, req, ctx
    )
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

  private def process(f: ChannelHandlerContext => Any, req: FullHttpRequest, ctx: ChannelHandlerContext) = {
    val future = Future(f(ctx))
    future.onComplete{
      case Success(_) =>
        complete(req, ctx)
      case Failure(t) => throw t
    }
  }

  private def complete(req: HttpRequest, ctx: ChannelHandlerContext) = {
    val future = ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
    if(!HttpHeaders.isKeepAlive(req))
      future.addListener(ChannelFutureListener.CLOSE)
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

  implicit val ec = new ExecutionContext {
    val threadPool = new ThreadPoolExecutor(
      MercuryConfig().worker_threads,
      MercuryConfig().worker_threads * 4,
      180l, TimeUnit.SECONDS, new SynchronousQueue[Runnable](),
      new ThreadFactory {
        val group = new ThreadGroup(Thread.currentThread().getThreadGroup, "mercury-handler-pool")

        override def newThread(r: Runnable): Thread = {
          new Thread(group, r, "handler-thread-" + group.activeCount())
        }
      }
    )

    override def reportFailure(t: Throwable): Unit = throw t

    override def execute(runnable: Runnable): Unit = threadPool.submit(runnable)
  }

  abstract class RequestHandlerType
  case class StaticContent(root: String) extends RequestHandlerType
  case class ProxyPass(host: String) extends RequestHandlerType
  case class CompleteResponse(code: Int, str: String) extends RequestHandlerType

  @tailrec
  def getRequestHandlerType(site: SiteConfig, req: FullHttpRequest): RequestHandlerType = {
    val locations = site.locations.map(_.toArray)
    val location = locations.map(parseLocations(_,  URLDecoder.decode(req.getUri, "utf-8"), req.getMethod.name.toLowerCase))
    if(location.isDefined && location.get != null) {
      req.setUri(location.get._1)
      getRequestHandlerType(site.locations.get(location.get._2).site, req)
    } else {
      if(site.returnDirective.isDefined) {
        new CompleteResponse(site.returnDirective.get.code, site.returnDirective.get.status)
      } else if(site.root.isDefined) {
        new StaticContent(site.root.get)
      } else {
        new StaticContent("html")
      }
    }
  }

  @tailrec
  def parseLocations(locations: Array[(String, LocationConfig)], uri: String, method: String): (String, String) = {
    if(locations.isEmpty) {
      return null
    }
    val locSplit = locations.head._1.split(" ")
    if(locSplit(0) != "any" && method != locSplit(0)) {
      //TODO: Log error
      parseLocations(locations.tail, uri, method)
    } else {
      if(locations.head._2.regex.isDefined) {
        val pattern = locations.head._2.regex.get
        val match_ = pattern.findFirstIn(uri)
        if (match_.isDefined) {
          (uri.substring(match_.get.length), locations.head._1)
        } else {
          parseLocations(locations.tail, uri, method)
        }
      }
      else {
        if (Try(uri.substring(0, locSplit(1).length) == locSplit(1)).getOrElse(false)) {
          (uri.substring(locSplit(1).length), locations.head._1)
        } else {
          parseLocations(locations.tail, uri, method)
        }
      }
    }
  }
}