package com.github.voylaf

import consumer.{ConsumerConfig, IOConsumerProgram, KafkaConsumerProgram}
import domain.Article
import metrics.MetricsServer
import producer.{IOProducerProgram, ProducerConfig}

import cats.effect.{IO, Ref, Resource}
import munit.CatsEffectSuite
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.{GenericContainer, Network}
import org.testcontainers.kafka.ConfluentKafkaContainer
import org.testcontainers.utility.DockerImageName

import java.time.{Duration, LocalDate}
import java.util.UUID
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._
import scala.language.postfixOps

class KafkaEndToEndSpec extends CatsEffectSuite {
  override val munitTimeout: FiniteDuration = 120 seconds

  class FixedGenericContainer(image: DockerImageName)
      extends GenericContainer[FixedGenericContainer](image)

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

  lazy val sampleArticle: Article = Article(
    id = UUID.randomUUID().toString,
    title = "Integration Testing with Kafka",
    content = "This is an Avro-based test",
    created = LocalDate.now(),
    author = domain.Author("Test Author")
  )

  def containers: Resource[IO, (ConfluentKafkaContainer, GenericContainer[_])] =
    Resource.make(
      IO {
        kafkaContainer.start()
        schemaRegistryContainer.start()
        (kafkaContainer, schemaRegistryContainer)
      }
    ) { case (kafka, registry) =>
      IO {
        registry.stop()
        kafka.stop()
      }
    }

  test("Article should be produced and consumed end-to-end (avro)") {
    containers.use { case (kafka, registry) =>
      val registryUrl = s"http://${registry.getHost}:${registry.getMappedPort(8081)}"
      val bootstrap   = kafka.getBootstrapServers
      val topic       = "integration-articles"
      val serdeFormat = "avro"
      val avroConsumerConfig = ConsumerConfig(
        groupId = "integration-e2e-test",
        topic = topic,
        bootstrapServers = bootstrap,
        serdeFormat = serdeFormat,
        schemaRegistryUrl = Some(registryUrl),
        chunkSize = 500,
        parallelism = 8,
        prometheusPort = 8091
      )

      val avroProducerConfig = ProducerConfig(
        clientId = "integration-e2e-test",
        topic = topic,
        bootstrapServers = bootstrap,
        serdeFormat = serdeFormat,
        schemaRegistryUrl = Some(registryUrl),
        chunkSize = 100,
        parallelism = 8,
        prometheusPort = 8092,
        seed = 26987,
        sleepingTime = 2 seconds
      )

      def consumerStream(ref: Ref[IO, List[Article]]): Resource[IO, fs2.Stream[IO, Unit]] =
        IOConsumerProgram.stream[Article](
          avroConsumerConfig,
          IOConsumerProgram.serde(avroConsumerConfig),
          (article: Article) => ref.update(_ :+ article)
        )

      for {
        collected <- Ref.of[IO, List[Article]](Nil)
        metrics = MetricsServer.start(avroConsumerConfig.prometheusPort)
        consumerFiber <- KafkaConsumerProgram.runWithMetrics(metrics, consumerStream(collected))
          .timeoutTo(15.seconds, IO.raiseError(new RuntimeException("Consumer timed out")))
          .start
        _ <- IOProducerProgram
          .run(avroProducerConfig)(List(sampleArticle))
          .timeoutTo(10.seconds, IO.raiseError(new RuntimeException("Producer timed out")))

        _ <- IO.sleep(5 seconds)
        _ <- consumerFiber.cancel

        received <- collected.get
        _        <- IO(assertEquals(received.map(_.id), List(sampleArticle.id)))
        _        <- IO(assertEquals(received.head.title, sampleArticle.title))
      } yield ()
    }
  }

  test("Article should be produced and consumed end-to-end (circe)") {
    containers.use { case (kafka, _) =>
      val bootstrap   = kafka.getBootstrapServers
      val topic       = "integration-articles"
      val serdeFormat = "circe"
      val avroConsumerConfig = ConsumerConfig(
        groupId = "integration-e2e-test",
        topic = topic,
        bootstrapServers = bootstrap,
        serdeFormat = serdeFormat,
        schemaRegistryUrl = None,
        chunkSize = 500,
        parallelism = 8,
        prometheusPort = 8091
      )

      val avroProducerConfig = ProducerConfig(
        clientId = "integration-e2e-test",
        topic = topic,
        bootstrapServers = bootstrap,
        serdeFormat = serdeFormat,
        schemaRegistryUrl = None,
        chunkSize = 100,
        parallelism = 8,
        prometheusPort = 8092,
        seed = 26987,
        sleepingTime = 2 seconds
      )

      def consumerStream(ref: Ref[IO, List[Article]]): Resource[IO, fs2.Stream[IO, Unit]] =
        IOConsumerProgram.stream[Article](
          avroConsumerConfig,
          IOConsumerProgram.serde(avroConsumerConfig),
          (article: Article) => ref.update(_ :+ article)
        )

      for {
        collected <- Ref.of[IO, List[Article]](Nil)
        metrics = MetricsServer.start(avroConsumerConfig.prometheusPort)
        consumerFiber <- KafkaConsumerProgram.runWithMetrics(metrics, consumerStream(collected))
          .timeoutTo(15.seconds, IO.raiseError(new RuntimeException("Consumer timed out")))
          .start
        _ <- IOProducerProgram
          .run(avroProducerConfig)(List(sampleArticle))
          .timeoutTo(10.seconds, IO.raiseError(new RuntimeException("Producer timed out")))

        _ <- IO.sleep(5 seconds)
        _ <- consumerFiber.cancel

        received <- collected.get
        _        <- IO(assertEquals(received.map(_.id), List(sampleArticle.id)))
        _        <- IO(assertEquals(received.head.title, sampleArticle.title))
      } yield ()
    }
  }
}
