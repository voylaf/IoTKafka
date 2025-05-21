package com.github.voylaf

import cats.effect.Async
import fs2.kafka.{ConsumerSettings, KafkaConsumer}

object KafkaUtils {
  def consumeOneRecord[F[_]: Async, K, V](topic: String)(
      settings: ConsumerSettings[F, K, V]
  ): F[V] = {
    KafkaConsumer
      .resource(settings)
      .evalTap(_.subscribeTo(topic))
      .use {
        _.stream
          .head
          .map(_.record.value)
          .compile
          .lastOrError
      }
  }
}
