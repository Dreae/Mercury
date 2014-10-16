package io.mercury.response

import io.mercury.server.MercuryIO

abstract class MercuryHttpResponder {

  def complete(): MercuryIO
}
