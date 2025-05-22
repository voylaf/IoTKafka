ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.16"

val jacksonVersion        = "2.19.0"
val circeVersion          = "0.14.13"
val avro4sKafkaVersion    = "4.1.2"
val TestcontainersVersion = "1.21.0"
val prometheusVersion     = "0.16.0"
val http4sVersion         = "0.23.30"

lazy val commonSettings = Seq(
  idePackagePrefix := Some("com.github.voylaf"),
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
  resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
  resolvers += "confluent" at "https://packages.confluent.io/maven/",
  libraryDependencies ++= Seq(
    "org.apache.kafka"            % "kafka-clients"         % "7.9.0-ce",
    "com.sksamuel.avro4s"        %% "avro4s-core"           % avro4sKafkaVersion,
    "org.apache.avro"             % "avro"                  % "1.12.0",
    "io.confluent"                % "kafka-avro-serializer" % "7.5.1",
    "com.github.pureconfig"      %% "pureconfig"            % "0.17.9",
    "com.typesafe.scala-logging" %% "scala-logging"         % "3.9.5",
    "ch.qos.logback"              % "logback-classic"       % "1.5.18",
    "com.github.fd4s"            %% "fs2-kafka"             % "3.7.0",
    "co.fs2"                     %% "fs2-core"              % "3.12.0",
    "org.typelevel"              %% "cats-effect"           % "3.6.1",
    "io.prometheus"               % "simpleclient"          % prometheusVersion,
    "io.prometheus"               % "simpleclient_common"   % prometheusVersion,
    "io.prometheus"               % "simpleclient_hotspot"  % prometheusVersion,
    "org.http4s"                 %% "http4s-ember-server"   % http4sVersion,
    "org.http4s"                 %% "http4s-dsl"            % http4sVersion,
    "org.scalameta"              %% "munit"                 % "1.1.1" % Test,
    "org.scalameta"              %% "munit-scalacheck"      % "1.1.0" % Test,
    "org.typelevel"              %% "munit-cats-effect"     % "2.1.0" % Test,
    "org.typelevel"              %% "discipline-munit"      % "2.0.0" % Test
  ),
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser"
  ).map(_ % circeVersion)
)

lazy val root = (project in file("."))
  .aggregate(core, it)
  .settings(
    name := "IoTKafka-root"
  )

lazy val core = (project in file("core"))
  .settings(
    commonSettings,
    name := "IoTKafka-core",
    Compile / sourceGenerators += (Compile / avroScalaGenerateSpecific).taskValue
  )
  .enablePlugins(SbtAvrohugger)

lazy val it = (project in file("it"))
  .settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.testcontainers" % "testcontainers"            % TestcontainersVersion % Test,
      "org.testcontainers" % "kafka"                     % TestcontainersVersion % Test,
      "com.dimafeng"      %% "testcontainers-scala-core" % "0.43.0"
    ),
    name := "IoTKafka-it"
  )
  .dependsOn(core)
