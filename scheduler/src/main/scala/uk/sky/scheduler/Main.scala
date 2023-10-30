package uk.sky.scheduler

import java.util.concurrent.TimeUnit

import cats.effect.kernel.Fiber
import cats.effect.std.MapRef
import cats.effect.syntax.all.*
import cats.effect.{Async, IO, IOApp, Sync}
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.java.OtelJava
import uk.sky.scheduler.circe.given
import uk.sky.scheduler.domain.Schedule
import uk.sky.scheduler.kafka.json.jsonDeserializer

import scala.concurrent.duration.*

object Main extends IOApp.Simple {
  def processRecord[F[_] : Async : LoggerFactory](
      record: ConsumerRecord[String, Schedule]
  ): F[ScheduleEvent] =
    for {
      _             <- LoggerFactory[F].getLogger.info(s"Processed message [${record.key}]")
      scheduleEvent <- ScheduleEvent.fromSchedule(record.key)(record.value)
    } yield scheduleEvent

  given scheduleDeserializer[F[_] : Sync]: ValueDeserializer[F, Schedule] =
    jsonDeserializer[F, Schedule]

  given outputSerializer[F[_] : Sync]: ValueSerializer[F, Option[Array[Byte]]] =
    Serializer.identity.option

  def consumerSettings[F[_] : Sync]: ConsumerSettings[F, String, Schedule] =
    ConsumerSettings[F, String, Schedule]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("kafka:9092")
      .withGroupId("kafka-message-scheduler")

  def producerSettings[F[_] : Sync]: ProducerSettings[F, Array[Byte], Option[Array[Byte]]] =
    ProducerSettings[F, Array[Byte], Option[Array[Byte]]]
      .withBootstrapServers("kafka:9092")

  def stream[F[_] : Async : LoggerFactory](
      scheduleRef: MapRef[F, String, Option[DeferredSchedule[F]]]
  ): Stream[F, Unit] =
    KafkaConsumer
      .stream(consumerSettings[F])
      .subscribeTo("schedules")
      .records
      .mapAsync(25) { committable =>
        processRecord(committable.record).flatTap { scheduleEvent =>
          for {
            scheduled <- scheduledFiber(scheduleEvent)
            _         <- scheduleRef.setKeyValue(scheduleEvent.id, scheduled)
          } yield ()
        }.map { scheduleEvent =>
          val record = ScheduleEvent.toProducerRecord(scheduleEvent)
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

  type DeferredSchedule[F[_]] = Fiber[F, Throwable, Unit]

  def scheduleRef[F[_] : Sync]: F[MapRef[F, String, Option[DeferredSchedule[F]]]] =
    MapRef.ofScalaConcurrentTrieMap[F, String, DeferredSchedule[F]]

  def scheduledFiber[F[_] : Async : LoggerFactory](scheduleEvent: ScheduleEvent): F[DeferredSchedule[F]] = {
    val logger = LoggerFactory.getLogger[F]

    val deferredExecution: F[Unit] = logger.info(s"Scheduled $scheduleEvent")

    val deferred = for {
      _        <- logger.info(s"Deferring schedule of $scheduleEvent")
      now      <- Async[F].realTimeInstant
      delay     = Math.max(0, scheduleEvent.time - now.toEpochMilli)
      duration  = Duration(delay, TimeUnit.MILLISECONDS)
      deferred <- Async[F].delayBy(deferredExecution, duration)
    } yield deferred

    deferred.start
  }

  override def run: IO[Unit] =
    for {
      otel4s                 <- OtelJava.global[IO]
      given LoggerFactory[IO] = Slf4jFactory.create[IO]
      scheduleRef            <- scheduleRef[IO]
      _                      <- stream[IO](scheduleRef).compile.drain
    } yield ()

}
