package com.github.voylaf

import munit.CatsEffectSuite
import domain.{Article, Author}

import cats.effect.IO
import fs2.kafka.Headers
import io.circe.generic.auto._

import java.time.LocalDate

class KafkaCodecsCirceSerdeSuite extends CatsEffectSuite {

  val topic = "test-json-topic"

  def createArticle(): Article = {
    val created = LocalDate.now()
    Article("json-001", "Circe test", "Test content", created, Author("Alice"))
  }

  test("KafkaCodecs should serialize and deserialize Article via circeSerdeProvider") {
    val article = createArticle()
    val serdeProvider = KafkaCodecs.circeSerdeProvider[IO, String, Article]

    for {
      serializer <- serdeProvider.valueSerializer.use(IO.pure)
      deserializer <- serdeProvider.valueDeserializer.use(IO.pure)

      bytes <- serializer.serialize(topic, Headers.empty, article)
      restored <- deserializer.deserialize(topic, Headers.empty, bytes)

    } yield {
      assertEquals(restored, article)
    }
  }
}
