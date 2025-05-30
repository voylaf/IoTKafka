package com.github.voylaf
package producer

import metrics.MetricsServer

import cats.effect.{ExitCode, IO, Resource}
import com.typesafe.scalalogging.StrictLogging

object IOProducerProgram extends StrictLogging {
  def stream[A](
      config: ProducerConfig,
      serdeProvider: KafkaSerdeProvider[IO, String, A],
      records: Seq[A]
  )(implicit loggingSupport: LoggingSupport[A]): Resource[IO, fs2.Stream[IO, Unit]] =
    KafkaProducerProgram.stream[IO, A](
      records = records,
      topic = config.topic,
      bootstrapServers = config.bootstrapServers,
      chunkSize = config.chunkSize,
      parallelism = config.parallelism,
      logFn = loggingSupport.logMessageSended,
      keyFn = loggingSupport.key,
      serde = serdeProvider
    )

  def serde[A](config: ProducerConfig)(implicit serdeSupport: SerdeSupport[IO, A]): KafkaSerdeProvider[IO, String, A] =
    SerdeFormat
      .fromString(config.serdeFormat)
      .map(format => KafkaCodecs.resolveSerde[IO, A](format, config.schemaRegistryUrl))
      .getOrElse(sys.error("Unknown serde format"))

  def run[A](config: ProducerConfig)(records: Seq[A])(
      implicit
      serdeSupport: SerdeSupport[IO, A],
      loggingSupport: LoggingSupport[A]
  ): IO[ExitCode] = {

    val metrics = MetricsServer.start(config.prometheusPort)

    KafkaProducerProgram.runWithMetrics(metrics, stream(config, serde(config), records), config.sleepingTime)
  }

}
