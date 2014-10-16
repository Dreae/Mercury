package io.mercury.server

class MercuryIO private (msgs: List[AnyRef]) {
  val msgList = msgs

  def this() = this(Nil)

  def >>(msg: AnyRef) = new MercuryIO(msgs :+ msg)
}
