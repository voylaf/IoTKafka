package com.github.voylaf

import com.typesafe.config.ConfigFactory

object LoggingSetup {
  def init(configName: String = "application.conf"): Unit = {
    val appConfig = ConfigFactory.load(configName)

    val logLevel = appConfig.getString("logging.level")

    System.setProperty("LOG_LEVEL", logLevel)
  }
}
