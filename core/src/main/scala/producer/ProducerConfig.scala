package com.github.voylaf
package producer

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.{DurationLong, FiniteDuration}
import scala.language.{implicitConversions, postfixOps}
import scala.util.Try

final case class ProducerConfig(
    clientId: String,
    topic: String,
    bootstrapServers: String,
    serdeFormat: String,
    schemaRegistryUrl: Option[String],
    chunkSize: Int,
    parallelism: Int,
    prometheusPort: Int,
    seed: Long,
    sleepingTime: FiniteDuration
)

object ProducerConfig extends LazyLogging {
  def getConfig(resource: String): Config = {
    // Load the full configuration
    val fullConfig: Config = ConfigFactory.load(resource)

    // Get producer-config with links resolving
    fullConfig.getConfig("producer").resolve()
  }

  def load(config: Config): ProducerConfig =
    ProducerConfig(
      clientId = config.getString("client.id"),
      topic = config.getString("topic"),
      bootstrapServers = config.getString("bootstrap.servers"),
      serdeFormat = config.getString("serde-format"),
      schemaRegistryUrl = Try(config.getString("schema.registry.url")).toOption,
      chunkSize = config.getInt("chunk-size"),
      parallelism = config.getInt("parallelism"),
      prometheusPort = config.getInt("prometheus.port"),
      seed = config.getLong("seed"),
      sleepingTime = config.getLong("sleeping-time-seconds") seconds
    )
}
