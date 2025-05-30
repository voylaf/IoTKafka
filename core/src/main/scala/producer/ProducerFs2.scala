package com.github.voylaf
package producer

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging

object ProducerFs2 extends IOApp with StrictLogging {
  LoggingSetup.init()

  override def run(args: List[String]): IO[ExitCode] = {
    val producerConfig = ProducerConfig.load(ProducerConfig.getConfig("kafka-intro.conf"))
    IOProducerProgram.run(producerConfig)(
      FancyGenerator.withSeed(producerConfig.seed).articles take 500
    )
  }
}
