package io.mercury.logging

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.{LinkedBlockingQueue, ThreadFactory, ThreadPoolExecutor, TimeUnit}

import io.netty.handler.codec.http.{HttpRequest, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class MercuryLogger(access_logFile: FileChannel, error_logFile: FileChannel) {
  implicit val ec = MercuryLogger.ec
  val access_log = access_logFile
  val error_log = error_logFile
  val timeFormatter = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z")

  def logAccess(remote: String, req: HttpRequest, response: HttpResponse) = {
    Future {
      val msg = "%s \"%s\" [%s] \"%s\" %d %s \"%s\"\n".format(
        remote,
        req.headers.get("Referer"),
        timeFormatter.format(new Date()),
        "%s %s %s".format(req.getMethod, req.getUri, req.getProtocolVersion),
        response.getStatus.code,
        response.headers.get("Content-Length"),
        req.headers().get("User-Agent")
      )
      access_log.write(ByteBuffer.wrap(msg.getBytes("utf-8")))
    }
  }
}

object MercuryLogger {
  implicit val ec = new ExecutionContext {
    val threadpool = new ThreadPoolExecutor(
      4, 4, 0l, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable](),
      new ThreadFactory {
        val group = new ThreadGroup(Thread.currentThread().getThreadGroup, "mercury-logging-pool")

        override def newThread(r: Runnable): Thread = {
          new Thread(group, r, "mercury-logger-" + group.activeCount())
        }
      }
    )

    override def reportFailure(t: Throwable): Unit = throw t

    override def execute(runnable: Runnable): Unit = {
      threadpool.submit(runnable)
    }
  }
}