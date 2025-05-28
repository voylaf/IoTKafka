package com.github.voylaf
package producer

import domain.Article
import metrics.MetricsServer

import cats.effect.{ExitCode, IO}

object ArticleProducerProgram {
  def run(config: ProducerConfig): IO[ExitCode] = {
    val serde = SerdeFormat
      .fromString(config.serdeFormat)
      .map(format => KafkaCodecs.resolveSerde[IO, Article](format, config.schemaRegistryUrl))
      .getOrElse(sys.error("Unknown serde format"))

    val metrics = MetricsServer.start(config.prometheusPort)

    val stream = KafkaProducerProgram.stream[IO, Article](
      records = FancyGenerator.withSeed(config.seed).articles take 500,
      topic = config.topic,
      bootstrapServers = config.bootstrapServers,
      chunkSize = config.chunkSize,
      parallelism = config.parallelism,
      logFn = a => s"Sending article with id=${a.id}, title=${a.title}",
      keyFn = _.id,
      serde = serde
    )

    KafkaProducerProgram.runWithMetrics(metrics, stream, config.sleepingTime)
  }

}
