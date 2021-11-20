package outbox

import cats.effect._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import cats.syntax.all._
import cats.~>
import fs2.Stream
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.Logger
import doobie.{ConnectionIO, _}
import doobie.implicits._
import doobie.postgres._
import doobie.postgres.implicits._
import fs2.kafka._
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.duration.DurationInt

//noinspection TypeAnnotation
object Api extends IOApp {
  val topic = "updates"
  lazy val transactor: ConnectionIO ~> IO = Transactor.fromDriverManager[IO](
    driver = "org.postgresql.Driver",
    url = "jdbc:postgresql://localhost:5432/postgres",
    user = "postgres",
    pass = "postgres",
  ).trans

  lazy val logger = LoggerFactory.getLogger("Main")

  lazy val consumerSettings =
    ConsumerSettings[IO, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("localhost:29092")
      .withGroupId("outbox")

  lazy val producerSettings =
    ProducerSettings[IO, String, String]
      .withBootstrapServers("localhost:29092")
      .withDeliveryTimeout(200.milliseconds)

  lazy val producer: KafkaProducer[IO, String, String] = KafkaProducer.resource(producerSettings).allocated.unsafeRunSync()._1

  lazy val consumerStream =
    KafkaConsumer.stream(consumerSettings)
      .subscribeTo(topic)
      .records
      .mapAsync(25) { committable =>
        import committable.record
        val id = UUID.fromString(record.key)
        transactor(sql"insert into processed_texts (id) values ($id);".update.run) *> IO.delay {
          logger.info(s"Received record with key=${record.key} and value=${record.value}")
        }.as(committable.offset)
      }
      .through(commitBatchWithin(500, 15.seconds))

  def routes: HttpRoutes[IO] = {
    val dsl = new Http4sDsl[IO] {}
    import dsl._
    HttpRoutes.of[IO] {
      case req@POST -> Root / "sync" / author =>
        for {
          bytes <- req.body.compile.toVector
          str = new String(bytes.toArray)
          id = UUID.randomUUID()
          _ <- transactor(for {
            _ <- sql"insert into texts (id, author, text) values ($id, $author, $str);".update.run
            _ <- sql"insert into processed_texts (id) values ($id);".update.run
          } yield ())
          res <- Ok(author + ": " + str)
        } yield res
      case req@POST -> Root / "outbox" / author =>
        for {
          bytes <- req.body.compile.toVector
          str = new String(bytes.toArray)
          id = UUID.randomUUID()

          _ <- transactor(for {
            _ <- sql"insert into texts (id, author, text) values ($id, $author, $str);".update.run
            _ <- sql"insert into outbox (kafka_topic, kafka_key, kafka_value) values ($topic, ${id.toString}, ${id.toString});".update.run
          } yield ())
          res <- Ok(author + ": " + str)
        } yield res
      case req@POST -> Root / "direct" / author =>
        for {
          bytes <- req.body.compile.toVector
          str = new String(bytes.toArray)
          id = UUID.randomUUID()

          _ <- transactor(for {
            _ <- sql"insert into texts (id, author, text) values ($id, $author, $str);".update.run
            _ <- producer.produce(ProducerRecords(Vector(ProducerRecord(topic, id.toString, id.toString)))).flatten.to[ConnectionIO]
          } yield ())
          res <- Ok(author + ": " + str)
        } yield res
    }
  }

  def apiStream: Stream[IO, Nothing] = {
    val httpApp = routes.orNotFound
    val finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)
    Stream.resource(
      EmberServerBuilder.default[IO]
        .withHost("0.0.0.0")
        .withPort(8080)
        .withHttpApp(finalHttpApp)
        .build >>
        Resource.eval(IO.never)
    ).drain
  }

  def run(args: List[String]): IO[ExitCode] = {
    (for {
      _ <- consumerStream.concurrently(apiStream).compile.resource.drain
      _ = logger.info("Started")
    } yield ()).use(_ => IO.never)
  }
}
