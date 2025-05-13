# Kafka FS2 Scala Project

This project demonstrates a Kafka **Producer** and **Consumer** written in **Scala** using **FS2**. It supports pluggable serialization via **Circe/JSON** or **Avro** with Confluent Schema Registry. Serialization format is configurable via `.conf`.

## Motivation
This project aims to demonstrate a clean, testable Kafka setup in Scala with full support for multiple serialization formats and a functional streaming architecture.

---

## Features

- ✅ Kafka **Producer** via `fs2.kafka.KafkaProducer`
- ✅ Kafka **Consumer** via `fs2.kafka.KafkaConsumer`
- ✅ Swappable serialization:  
  - **Circe (JSON)**
  - **Avro (Confluent)**
- ✅ Message generator using `FancyGenerator`
- ✅ Lazy, streaming design with `Stream[IO, _]`
- ✅ Configuration via Typesafe config (`.conf`)
- ✅ Logging using `slf4j` + `logback`
- ✅ Schema Registry integration for Avro
- ✅ Chunked, parallel streaming with offset commits

---

## Configuration (`kafka-intro.conf`)

```hocon
bootstrap.servers = "localhost:9092"
group.id = "article-consumer-group"
chunk-size = 100
parallelism = 4
serde-format = "avro" // or "circe"
schema.registry.url = "http://localhost:8081"
```

---

## Running the Project
### Producer
*sbt runMain ArticleJsonStringProducerFs2*

The producer uses FancyGenerator.withSeed(seed) to lazily create articles and publish them to Kafka.

### Consumer
*sbt runMain ArticleJsonStringConsumerFs2*

The consumer reads messages from Kafka (either Article or AvroArticle), logs them, and commits offsets in batches.

---

## How Serialization Works
The serialization format (SerdeFormat) is parsed from config.

A type-safe KafkaSerdeProvider supplies serializers/deserializers.

Circe requires an implicit Decoder[A]; Avro does not.

The stream function is generic and safely avoids Decoder[A] requirements for Avro by using overloads or optional implicits.

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
