package com.github.voylaf
package consumer

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import scala.util.Try

final case class ConsumerConfig(
    groupId: String,
    topic: String,
    bootstrapServers: String,
    serdeFormat: String,
    schemaRegistryUrl: Option[String],
    chunkSize: Int,
    parallelism: Int,
    prometheusPort: Int
)

object ConsumerConfig extends LazyLogging {
  def getConfig(resource: String): Config = {
    // Load the full configuration
    val fullConfig: Config = ConfigFactory.load(resource)
    // Get producer-config with links resolving
    fullConfig.getConfig("consumer").resolve()
  }

  def load(config: Config): ConsumerConfig =
    ConsumerConfig(
      groupId = config.getString("group.id"),
      topic = config.getString("topic"),
      bootstrapServers = config.getString("bootstrap.servers"),
      serdeFormat = config.getString("serde-format"),
      schemaRegistryUrl = Try(config.getString("schema.registry.url")).toOption,
      chunkSize = config.getInt("chunk-size"),
      parallelism = config.getInt("parallelism"),
      prometheusPort = config.getInt("prometheus.port")
    )
}
