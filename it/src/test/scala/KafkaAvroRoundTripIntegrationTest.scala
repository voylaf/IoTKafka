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

import com.github.dockerjava.api.model.{ExposedPort, PortBinding, Ports}
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import org.testcontainers.containers.wait.strategy.Wait

import scala.util.chaining.scalaUtilChainingOps

class KafkaAvroRoundTripIntegrationTest extends CatsEffectSuite {

  /*
  The KafkaContainer class (from org.testcontainers:kafka) internally:

  Ignores or overrides KAFKA_ADVERTISED_LISTENERS and KAFKA_LISTENERS if you set them via .withEnv(...).

  It automatically generates listeners like:
    PLAINTEXT://localhost:<random_mapped_port>,BROKER://<container_hostname>:9093
  This is done to ensure Kafka works correctly both inside the Testcontainers environment and externally.

  Workaround: do not use KafkaContainer, but use GenericContainer manually instead.
  */

  class FixedGenericContainer(image: DockerImageName)
      extends GenericContainer[FixedGenericContainer](image)

  val topic = "integration-articles"

  val network: Network = Network.newNetwork()

  lazy val uuid: String = UUID.randomUUID.toString

  lazy val kafkaContainer: FixedGenericContainer = {
    new FixedGenericContainer(
      DockerImageName.parse("confluentinc/cp-kafka:7.9.1")
    )
      .withNetwork(network)
      .withNetworkAliases("kafka")
      .withEnv(
        Map(
          "KAFKA_NODE_ID"                                  -> "1",
          "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP"           -> "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT,EXTERNAL:PLAINTEXT",
          "KAFKA_ADVERTISED_LISTENERS"                     -> "PLAINTEXT://kafka:9092,EXTERNAL://localhost:29094",
          "KAFKA_LISTENERS"                                -> "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093,EXTERNAL://0.0.0.0:29094",
          "KAFKA_INTER_BROKER_LISTENER_NAME"               -> "PLAINTEXT",
          "KAFKA_CONTROLLER_LISTENER_NAMES"                -> "CONTROLLER",
          "KAFKA_PROCESS_ROLES"                            -> "broker,controller",
          "KAFKA_CONTROLLER_QUORUM_VOTERS"                 -> "1@kafka:9093",
          "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR"         -> "1",
          "KAFKA_TRANSACTION_STATE_LOG_MIN_ISR"            -> "1",
          "KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR" -> "1",
          "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS"         -> "0",
          "CLUSTER_ID"                                     -> uuid,
          "KAFKA_DEBUG"                                    -> "true",
          "KAFKA_LOG4J_ROOT_LOGLEVEL"                      -> "DEBUG"
        ).asJava
      )
      .withExposedPorts(29094)
      /*
      Kafka inside the container advertises its address to clients.
      But localhost:29094 is not mapped to the host — Testcontainers chose a random port => Kafka lies to clients, and they can't connect.
      We explicitly map port 29094 to the host in kafkaContainer:
       */
      .withCreateContainerCmdModifier { cmd =>
        cmd.withPortBindings(new PortBinding(Ports.Binding.bindPort(29094), ExposedPort.tcp(29094)))
      }
      .waitingFor(
        Wait.forListeningPorts(29094).withStartupTimeout(Duration.ofSeconds(60))
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
          "SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS" -> "kafka:9092",
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
    val bootstrapServers  = s"localhost:29094"

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
