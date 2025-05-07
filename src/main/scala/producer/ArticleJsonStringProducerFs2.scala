package com.github.voylaf
package producer

import cats.effect.{ExitCode, IO, IOApp}
import domain.Article
import avro.{Article => AvroArticle}

import com.typesafe.scalalogging.StrictLogging
import fs2.{Stream => Fs2Stream}
import fs2.kafka.{KafkaProducer, ProducerRecord, ProducerSettings}
import io.circe.generic.auto._
import shapeless.{:+:, Coproduct}

object ArticleJsonStringProducerFs2 extends IOApp with StrictLogging {

  LoggingSetup.init()

  private val (config, topic, seed) = ProducerConfig.getConfig("kafka-intro.conf")
  // Получаем формат сериализации из конфигурации
  private val serdeFormat = SerdeFormat.fromString(config.getString("serde-format")) match {
    case Right(format) => format
    case Left(err)     => sys.error(err)
  }

  private def convertArticle(article: Article): ArticleLike = {
    serdeFormat match {
      case SerdeFormat.Circe => Coproduct[ArticleLike](article)
      case SerdeFormat.Avro  => Coproduct[ArticleLike](Article.toAvroArticle(article))
    }
  }

  private val producerSettings: ProducerSettings[IO, String, ArticleLike] = {
    val serde: KafkaSerdeProvider[IO, String, ArticleLike] = SerdeFormat
      .fromString(config.getString("serde-format")) match {
      case Right(SerdeFormat.Circe) =>
        KafkaCodecs.circeSerdeProvider[IO, String, Article]

      case Right(SerdeFormat.Avro) =>
        KafkaCodecs.avroSerdeProvider[IO, String, AvroArticle]

      case Left(err) => sys.error(err)
    }
    KafkaCodecs.producerSettings(config.getString("bootstrap.servers"))(serde)
  }

  def articles: Seq[Article] = FancyGenerator.withSeed(seed).articles take 2000

  val chunkSize: Int   = config.getInt("chunk-size")
  val parallelism: Int = config.getInt("parallelism")

  val stream: Fs2Stream[IO, Unit] = KafkaProducer
    .stream(producerSettings)
    .flatMap { producer =>
      Fs2Stream
        .emits(articles)
        .evalTap(article => IO(logger.debug(s"Sending article with id=${article.id}, title=${article.title}")))
        .map(article => ProducerRecord(topic, article.id, convertArticle(article)))
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
