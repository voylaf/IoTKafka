package com.github.voylaf
package consumer

import cats.effect.{Async, ExitCode, Resource}
import metrics.KafkaMetrics

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka.{KafkaConsumer, commitBatchWithin}

import scala.concurrent.duration.DurationInt

object KafkaConsumerProgram extends StrictLogging {
  def stream[F[_]: Async, A](
      topic: String,
      groupId: String,
      bootstrapServers: String,
      chunkSize: Int,
      parallelism: Int,
      serde: KafkaSerdeProvider[F, String, A],
      handler: A => F[Unit]
  ): Resource[F, fs2.Stream[F, Unit]] = {
    val consumerSettings = KafkaCodecs.consumerSettings[F, String, A](groupId, bootstrapServers)(serde)

    KafkaConsumer
      .resource(consumerSettings)
      .evalTap(_.subscribeTo(topic))
      .map {
        _.stream
          .parEvalMapUnordered(parallelism) { committable =>
            val message = committable.record.value
            for {
              _ <- handler(message)
            } yield committable.offset
          }
          .through(commitBatchWithin(chunkSize, 5.seconds))
      }
  }

  def runWithMetrics[F[_]: Async](
      metrics: Resource[F, Unit],
      stream: Resource[F, fs2.Stream[F, Unit]]
  ): F[ExitCode] = {
    metrics
      .flatMap(_ => stream)
      .use(_.compile.drain)
      .as(ExitCode.Success)
      .handleErrorWith { ex =>
        KafkaMetrics.consumerErrors.inc()
        logger.error("Error during stream execution", ex)
        ExitCode.Error.pure[F]
      }
  }
}
