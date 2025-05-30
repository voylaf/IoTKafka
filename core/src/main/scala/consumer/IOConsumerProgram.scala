package com.github.voylaf
package consumer

import metrics.{KafkaMetrics, MetricsServer}

import cats.effect.{ExitCode, IO, Resource}
import com.typesafe.scalalogging.StrictLogging

object IOConsumerProgram extends StrictLogging {
  def stream[A](
      config: ConsumerConfig,
      serde: KafkaSerdeProvider[IO, String, A],
      handler: A => IO[Unit]
  ): Resource[IO, fs2.Stream[IO, Unit]] = {
    KafkaConsumerProgram.stream[IO, A](
      topic = config.topic,
      groupId = config.groupId,
      bootstrapServers = config.bootstrapServers,
      chunkSize = config.chunkSize,
      parallelism = config.parallelism,
      serde = serde,
      handler = handler
    )
  }

  def serde[A](config: ConsumerConfig)(implicit serdeSupport: SerdeSupport[IO, A]): KafkaSerdeProvider[IO, String, A] =
    SerdeFormat
      .fromString(config.serdeFormat)
      .map(format => KafkaCodecs.resolveSerde[IO, A](format, config.schemaRegistryUrl))
      .getOrElse(sys.error("Unknown serde format"))

  def run[A](config: ConsumerConfig)(
      implicit
      serdeSupport: SerdeSupport[IO, A],
      loggingSupport: LoggingSupport[A]
  ): IO[ExitCode] = {
    val metrics = MetricsServer.start(config.prometheusPort)
    val handler = (a: A) =>
      IO {
        KafkaMetrics.consumedMessages.inc()
        logger.info(loggingSupport.logMessageRecieved(a))
      }

    KafkaConsumerProgram.runWithMetrics(metrics, stream[A](config, serde(config), handler))
  }

}
