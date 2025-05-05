package com.github.voylaf
package consumer

import cats.effect.{IO, IOApp}
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka._
import io.circe.generic.auto._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object ArticleJsonStringConsumerFs2 extends IOApp.Simple with StrictLogging {
  LoggingSetup.init()

  private val (config, topic) = ConsumerConfig.getConfig("kafka-intro.conf")
  private val serdeFormat: KafkaSerdeProvider[IO, String, Article] =
    SerdeFormat
      .fromString(config.getString("serde-format"))
      .fold(sys.error, _.provider[IO, String, Article])

  private val consumerSettings =
    KafkaCodecs.consumerSettings[IO, String, Article](
      config.getString("group.id"),
      config.getString("bootstrap.servers")
    )(serdeFormat)

  val chunkSize: Int   = config.getInt("chunk-size")
  val parallelism: Int = config.getInt("parallelism")

  private val stream =
    KafkaConsumer
      .stream(consumerSettings)
      .evalTap(_.subscribeTo(topic))
      .flatMap(_.stream)
      .parEvalMapUnordered(parallelism) { committable =>
        for {
          article <- IO.pure(committable.record.value)
          _ <- IO(logger.info(
            s"New article received. Title: ${article.title}. Author: ${article.author.name}"
          ))
        } yield committable.offset
      }
      .through(commitBatchWithin(chunkSize, 5 seconds)) // commit the batch

  override def run: IO[Unit] =
    stream.compile.drain
}
