package com.github.voylaf
package producer

import org.apache.kafka.clients.producer.KafkaProducer

object ArticleJsonStringProducer
    extends App
    with ProducerUtils[Article]
    with JsonStringSerializer[Article] {

  LoggingSetup.init()

  private val (config, topic, seed) = ProducerConfig.getConfig("kafka-intro.conf")

  private val producer =
    new KafkaProducer[String, String](config, keySerializer, valueSerializer)

  val articles = FancyGenerator.withSeed(seed).articles

  for (article <- articles) {
    produce(producer, topic, article.id, article.toJsonString)
  }

  producer.close()
}
