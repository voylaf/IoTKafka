package com.github.voylaf

import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import com.typesafe.scalalogging.LazyLogging

trait ProducerUtils[T] extends LazyLogging {

  val callback: Callback = (metadata: RecordMetadata, exception: Exception) => {
    Option(exception).fold {
      logger.info(
        s"Message successfully sent to topic=${metadata.topic()}, partition=${metadata.partition()}, offset=${metadata.offset()}, timestamp=${metadata.timestamp()}"
      )
    } { ex =>
      logger.error(s"Error while producing message: ${ex.getMessage}", ex)
    }
  }

  def produce[K, V](
      producer: KafkaProducer[K, V],
      topic: String,
      key: K,
      value: V
  ): Unit = {
    val record = new ProducerRecord(topic, key, value)
    producer.send(record, callback)
  }

}
