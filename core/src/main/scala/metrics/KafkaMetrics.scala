package com.github.voylaf
package metrics

import io.prometheus.client.Counter

object KafkaMetrics {
  val producedMessages: Counter = Counter.build()
    .name("kafka_messages_produced_total")
    .help("Total number of produced messages")
    .register()

  val consumedMessages: Counter = Counter.build()
    .name("kafka_messages_consumed_total")
    .help("Total number of consumed messages")
    .register()

  val consumerErrors: Counter = Counter.build()
    .name("kafka_consumer_errors_total")
    .help("Total number of failed Kafka consume attempts")
    .register()

  val producerErrors: Counter = Counter.build()
    .name("kafka_producer_errors_total")
    .help("Total number of failed Kafka produce attempts")
    .register()
}
