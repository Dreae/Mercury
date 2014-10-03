package io.mercury.response

import java.io.{File, FileNotFoundException, RandomAccessFile}
import java.net.URLDecoder

import io.mercury.config.MercuryConfig
import io.mercury.exceptions.http.{ForbiddenException, MethodNotAllowedException, NotFoundException}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http._
import io.netty.handler.stream.ChunkedFile

class StaticContentResponse(root: String) {

  def toResponse(request: FullHttpRequest, ctx: ChannelHandlerContext) = {
    if(request.getMethod.name != "GET")
      throw new MethodNotAllowedException

    val uri = request.getUri
    val path = sanitizeUri(uri)
    if(path == null)
      throw new NotFoundException

    var file = new File(root, path)
    if(file.isDirectory) {
      val index = new File(file.getCanonicalPath, "index.html")
      if(!index.exists || index.isHidden || !index.isFile)
        throw new ForbiddenException
      else
        file = index
    }
    if(file.isHidden || !file.exists || !file.isFile)
      throw new NotFoundException

    try {
      val raf = new RandomAccessFile(file, "r")
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "OK"))
      response.headers.set("Content-Length", file.length)
      response.headers.set("Content-Type", guessMimeType(file.getCanonicalPath))
      ctx.write(response)
      ctx.write(new HttpChunkedInput(new ChunkedFile(raf, 0, file.length, 2048)))
      ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
    } catch {
      case _: FileNotFoundException => throw new NotFoundException
    }
  }

  private def sanitizeUri(uri: String): String = {
    val url = URLDecoder.decode(uri, "utf-8")
    val testFile = new File(root, url).getCanonicalPath
    val rootFile = new File(root).getCanonicalPath
    if(testFile.substring(0, rootFile.length) == rootFile){
      url
    } else {
      null
    }
  }

  private def guessMimeType(file: String): String = {
    val ext = file.substring(file.lastIndexOf('.') + 1, file.length)
    val types = MercuryConfig().mimeTypes.asInstanceOf[List[(List[String], String)]].filter(_._1.contains(ext))
    if(types.nonEmpty) types(0)._2 else MercuryConfig().defaultType.asInstanceOf[String]
  }
}
