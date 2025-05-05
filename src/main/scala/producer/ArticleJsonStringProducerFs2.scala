package com.github.voylaf
package producer

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging
import fs2.{Stream => Fs2Stream}
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerSettings}
import io.circe.generic.auto._

object ArticleJsonStringProducerFs2 extends IOApp with StrictLogging {

  LoggingSetup.init()

  private val (config, topic, seed) = ProducerConfig.getConfig("kafka-intro.conf")
  private val serdeFormat =
    SerdeFormat
      .fromString(config.getString("serde-format"))
      .fold(sys.error, _.provider[IO, String, Article])

  private val producerSettings: ProducerSettings[IO, String, Article] =
    KafkaCodecs.producerSettings[IO, String, Article](config.getString("bootstrap.servers"))(serdeFormat)

  val articles: Seq[Article] = FancyGenerator.withSeed(seed).articles take 200

  val chunkSize: Int   = config.getInt("chunk-size")
  val parallelism: Int = config.getInt("parallelism")

  val stream: Fs2Stream[IO, Unit] = KafkaProducer
    .stream(producerSettings)
    .flatMap { producer =>
      Fs2Stream
        .emits(articles)
        .evalTap(article => IO(logger.debug(s"Sending article with id=${article.id}, title=${article.title}")))
        .map(article => ProducerRecord(topic, article.id, article))
        .chunkN(chunkSize)
        .evalTap(chunk =>
          IO(logger.info(s"Sending chunk with ${chunk.size} articles"))
        )
        .covary[IO]
        .parEvalMapUnordered(parallelism) { chunk =>
          producer.produce(chunk).void
        }
    }

  override def run(args: List[String]): IO[ExitCode] = {
    stream
      .compile
      .drain
      .onError { case ex =>
        logger.error(s"Error during KafkaProducer stream execution: ${ex.getMessage}")
        IO.unit
      }
      .as(ExitCode.Success)
  }
}
