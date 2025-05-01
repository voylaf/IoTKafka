package com.github.voylaf
package consumer

import producer.ArticleJsonStringProducer.jsonMapper

import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.errors.WakeupException

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.{IterableHasAsScala, SeqHasAsJava}
import scala.language.postfixOps

object ArticleJsonStringConsumer
    extends App
    with ConsumerUtils[Article]
    with JsonStringDeserializer[Article] {

  private val (config, topic) = ConsumerConfig.getConfig("kafka-intro.conf")

  private val consumer =
    new KafkaConsumer(config, keyDeserializer, valueDeserializer)

  @volatile private var running = true

  sys.addShutdownHook {
    logger.info("Shutdown signal received, closing Kafka consumer...")
    running = false
    consumer.wakeup()
  }

  consumer.subscribe(List(topic).asJava)

  try {
    while (running) {
      val records: ConsumerRecords[String, String] =
        pool(consumer, 1 second)
      for (record <- records.asScala) {
        try {
          processRecord(record.key(), record.value())
        } catch {
          case ex: Throwable =>
            logger.error(s"Error while processing record ${record.key()}", ex)
        }
      }

      consumer.commitAsync()
    }
  } catch {
    case _: WakeupException if !running =>
      logger.info("Kafka consumer is shutting down gracefully...")
    case ex: Throwable =>
      logger.error("Unexpected error in Kafka consumer loop", ex)
      throw ex
  } finally {
    consumer.close()
    logger.info("Kafka consumer closed.")
  }

  override def processRecord(key: String, value: String): Unit = {
    val article = fromJsonString(value)
    logger.info(
      s"New article received. Title: ${article.title}. Author: ${article.author.name}"
    )
  }

}
