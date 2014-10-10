package io.mercury.response

import java.io.{File, FileNotFoundException, RandomAccessFile}
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.{Locale, TimeZone, Calendar, Date, GregorianCalendar}

import com.typesafe.config.Config
import io.mercury.config.MercuryConfig
import io.mercury.exceptions.http.{MethodNotAllowedException, NotFoundException}
import io.mercury.server.MercuryServerHandler.HTTP_DATE_FORMAT
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http._
import io.netty.handler.stream.ChunkedFile

class StaticContentResponse(root: String, site: Config, req: FullHttpRequest) extends MercuryHttpResponder {
  val format = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US)
  format.setTimeZone(TimeZone.getTimeZone("GMT"))

  override def complete: (ChannelHandlerContext => Any)  = {
    if(req.getMethod.name != "GET")
    throw new MethodNotAllowedException

    val uri = req.getUri
    val path = sanitizeUri(uri)
    if(path == null)
    throw new NotFoundException

    var file = new File(root, path)
    if(file.isDirectory) {
      val indices = site.getList("index").unwrapped().toArray.map{case index: String => new File(file.getCanonicalPath, index)}
      for(index <- indices) {
        if(index.exists && !index.isHidden && index.isFile)
        file = index
      }
    }
    if(file.isHidden || !file.exists || !file.isFile || file.isDirectory)
    throw new NotFoundException

    val ifModifiedSince = req.headers().get("If-Modified-Since")
    if(ifModifiedSince != null && ifModifiedSince.nonEmpty && (format.parse(ifModifiedSince).getTime / 1000) == (file.lastModified() / 1000)) {
      val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(304, "Not Modified"))
      (ctx) => ctx.write(response)
    } else {
      try {
        val raf = new RandomAccessFile(file, "r")
        val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(200, "OK"))
        response.headers.set("Content-Length", file.length)
        response.headers.set("Content-Type", guessMimeType(file.getCanonicalPath))
        setCacheHeaders(response, file)
        (ctx) => {
          ctx.write(response)
          ctx.write(new HttpChunkedInput(new ChunkedFile(raf, 0, file.length, 2048)))
        }
      } catch {
        case _: FileNotFoundException => throw new NotFoundException
      }
    }
  }

  private def setCacheHeaders(resp: HttpResponse, file: File) = {
    val time = new GregorianCalendar()
    resp.headers().set("Date", format.format(time.getTime))
    time.add(Calendar.SECOND, 3600)
    resp.headers().set("Expires", format.format(time.getTime))
    resp.headers().add("Cache-Control", "max-age=" + 3600)
    resp.headers().add("Last-Modified", format.format(new Date(file.lastModified())))
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
