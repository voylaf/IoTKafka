package com.github.voylaf
package consumer

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging


object ConsumerFs2 extends IOApp with StrictLogging {
  LoggingSetup.init()

  override def run(args: List[String]): IO[ExitCode] = {
    ArticleConsumerProgram.run(ConsumerConfig.load(ConsumerConfig.getConfig("kafka-intro.conf")))
  }
}
