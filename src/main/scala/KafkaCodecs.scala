package com.github.voylaf

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka._
import io.circe.{parser, Decoder, Encoder}
import io.circe.syntax._

object KafkaCodecs extends StrictLogging {

  def circeJsonSerializer[A: Encoder]: Serializer[IO, A] =
    Serializer.string[IO].contramap { value =>
      val json = value.asJson.noSpaces
      logger.debug(s"Serializing value: $json")
      json
    }

  implicit def keySerializer[A: Encoder]: Resource[IO, KeySerializer[IO, A]] =
    Resource.pure(circeJsonSerializer[A])

  implicit def valueSerializer[A: Encoder]: Resource[IO, ValueSerializer[IO, A]] =
    Resource.pure(circeJsonSerializer[A])

  def circeJsonDeserializer[A: Decoder]: Deserializer[IO, A] =
    Deserializer.string[IO].flatMap { str =>
      parser.decode[A](str) match {
        case Right(value) =>
          logger.debug(s"Deserialized value: $value")
          Deserializer.const(value)
        case Left(error) =>
          logger.error(s"Deserialization failed for input: $str", error)
          Deserializer.fail(new RuntimeException(s"Deserialization error: $error"))
      }
    }

  implicit def keyDeserializer[A: Decoder]: Resource[IO, KeyDeserializer[IO, A]] =
    Resource.pure(circeJsonDeserializer[A])

  implicit def valueDeserializer[A: Decoder]: Resource[IO, ValueDeserializer[IO, A]] =
    Resource.pure(circeJsonDeserializer[A])

  def producerSettings[K: Encoder, V: Encoder](
      bootstrapServers: String
  ): ProducerSettings[IO, K, V] =
    ProducerSettings[IO, K, V]
      .withBootstrapServers(bootstrapServers)
      .withSerializers(
        keySerializer[K],
        valueSerializer[V]
      )

  def consumerSettings[K: Decoder, V: Decoder](
      groupId: String,
      bootstrapServers: String
  ): ConsumerSettings[IO, K, V] =
    ConsumerSettings[IO, K, V]
      .withGroupId(groupId)
      .withBootstrapServers(bootstrapServers)
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withDeserializers(
        keyDeserializer[K],
        valueDeserializer[V]
      )
}
