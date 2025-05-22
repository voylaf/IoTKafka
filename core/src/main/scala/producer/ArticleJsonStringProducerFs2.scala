package com.github.voylaf
package producer

import cats.effect.{ExitCode, IO, IOApp, Resource}
import domain.Article
import avro.{Article => AvroArticle}

import cats.implicits.catsSyntaxApplyOps
import com.github.voylaf.metrics.{KafkaMetrics, MetricsServer}
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka.{KafkaProducer, ProducerRecord}
import fs2.{Stream => Fs2Stream}
import io.circe.generic.auto._

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

object ArticleJsonStringProducerFs2 extends IOApp with StrictLogging {

  LoggingSetup.init()

  private val (config, topic, seed) = ProducerConfig.getConfig("kafka-intro.conf")

  private def serdeFormatIO: IO[SerdeFormat] = SerdeFormat.fromString(config.getString("serde-format")) match {
    case Right(format) => IO.pure(format)
    case Left(_)       => IO.raiseError(new IllegalArgumentException("Unknown serde format"))
  }

  val chunkSize: Int         = config.getInt("chunk-size")
  val parallelism: Int       = config.getInt("parallelism")
  val articles: Seq[Article] = FancyGenerator.withSeed(seed).articles.take(2000)
  val servers: String        = config.getString("bootstrap.servers")

  private def stream[A](
      records: Seq[A],
      keyFn: A => String,
      logFn: A => String,
      serdeProvider: KafkaSerdeProvider[IO, String, A]
  ): Resource[IO, Fs2Stream[IO, Unit]] = {
    val producerSettings = KafkaCodecs.producerSettings(servers)(serdeProvider)

    KafkaProducer.resource(producerSettings).map { producer =>
      Fs2Stream
        .emits(records)
        .evalTap(a => IO(logger.debug(logFn(a))))
        .map(a => ProducerRecord(topic, keyFn(a), a))
        .chunkN(chunkSize)
        .evalTap(chunk => IO(logger.info(s"Sending chunk with ${chunk.size} articles")))
        .parEvalMapUnordered(parallelism) { chunk =>
          producer
            .produce(chunk)
            .flatTap { _ =>
              IO(KafkaMetrics.producedMessages.inc(chunk.size))
            }.void
        }
    }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val metrics = MetricsServer.start(8092)
    val kafkaStreamResource: IO[Resource[IO, Fs2Stream[IO, Unit]]] = serdeFormatIO.map {
      case SerdeFormat.Circe =>
        metrics.flatMap(_ =>
          stream[Article](
            records = articles,
            keyFn = _.id,
            logFn = a => s"Sending article with id=${a.id}, title=${a.title}",
            serdeProvider = KafkaCodecs.circeSerdeProvider[IO, String, Article]
          )
        )

      case SerdeFormat.Avro =>
        val avroArticles = articles.map(Article.toAvroArticle)
        val schemaUrl    = config.getString("schema.registry.url")
        metrics.flatMap(_ =>
          stream[AvroArticle](
            records = avroArticles,
            keyFn = _.id,
            logFn = a => s"Sending article with id=${a.id}, title=${a.title}",
            serdeProvider = KafkaCodecs.avroSerdeProvider[IO, String, AvroArticle](schemaUrl)
          )
        )
    }

    kafkaStreamResource
      .flatMap(_.use(_.compile.drain))
      .as(ExitCode.Success)
      .handleErrorWith { ex =>
        KafkaMetrics.producerErrors.inc()
        logger.error("Error during stream execution", ex)
        IO.pure(ExitCode.Error)
      }
  }
}
