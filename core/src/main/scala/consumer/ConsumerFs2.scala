package com.github.voylaf
package consumer

import domain.Article

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging

object ConsumerFs2 extends IOApp with StrictLogging {
  LoggingSetup.init()

  override def run(args: List[String]): IO[ExitCode] = {
    IOConsumerProgram.run[Article](ConsumerConfig.load(ConsumerConfig.getConfig("kafka-intro.conf")))
  }
}
