package com.github.voylaf

trait SerdeSupport[F[_], A] {
  def circe: KafkaSerdeProvider[F, String, A]
  def avro(schemaRegistryUrl: String): KafkaSerdeProvider[F, String, A]
}
