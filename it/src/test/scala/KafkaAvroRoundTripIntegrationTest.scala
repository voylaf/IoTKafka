package com.github.voylaf

import cats.effect.IO
import fs2.kafka.KafkaProducer
import munit.CatsEffectSuite
import org.testcontainers.containers.{GenericContainer, Network}
import org.testcontainers.utility.DockerImageName

import java.time.{Duration, LocalDate}
import java.util.UUID
import scala.jdk.CollectionConverters._
import avro.{Article => AvroArticle}
import domain.Article

import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.kafka.ConfluentKafkaContainer

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.postfixOps
import scala.util.chaining.scalaUtilChainingOps

class KafkaAvroRoundTripIntegrationTest extends CatsEffectSuite {
  override val munitIOTimeout: FiniteDuration = 120 seconds

  class FixedGenericContainer(image: DockerImageName)
      extends GenericContainer[FixedGenericContainer](image)

  val topic = "integration-articles"

  val network: Network = Network.newNetwork()

  lazy val uuid: String = UUID.randomUUID.toString

  /*
  The KafkaContainer and ConfluentKafkaContainer classes internally:

  Ignore or overrides KAFKA_ADVERTISED_LISTENERS and KAFKA_LISTENERS if you set them via .withEnv(...).

  It automatically generates listeners like:
    PLAINTEXT://localhost:<random_mapped_port>,BROKER://<container_hostname>:9093
  This is done to ensure Kafka works correctly both inside the Testcontainers environment and externally.
   */

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

  override def beforeAll(): Unit = {
    kafkaContainer.start()
    schemaRegistryContainer.start()
  }

  override def afterAll(): Unit = {
    schemaRegistryContainer.stop()
    kafkaContainer.stop()
  }

  test("Avro Article round-trips through Kafka using Schema Registry") {
    val schemaRegistryUrl = s"http://localhost:${schemaRegistryContainer.getMappedPort(8081)}"
    val bootstrapServers  = kafkaContainer.getBootstrapServers

    val article = Article(
      id = UUID.randomUUID().toString,
      title = "Integration Testing with Kafka",
      content = "This is an Avro-based test.",
      created = LocalDate.now(),
      author = domain.Author("Test Author")
    )

    val avroArticle: AvroArticle = Article.toAvroArticle(article)

    val serdeProvider = KafkaCodecs.avroSerdeProvider[IO, String, AvroArticle](schemaRegistryUrl)

    val produceAndConsume: IO[AvroArticle] = for {
      producer <- KafkaProducer
        .resource(KafkaCodecs.producerSettings(bootstrapServers)(serdeProvider))
        .use { producer =>
          val record = fs2.kafka.ProducerRecord(topic, avroArticle.id, avroArticle)
          producer.produceOne(record).flatten
        }

      consumer <- KafkaCodecs
        .consumerSettings("test-group", bootstrapServers)(serdeProvider)
        .pipe(KafkaUtils.consumeOneRecord[IO, String, AvroArticle](topic))
    } yield consumer

    produceAndConsume.map { decoded =>
      assertEquals(decoded, avroArticle)
    }
  }
}
