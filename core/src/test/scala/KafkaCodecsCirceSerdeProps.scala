package com.github.voylaf

import domain.{Article, Author}

import fs2.kafka._
import io.circe.generic.auto._
import munit.DisciplineSuite
import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen}
import cats.effect.IO
import cats.effect.unsafe.implicits.global

import java.time.LocalDate

class KafkaCodecsCirceSerdeProps extends DisciplineSuite {

  val topic = "test-json-topic"

  implicit val arbAuthor: Arbitrary[Author] = Arbitrary(
    for {
      name <- Gen.alphaStr
    } yield Author(name)
  )

  val arbLocalDate: Arbitrary[LocalDate] = Arbitrary {
    for {
      days <- Gen.choose(-10000, 10000)
    } yield LocalDate.now().plusDays(days)
  }

  implicit val arbArticle: Arbitrary[Article] = Arbitrary(
    for {
      id      <- Gen.uuid.map(_.toString)
      title   <- Gen.alphaStr
      content <- Gen.alphaStr
      created <- arbLocalDate.arbitrary
      author  <- arbAuthor.arbitrary
    } yield Article(id, title, content, created, author)
  )

  property("KafkaCodecs.circeSerdeProvider should round-trip Article") {
    forAll { (article: Article) =>
      val serdeProvider = KafkaCodecs.circeSerdeProvider[IO, String, Article]

      val result = for {
        serializer   <- serdeProvider.valueSerializer.use(IO.pure)
        deserializer <- serdeProvider.valueDeserializer.use(IO.pure)
        bytes        <- serializer.serialize(topic, Headers.empty, article)
        decoded      <- deserializer.deserialize(topic, Headers.empty, bytes)
      } yield decoded

      assertEquals(result.unsafeRunSync(), article)
    }
  }
}
