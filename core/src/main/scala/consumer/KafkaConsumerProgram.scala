package com.github.voylaf
package consumer

import cats.effect.{Async, ExitCode, Resource}
import cats.effect.kernel.Sync
import metrics.KafkaMetrics

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka.{KafkaConsumer, commitBatchWithin}
import fs2.{Stream => Fs2Stream}
import io.circe.Decoder

import scala.concurrent.duration.DurationInt

object KafkaConsumerProgram extends StrictLogging {
  def stream[F[_]: Async, A: Decoder](
      topic: String,
      groupId: String,
      bootstrapServers: String,
      chunkSize: Int,
      parallelism: Int,
      logFn: A => String,
      serde: KafkaSerdeProvider[F, String, A]
  ): Resource[F, Fs2Stream[F, Unit]] = {
    val consumerSettings = KafkaCodecs.consumerSettings[F, String, A](groupId, bootstrapServers)(serde)

    KafkaConsumer
      .resource(consumerSettings)
      .evalTap(_.subscribeTo(topic))
      .map {
        _.stream
          .parEvalMapUnordered(parallelism) { committable =>
            for {
              article <- Sync[F].delay(committable.record.value)
              _       <- Sync[F].blocking(KafkaMetrics.consumedMessages.inc())
              _       <- Sync[F].blocking(logger.info(logFn(article)))
            } yield committable.offset
          }
          .through(commitBatchWithin(chunkSize, 5.seconds))
      }
  }

  def runWithMetrics[F[_]: Async, A](
      metrics: Resource[F, Unit],
      stream: Resource[F, Fs2Stream[F, Unit]]
  ): F[ExitCode] = {
    metrics
      .flatMap(_ => stream)
      .use(_.compile.drain)
      .as(ExitCode.Success)
      .handleErrorWith { ex =>
        KafkaMetrics.consumerErrors.inc()
        logger.error("Error during stream execution", ex)
        Sync[F].pure(ExitCode.Error)
      }
  }
}
