ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

val jacksonVersion = "2.19.0"

lazy val root = (project in file("."))
  .settings(
    name := "IoTKafka",
    idePackagePrefix := Some("com.github.voylaf"),
    resolvers += "confluent" at "https://packages.confluent.io/maven/",
    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % "7.9.0-ce",
      "com.sksamuel.avro4s" %% "avro4s-core" % "4.1.2",
      "io.confluent" % "kafka-avro-serializer" % "7.5.1",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion,
      "com.github.pureconfig" %% "pureconfig" % "0.17.9",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "ch.qos.logback" % "logback-classic" % "1.5.18",
    )
  )
