package com.github.voylaf
package consumer

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pureconfig._
import pureconfig.generic.auto._

import java.util.Properties
import scala.jdk.CollectionConverters.CollectionHasAsScala

final case class ConsumerConfig(consumer: Config, topic: String)

object ConsumerConfig extends LazyLogging {
  def getConfig(resource: String): (Config, String) = {
    // Load the full configuration
    val fullConfig: Config = ConfigFactory.load(resource)

    // Get producer-config with links resolving
    val consumerConfig: Config = fullConfig.getConfig("consumer").resolve()

    // Read topic and seed separately via PureConfig
    val topic = ConfigSource.fromConfig(fullConfig).loadOrThrow[ConsumerConfig].topic

    logger.info(s"[CONFIG] topic: $topic")

    (consumerConfig, topic)
  }

  def consumerConfigToProperties(config: Config): Properties = {
    val map: Map[String, AnyRef] = config.entrySet().asScala.map(entry =>
      entry.getKey -> config.getAnyRef(entry.getKey)
    ).toMap

    val props = new Properties()
    map.foreach { case (k, v) => props.put(k, v) }
    props
  }

}
