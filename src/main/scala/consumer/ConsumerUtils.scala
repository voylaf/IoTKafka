package com.github.voylaf
package consumer

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters.IterableHasAsScala
import scala.jdk.DurationConverters.ScalaDurationOps
import scala.language.postfixOps

trait ConsumerUtils[T] extends LazyLogging {
  def pool[K, V](
      consumer: KafkaConsumer[K, V],
      timeout: FiniteDuration = 5 seconds
  ): ConsumerRecords[K, V] = {
    val records: ConsumerRecords[K, V] =
      consumer.poll(new ScalaDurationOps(timeout).toJava)
    records.asScala.foreach(record => {
      logger.debug(
        s"Topic: ${record.topic()}, Partition: ${record.partition()}, Offset: ${record.offset()}, Key: ${record.key()}, Value: ${record.value()}"
      )
    })
    records
  }

  def processRecord(key: String, value: String): Unit
}
