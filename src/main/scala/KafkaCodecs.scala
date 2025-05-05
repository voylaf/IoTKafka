package com.github.voylaf

import cats.effect.{Resource, Sync}
import com.typesafe.scalalogging.StrictLogging
import fs2.kafka._
import io.circe.generic.auto._
import io.circe.{parser, Decoder, Encoder}
import io.circe.syntax._
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.avro.specific.SpecificRecord

sealed trait SerdeFormat

case object Circe extends SerdeFormat

case object Avro extends SerdeFormat

object SerdeFormat {
  def fromString(str: String): Either[String, SerdeFormat] = str.toLowerCase match {
    case "circe" => Right(Circe)
    case "avro"  => Right(Avro)
    case other   => Left(s"Unknown serde format: $other")
  }
}

object SerdeFactory {
  def forFormat[F[_]: Sync, K, V](
      format: SerdeFormat
  )(implicit
      ke: Encoder[K],
      kd: Decoder[K],
      ve: Encoder[V],
      vd: Decoder[V]
  ): KafkaSerdeProvider[F, K, V] = format match {
    case Circe => KafkaCodecs.circeSerdeProvider[F, K, V]
    case Avro =>
      throw new IllegalArgumentException("Avro serde requires SpecificRecord. Use different overload.")
  }

  def forAvro[F[_]: Sync, K <: SpecificRecord, V <: SpecificRecord](
      format: SerdeFormat
  ): KafkaSerdeProvider[F, K, V] = format match {
    case Avro => KafkaCodecs.avroSerdeProvider[F, K, V]
    case Circe =>
      throw new IllegalArgumentException("Circe serde requires Encoder/Decoder. Use different overload.")
  }
}

trait KafkaSerdeProvider[F[_], K, V] {
  def keySerializer: Resource[F, KeySerializer[F, K]]
  def valueSerializer: Resource[F, ValueSerializer[F, V]]
  def keyDeserializer: Resource[F, KeyDeserializer[F, K]]
  def valueDeserializer: Resource[F, ValueDeserializer[F, V]]
}

object KafkaCodecs extends StrictLogging {
  def circeSerdeProvider[F[_]: Sync, K: Encoder: Decoder, V: Encoder: Decoder]: KafkaSerdeProvider[F, K, V] =
    new KafkaSerdeProvider[F, K, V] {

      private def circeSerializer[A: Encoder]: Serializer[F, A] =
        Serializer.string[F].contramap { value =>
          val json = value.asJson.noSpaces
          logger.debug(s"Serializing value: $json")
          json
        }

      private def circeJsonDeserializer[A: Decoder]: Deserializer[F, A] =
        Deserializer.string[F].flatMap { str =>
          parser.decode[A](str) match {
            case Right(value) =>
              logger.debug(s"Deserialized value: $value")
              Deserializer.const(value)
            case Left(error) =>
              logger.error(s"Deserialization failed for input: $str", error)
              Deserializer.fail(new RuntimeException(s"Deserialization error: $error"))
          }
        }

      override val keySerializer: Resource[F, KeySerializer[F, K]]     = Resource.pure(circeSerializer[K])
      override val valueSerializer: Resource[F, ValueSerializer[F, V]] = Resource.pure(circeSerializer[V])

      override val keyDeserializer: Resource[F, KeyDeserializer[F, K]]     = Resource.pure(circeJsonDeserializer[K])
      override val valueDeserializer: Resource[F, ValueDeserializer[F, V]] = Resource.pure(circeJsonDeserializer[V])
    }

  def avroSerdeProvider[F[_]: Sync, K <: SpecificRecord, V <: SpecificRecord]: KafkaSerdeProvider[F, K, V] =
    new KafkaSerdeProvider[F, K, V] {
      override val keySerializer: Resource[F, KeySerializer[F, K]] = {
        val delegate = new KafkaAvroSerializer().asInstanceOf[KafkaSerializer[K]]
        Resource.pure(Serializer.delegate[F, K](delegate))
      }
      override val valueSerializer: Resource[F, ValueSerializer[F, V]] = {
        val delegate = new KafkaAvroSerializer().asInstanceOf[KafkaSerializer[V]]
        Resource.pure(Serializer.delegate[F, V](delegate))
      }

      override val keyDeserializer: Resource[F, KeyDeserializer[F, K]] = {
        val delegate = new KafkaAvroDeserializer().asInstanceOf[KafkaDeserializer[K]]
        Resource.pure(Deserializer.delegate[F, K](delegate))
      }

      override val valueDeserializer: Resource[F, ValueDeserializer[F, V]] = {
        val delegate = new KafkaAvroDeserializer().asInstanceOf[KafkaDeserializer[V]]
        Resource.pure(Deserializer.delegate[F, V](delegate))
      }
    }

  def producerSettings[F[_]: Sync, K, V](
      bootstrapServers: String
  )(serde: KafkaSerdeProvider[F, K, V]): ProducerSettings[F, K, V] = {
    implicit val ks: Resource[F, KeySerializer[F, K]]   = serde.keySerializer
    implicit val vs: Resource[F, ValueSerializer[F, V]] = serde.valueSerializer

    ProducerSettings[F, K, V]
      .withBootstrapServers(bootstrapServers)
  }

  def consumerSettings[F[_]: Sync, K: Decoder, V: Decoder](
      groupId: String,
      bootstrapServers: String
  )(serde: KafkaSerdeProvider[F, K, V]): ConsumerSettings[F, K, V] = {
    implicit val kd: Resource[F, KeyDeserializer[F, K]]   = serde.keyDeserializer
    implicit val vd: Resource[F, ValueDeserializer[F, V]] = serde.valueDeserializer

    ConsumerSettings[F, K, V]
      .withGroupId(groupId)
      .withBootstrapServers(bootstrapServers)
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
  }

}
