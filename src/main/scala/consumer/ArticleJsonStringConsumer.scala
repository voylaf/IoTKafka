package com.github.voylaf
package consumer

import producer.ArticleJsonStringProducer.jsonMapper

import org.apache.kafka.clients.consumer.KafkaConsumer

import scala.concurrent.duration.DurationInt
import scala.jdk.javaapi.CollectionConverters.asJavaCollection
import scala.language.postfixOps

object ArticleJsonStringConsumer
    extends App
    with ConsumerUtils[Article]
    with JsonStringDeserializer[Article] {

  private val (config, topic) = ConsumerConfig.getConfig("kafka-intro.conf")

  private val consumer =
    new KafkaConsumer(config, keyDeserializer, valueDeserializer)

  consumer.subscribe(asJavaCollection(List(topic)))

  while (true) {
    val messages = pool(consumer, 1 seconds)
    for ((_, value) <- messages) {
      val article = fromJsonString(value)
      logger.info(
        s"New article received. Title: ${article.title}.  Author: ${article.author.name} "
      )
    }
    consumer.commitAsync()
  }

}
