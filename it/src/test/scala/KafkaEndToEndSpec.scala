package com.github.voylaf

import metrics.MetricsServer

import cats.effect.{IO, Resource}
import munit.CatsEffectSuite
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.{GenericContainer, Network}
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

import java.time.Duration
import java.util.UUID
import scala.jdk.CollectionConverters._

class KafkaEndToEndSpec extends CatsEffectSuite {

  class FixedGenericContainer(image: DockerImageName)
      extends GenericContainer[FixedGenericContainer](image)

  val topic = "integration-e2e-test"

  val network: Network = Network.newNetwork()

  lazy val uuid: String = UUID.randomUUID.toString

  lazy val kafkaContainer: ConfluentKafkaContainer = {
    new ConfluentKafkaContainer("confluentinc/cp-kafka:7.9.1")
      .withNetwork(network)
      .withNetworkAliases("kafka")
      .withExposedPorts(9092)
      .waitingFor(
        Wait.forListeningPorts(9092).withStartupTimeout(Duration.ofSeconds(60))
      )
      .withReuse(false)
  }

  lazy val schemaRegistryContainer: FixedGenericContainer = {
    new FixedGenericContainer(
      DockerImageName
        .parse("confluentinc/cp-schema-registry:7.9.1")
    )
      .dependsOn(kafkaContainer)
      .withNetwork(network)
      .withEnv(
        Map(
          "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> "kafka:9093",
          "SCHEMA_REGISTRY_HOST_NAME"                    -> "schema-registry",
          "SCHEMA_REGISTRY_LISTENERS"                    -> "http://0.0.0.0:8081"
        ).asJava
      )
      .withExposedPorts(8081)
      .withReuse(false)
      .withNetworkAliases("schema-registry")
      .waitingFor(Wait
        .forHttp("/subjects")
        .forPort(8081)
        .withStartupTimeout(Duration.ofSeconds(60)))
  }

  // consumer job
  lazy val metrics: Resource[IO, Unit] = MetricsServer.start(8091)

  override def beforeAll(): Unit = {
    kafkaContainer.start()
    schemaRegistryContainer.start()
  }

  override def afterAll(): Unit = {
    schemaRegistryContainer.stop()
    kafkaContainer.stop()
  }

  test("Article should be produced and consumed end-to-end (avro)") {}
}
