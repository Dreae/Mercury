package io.mercury.exceptions

case class UnknownRequestHandlerException(message: String) extends Exception(message)
