package com.github.voylaf
package consumer

import cats.effect.{ExitCode, IO, IOApp, Resource}
import avro.{Article => AvroArticle, Author => AvroAuthor}
import domain.Article
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka._
import fs2.{Stream => Fs2Stream}
import io.circe.Decoder
import io.circe.generic.auto._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object ArticleJsonStringConsumerFs2 extends IOApp with StrictLogging {
  LoggingSetup.init()

  private val (config, topic) = ConsumerConfig.getConfig("kafka-intro.conf")
  def groupId: String         = config.getString("group.id")
  def servers: String         = config.getString("bootstrap.servers")
  private def serdeFormatIO: IO[SerdeFormat] =
    SerdeFormat.fromString(config.getString("serde-format")) match {
      case Right(format) => IO.pure(format)
      case Left(_)       => IO.raiseError(new IllegalArgumentException("Unknown serde format"))
    }

  def chunkSize: Int   = config.getInt("chunk-size")
  def parallelism: Int = config.getInt("parallelism")

//  is this a small hack via avro4s?
  private def stream[A: Decoder](
      logFn: A => String,
      serdeProvider: KafkaSerdeProvider[IO, String, A]
  ): Resource[IO, Fs2Stream[IO, Unit]] = {
    val consumerSettings = KafkaCodecs.consumerSettings(groupId, servers)(serdeProvider)
    KafkaConsumer
      .resource(consumerSettings)
      .evalTap(_.subscribeTo(topic))
      .map {
        _.stream
          .parEvalMapUnordered(parallelism) { committable =>
            for {
              article <- IO.pure(committable.record.value)
              _       <- IO(logger.info(logFn(article)))
            } yield committable.offset
          }
          .through(commitBatchWithin(chunkSize, 5.seconds))
      }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    serdeFormatIO.map {
      case SerdeFormat.Circe =>
        val serde = KafkaCodecs.circeSerdeProvider[IO, String, Article]
        val logFn = (article: Article) => s"New article received. Title: ${article.title}. Author: ${article.author.name}"
        stream[Article](logFn, serde)

      case SerdeFormat.Avro =>
        val schemaUrl: String = config.getString("schema.registry.url")
        val serde             = KafkaCodecs.avroSerdeProvider[IO, String, AvroArticle](schemaUrl)
        val logFn             = (article: AvroArticle) => s"New article received. Title: ${article.title}. Author: ${article.author.name}"
        stream[AvroArticle](logFn, serde)
    }
      .flatMap(_.use(_.compile.drain))
      .as(ExitCode.Success)
      .handleErrorWith { ex =>
        logger.error("Error during stream execution", ex)
        IO.pure(ExitCode.Error)
      }

  }
}
