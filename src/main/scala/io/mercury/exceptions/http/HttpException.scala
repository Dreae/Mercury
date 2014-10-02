package io.mercury.exceptions.http

case class HttpException(status: Int, msg: String) extends Exception
