package com.github.voylaf
package consumer

import cats.effect.{IO, IOApp}
import avro.{Article => AvroArticle}
import domain.Article
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka._
import io.circe.generic.auto._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object ArticleJsonStringConsumerFs2 extends IOApp.Simple with StrictLogging {
  LoggingSetup.init()

  private val (config, topic) = ConsumerConfig.getConfig("kafka-intro.conf")
  private val consumerSettings =
    SerdeFormat
      .fromString(config.getString("serde-format")) match {
      case Right(SerdeFormat.Circe) =>
        val serde = KafkaCodecs.circeSerdeProvider[IO, String, Article]
        KafkaCodecs.consumerSettings(config.getString("group.id"), config.getString("bootstrap.servers"))(serde)

      case Right(SerdeFormat.Avro) =>
        val serde = KafkaCodecs.avroSerdeProvider[IO, String, AvroArticle]
        KafkaCodecs.consumerSettings(config.getString("group.id"), config.getString("bootstrap.servers"))(serde)

      case Left(err) => sys.error(err)
    }

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
            s"New article received: ${article} (${article.getClass})"
//            s"New article received. Title: ${article.title}. Author: ${article.author.name}"
          ))
        } yield committable.offset
      }
      .through(commitBatchWithin(chunkSize, 5 seconds)) // commit the batch

  override def run: IO[Unit] =
    stream.compile.drain
}
