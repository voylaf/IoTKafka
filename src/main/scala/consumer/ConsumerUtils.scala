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
  ): Iterable[(K, V)] = {
    val records: ConsumerRecords[K, V] =
      consumer.poll(new ScalaDurationOps(timeout).toJava)
    val messages = records.asScala.map(record => {
      logger.debug(
        s"received record from topic ${record.topic}. Key:  ${record.key} value: ${record.value.toString}"
      )
      (record.key, record.value)
    })
    messages
  }
}
