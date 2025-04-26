package com.github.voylaf

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pureconfig._
import pureconfig.generic.auto._

import java.util.Properties
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.language.implicitConversions

final case class ProducerConfig(producer: Config, topic: String, seed: Long)

object ProducerConfig extends LazyLogging {
  def getConfig(resource: String): (Properties, String, Long) = {
    // Load the full configuration
    val fullConfig: Config = ConfigFactory.load(resource)

    // Get producer-config with links resolving
    val producerConfig: Config = fullConfig.getConfig("producer").resolve()

    // Read topic and seed separately via PureConfig
    val topic = ConfigSource.fromConfig(fullConfig).loadOrThrow[ProducerConfig].topic
    val seed = ConfigSource.fromConfig(fullConfig).loadOrThrow[ProducerConfig].seed

    val props = producerConfigToProperties(producerConfig)

    logger.info(s"[CONFIG] topic: $topic")
    logger.info(s"[CONFIG] properties: ${props.entrySet()}")

    (props, topic, seed)
  }

  private def producerConfigToProperties(config: Config): Properties = {
    val map: Map[String, AnyRef] = config.entrySet().asScala.map(entry =>
      entry.getKey -> config.getAnyRef(entry.getKey)
    ).toMap

    val props = new Properties()
    map.foreach { case (k, v) => props.put(k, v) }
    props
  }

}
