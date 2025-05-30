package com.github.voylaf

trait LoggingSupport[A] {
  def logMessageRecieved(a: A): String
  def logMessageSended(a: A): String
  def key(a: A): String
}
