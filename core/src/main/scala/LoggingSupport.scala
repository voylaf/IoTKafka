package com.github.voylaf

trait LoggingSupport[A] {
  def logMessage(a: A): String
}
