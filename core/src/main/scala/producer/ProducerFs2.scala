package com.github.voylaf
package producer

import cats.effect.{ExitCode, IO, IOApp}
import com.typesafe.scalalogging.StrictLogging

object ProducerFs2 extends IOApp with StrictLogging {
  LoggingSetup.init()

  override def run(args: List[String]): IO[ExitCode] = {
    ArticleProducerProgram.run(ProducerConfig.load(ProducerConfig.getConfig("kafka-intro.conf")))
  }
}
