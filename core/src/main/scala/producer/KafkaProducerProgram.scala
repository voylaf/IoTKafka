package com.github.voylaf
package producer

import cats.effect.{Async, ExitCode, IO, Resource, Sync}
import metrics.KafkaMetrics

import cats.implicits._
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka.{KafkaProducer, ProducerRecord}

import scala.concurrent.duration.FiniteDuration

object KafkaProducerProgram extends StrictLogging {
  def stream[F[_]: Async, A](
      records: Seq[A],
      topic: String,
      bootstrapServers: String,
      chunkSize: Int,
      parallelism: Int,
      logFn: A => String,
      keyFn: A => String,
      serde: KafkaSerdeProvider[F, String, A]
  ): Resource[F, fs2.Stream[F, Unit]] = {
    val producerSettings = KafkaCodecs.producerSettings(bootstrapServers)(serde)

    KafkaProducer.resource(producerSettings).map { producer =>
      fs2.Stream
        .emits(records)
        .evalTap(a => Sync[F].blocking(logger.debug(logFn(a))))
        .map(a => ProducerRecord(topic, keyFn(a), a))
        .chunkN(chunkSize)
        .evalTap(chunk => Sync[F].blocking(logger.info(s"Sending chunk with ${chunk.size} articles")))
        .parEvalMapUnordered(parallelism) { chunk =>
          producer
            .produce(chunk)
            .flatTap { _ =>
              Sync[F].blocking(KafkaMetrics.producedMessages.inc(chunk.size))
            }.void
        }
    }
  }

  def runWithMetrics[F[_]: Async, A](
      metrics: Resource[F, Unit],
      stream: Resource[F, fs2.Stream[F, Unit]],
      sleepingTime: FiniteDuration
  ): F[ExitCode] = {
    metrics
      .flatMap(_ => stream)
      .use(_.compile.drain >> Sync[F].sleep(sleepingTime))
      .as(ExitCode.Success)
      .handleErrorWith { ex =>
        KafkaMetrics.producerErrors.inc()
        logger.error("Error during stream execution", ex)
        Sync[F].pure(ExitCode.Error)
      }
  }

}
