package com.github.voylaf

import avro.{Article => AvroArticle}

import cats.effect.IO
import domain.{Article, Author}
import fs2.kafka._
import munit.CatsEffectSuite

import java.time.LocalDate

class KafkaCodecsAvroSerdeSuite extends CatsEffectSuite {

  val topic             = "test-avro-topic"
  val schemaRegistryUrl = "mock://schema-registry"

  val avroConfig: Map[String, AnyRef] = Map(
    "schema.registry.url"  -> schemaRegistryUrl,
    "specific.avro.reader" -> java.lang.Boolean.TRUE
  )

  def createArticle(): AvroArticle = {
    val author  = Author("Bob")
    val created = LocalDate.now()
    Article.toAvroArticle(Article("avro-001", "Avro test", "Test content", created, author))
  }

  test("KafkaCodecs should serialize and deserialize AvroArticle correctly") {
    val article = createArticle()

    val serdeProvider = KafkaCodecs.avroSerdeProvider[cats.effect.IO, String, AvroArticle](schemaRegistryUrl)

    for {
      serializer   <- serdeProvider.valueSerializer.use(s => IO.pure(s))
      deserializer <- serdeProvider.valueDeserializer.use(d => IO.pure(d))

      bytes    <- serializer.serialize(topic, Headers.empty, article)
      restored <- deserializer.deserialize(topic, Headers.empty, bytes)

    } yield {
      assertEquals(restored, article)
    }
  }
}
