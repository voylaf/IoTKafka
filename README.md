[![CI Build](https://github.com/voylaf/IoTKafka/actions/workflows/ci-build.yml/badge.svg)](https://github.com/voylaf/IoTKafka/actions/workflows/ci-build.yml)

# Kafka FS2 Scala Project

A modular and testable Kafka application in Scala using FS2, Cats Effect, Avro, Circe, Prometheus metrics, and Testcontainers.

---

## Motivation
This project is designed to demonstrate a clean, scalable Kafka producer/consumer architecture in Scala, supporting:

- ✅ Pluggable serialization: **Avro** or **Circe**
- ✅ Fully testable with **unit**, **integration**, and **end-to-end tests**
- ✅ **Prometheus** metrics endpoint
- ✅ Modular design for extending to multiple domain types
- ✅ Built using `fs2-kafka`, `cats-effect`, `pureconfig`, and `testcontainers-scala`

---

## Features

| Feature                   | Description                                         |
|---------------------------|-----------------------------------------------------|
| **Producer**              | FS2-based, chunked, parallel Kafka producer         |
| **Consumer**              | Streamed and batched Kafka consumer with metrics    |
| **Serde Support**         | Supports Circe and Avro, pluggable via config       |
| **Metrics**               | Exposes `/metrics` endpoint for Prometheus         |
| **Integration Tests**     | Uses Testcontainers to test Kafka + Schema Registry|
| **Typeclass Abstraction** | `LoggingSupport[A]`, `SerdeSupport[F, A]`           |

---

## Typeclasses

### `SerdeSupport[F[_], A]`

```scala
trait SerdeSupport[F[_], A] {
  def circe: KafkaSerdeProvider[F, String, A]
  def avro(schemaRegistryUrl: String): KafkaSerdeProvider[F, String, A]
}
```

Used to provide serializers/deserializers based on config. Example:

```scala
implicit def articleSerdeSupport[F[_]: Sync]: SerdeSupport[F, Article] =
    new SerdeSupport[F, Article] {
      def circe: KafkaSerdeProvider[F, String, Article] =
        KafkaCodecs.circeSerdeProvider[F, String, Article]

      def avro(schemaRegistryUrl: String): KafkaSerdeProvider[F, String, Article] =
        KafkaCodecs.avroSerdeProvider[F, String, AvroArticle](schemaRegistryUrl)
          .contramap[Article](
            to = Article.toAvroArticle,
            from = Article.fromAvroArticle
          )
    }
```

### `LoggingSupport[A]`

```scala
trait LoggingSupport[A] {
  def logMessageRecieved(a: A): String
  def logMessageSended(a: A): String
  def key(a: A): String
}
```
---

## Configuration (`kafka-intro.conf`)

```hocon
bootstrap-servers = "localhost:9094"
topic = "scala-articles"
// circe or avro
serde-format = "avro"
producer {
  client.id = scala-kafka-producer
  bootstrap.servers = ${bootstrap-servers}
  chunk-size = 100
  parallelism = 4
  serde-format = ${serde-format}
  schema.registry.url = "http://localhost:8081"
  prometheus.port = 8092
  sleeping-time-seconds = 20
  topic = ${topic}
  seed = 20505
}
consumer {
  group.id = scala-kafka-consumer
  bootstrap.servers = ${bootstrap-servers}
  chunk-size = 500
  parallelism = 8
  serde-format = ${serde-format}
  schema.registry.url = "http://localhost:8081"
  prometheus.port = 8091
  topic = ${topic}
}
```

---

## Testing

✅ Unit tests for serialization (using Discipline, munit)

✅ Integration tests for Schema Registry + Avro

✅ E2E tests for real production + consumption of messages

Run all tests:

```bash
sbt test
```

---

## Metrics

Prometheus-compatible metrics endpoint on ports 8091 and 8092:
```
http://localhost:8091/metrics
http://localhost:8092/metrics
```
Exposes:

- kafka_messages_produced_total

- kafka_messages_consumed_total

- kafka_producer_errors_total

- kafka_consumer_errors_total

Prometheus queries are also available on port 9090:
```
http://localhost:9090/query
```

---
## End-to-End Example
See KafkaAvroRoundTripIntegrationTest.scala for how to:

- spin up real Kafka + Schema Registry containers

- produce a message

- consume and assert it with Ref[IO, List[A]]

---

## Build & Run
To run the producer/consumer:
```
sbt "core/runMain com.github.voylaf.consumer.ConsumerFs2"
sbt "core/runMain com.github.voylaf.producer.ProducerFs2"
```

---
## Production Considerations

In this demo, messages are generated via a local generator (FancyGenerator).
In production, messages would likely come from:

- A database query (JDBC, Doobie, etc.)

- An external API

- Another Kafka topic

- A message queue

- Consider extracting a common ArticleSource[F[_]] interface.

---

## Dependencies

- Scala 2.13

- fs2-kafka

- cats-effect

- circe-core / circe-generic

- avro4s

- kafka-avro-serializer

- scala-logging / logback

- Typesafe Config

- prometheus-client

- grafana

- testcontainers

- testcontainers-scala-core

---
## License
MIT — free to use, modify, and contribute.
