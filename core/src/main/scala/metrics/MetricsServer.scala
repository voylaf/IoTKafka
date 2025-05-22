package com.github.voylaf.metrics

import cats.effect._
import cats.implicits.toFunctorOps
import com.comcast.ip4s.{IpLiteralSyntax, Port}
import io.prometheus.client.exporter.common.TextFormat
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.CollectorRegistry
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.typelevel.ci.CIStringSyntax

import java.io.StringWriter

object MetricsServer {
  def start(port: Int = 8090): Resource[IO, Unit] = {
    val registry = CollectorRegistry.defaultRegistry
    DefaultExports.initialize()

    val routes = HttpRoutes.of[IO] {
      case GET -> Root / "metrics" =>
        val writer = new StringWriter()
        TextFormat.write004(writer, registry.metricFamilySamples())
        Ok(writer.toString, Header.Raw(ci"Content-Type", TextFormat.CONTENT_TYPE_004))
    }

    Port.fromInt(port) match {
      case Some(validPort) =>
        EmberServerBuilder.default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(validPort)
          .withHttpApp(routes.orNotFound)
          .build
          .void
      case None =>
        Resource.eval(IO.raiseError(new IllegalArgumentException(s"Invalid port number: $port")))
    }
  }
}
