name := "outbox"
val Http4sVersion = "0.22.7"
val doobeVerson = "0.13.3"
ThisBuild / version := "0.1"
ThisBuild / scalaVersion := "2.13.7"
ThisBuild / libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core" % doobeVerson,
  "org.tpolecat" %% "doobie-hikari" % doobeVerson,
  "org.tpolecat" %% "doobie-postgres" % doobeVerson, // Postgres driver 42.2.23 + type mappings.
  "org.tpolecat" %% "doobie-quill" % doobeVerson, // Support for Quill 3.7.2
  "org.http4s" %% "http4s-ember-server" % "0.22.7",
  "org.http4s" %% "http4s-ember-client" % Http4sVersion,
  "org.http4s" %% "http4s-circe" % Http4sVersion,
  "org.http4s" %% "http4s-dsl" % Http4sVersion,
  "io.circe" %% "circe-generic" % "0.14.1",
  "ch.qos.logback" % "logback-classic" % "1.1.11",
  "com.github.fd4s" %% "fs2-kafka" % "1.8.0",
)

lazy val outbox = project.in(file(".")).enablePlugins(FlywayPlugin).settings(
  flywayUrl := "jdbc:postgresql://localhost:5432/postgres",
  flywayUser := "postgres",
  flywayPassword := "postgres",
  flywayLocations += "classpath:db/migration",
)
