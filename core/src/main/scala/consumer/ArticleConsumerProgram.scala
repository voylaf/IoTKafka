package com.github.voylaf
package consumer

import domain.Article
import metrics.MetricsServer

import cats.effect.{ExitCode, IO}
import io.circe.generic.decoding.DerivedDecoder.deriveDecoder

object ArticleConsumerProgram {
  def run(config: ConsumerConfig): IO[ExitCode] = {
    val serde = SerdeFormat
      .fromString(config.serdeFormat)
      .map(format => KafkaCodecs.resolveSerde[IO, Article](format, config.schemaRegistryUrl))
      .getOrElse(sys.error("Unknown serde format"))

    val metrics = MetricsServer.start(config.prometheusPort)

    val stream = KafkaConsumerProgram.stream[IO, Article](
      topic = config.topic,
      groupId = config.groupId,
      bootstrapServers = config.bootstrapServers,
      chunkSize = config.chunkSize,
      parallelism = config.parallelism,
      logFn = (article: Article) => s"New article received. Title: ${article.title}. Author: ${article.author.name}",
      serde = serde
    )

    KafkaConsumerProgram.runWithMetrics(metrics, stream)
  }

}
