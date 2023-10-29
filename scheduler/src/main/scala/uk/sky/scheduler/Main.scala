package uk.sky.scheduler

import cats.Applicative
import cats.effect.{Async, IO, IOApp, Sync}
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.java.OtelJava

import scala.concurrent.duration.*

object Main extends IOApp.Simple {
  def processRecord[F[_] : Applicative : LoggerFactory](record: ConsumerRecord[String, String]): F[(String, String)] =
    for {
      _ <- LoggerFactory[F].getLogger.info(s"Processed message [${record.key}]")
    } yield record.key -> record.value

  def consumerSettings[F[_] : Sync]: ConsumerSettings[F, String, String] =
    ConsumerSettings[F, String, String]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("kafka:9092")
      .withGroupId("group")

  def producerSettings[F[_] : Sync]: ProducerSettings[F, String, String] =
    ProducerSettings[F, String, String]
      .withBootstrapServers("kafka:9092")

  def stream[F[_] : Async : LoggerFactory]: Stream[F, Unit] =
    KafkaConsumer
      .stream(consumerSettings[F])
      .subscribeTo("schedules")
      .records
      .mapAsync(25) { committable =>
        processRecord(committable.record).map { case (key, value) =>
          val record = ProducerRecord("output-topic", key, value)
          committable.offset -> ProducerRecords.one(record)
        }
      }
      .through { offsetsAndProducerRecords =>
        KafkaProducer.stream(producerSettings[F]).flatMap { producer =>
          offsetsAndProducerRecords.evalMap { case (offset, producerRecord) =>
            LoggerFactory[F].getLogger.info(s"Producing messages [${producerRecord.map(_.key)}]") *>
              producer
                .produce(producerRecord)
                .map(_.as(offset))
          }.parEvalMapUnbounded(identity)
        }
      }
      .through(commitBatchWithin(500, 15.seconds))

  override def run: IO[Unit] =
    for {
      otel4s                 <- OtelJava.global[IO]
      given LoggerFactory[IO] = Slf4jFactory.create[IO]
      _                      <- stream[IO].compile.drain
    } yield ()

}
