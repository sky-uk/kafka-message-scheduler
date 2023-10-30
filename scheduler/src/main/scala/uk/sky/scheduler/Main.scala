package uk.sky.scheduler

import cats.effect.std.{MapRef, Queue}
import cats.effect.{Async, IO, IOApp, Sync}
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.java.OtelJava
import uk.sky.scheduler.ScheduleQueue.DeferredSchedule
import uk.sky.scheduler.circe.given
import uk.sky.scheduler.domain.Schedule
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.Event
import uk.sky.scheduler.kafka.json.jsonDeserializer

import scala.concurrent.duration.*

object Main extends IOApp.Simple {
  def toEvent[F[_] : Sync : LoggerFactory](
      cr: CommittableConsumerRecord[F, String, Either[Throwable, Option[Schedule]]]
  ): F[Event[F]] = {
    val logger = LoggerFactory[F].getLogger

    val key    = cr.record.key
    val offset = cr.offset
    val topic  = cr.record.topic

    cr.record.value match {
      case Left(error)           =>
        val scheduleError = ScheduleError.DecodeError(key, error.getMessage)
        logger.info(s"Error decoding [$key] from topic $topic - ${error.getMessage}") *>
          Event.Error(key, scheduleError, offset).pure
      case Right(Some(schedule)) =>
        logger.info(s"Decoded UPDATE for [$key] from topic $topic") *>
          ScheduleEvent.fromSchedule(schedule).map(Event.Update(key, _, offset))
      case Right(None)           =>
        logger.info(s"Decoded DELETE for [$key] from topic $topic") *>
          Event.Delete(key, offset).pure
    }
  }

  given scheduleDeserializer[F[_] : Sync]: ValueDeserializer[F, Either[Throwable, Option[Schedule]]] =
    jsonDeserializer[F, Schedule].option.attempt

  given outputSerializer[F[_] : Sync]: ValueSerializer[F, Option[Array[Byte]]] =
    Serializer.identity.option

  def consumerSettings[F[_] : Sync]: ConsumerSettings[F, String, Either[Throwable, Option[Schedule]]] =
    ConsumerSettings[F, String, Either[Throwable, Option[Schedule]]]
      .withAutoOffsetReset(AutoOffsetReset.Earliest)
      .withBootstrapServers("kafka:9092")
      .withGroupId("kafka-message-scheduler")

  def producerSettings[F[_] : Sync]: ProducerSettings[F, Array[Byte], Option[Array[Byte]]] =
    ProducerSettings[F, Array[Byte], Option[Array[Byte]]]
      .withBootstrapServers("kafka:9092")

  def stream[F[_] : Async : LoggerFactory](
      scheduleRef: MapRef[F, String, Option[DeferredSchedule[F]]],
      queue: Queue[F, ScheduleEvent]
  ): Stream[F, Unit] = {
    val logger = LoggerFactory[F].getLogger

    val scheduleQueue = ScheduleQueue.observed(ScheduleQueue(scheduleRef, queue))

    val consumer: Stream[F, Unit] =
      KafkaConsumer
        .stream(consumerSettings[F])
        .subscribeTo("schedules")
        .records
        .evalMap(toEvent)
        .evalTap {
          case Event.Update(key, schedule, _) => scheduleQueue.schedule(key, schedule)
          case Event.Delete(key, _)           => scheduleQueue.cancel(key)
          case Event.Error(key, _, _)         => scheduleQueue.cancel(key)
        }
        .map(_.offset)
        .through(commitBatchWithin[F](500, 15.seconds))

    /** Construct a Stream from the Queue schedules delay their offering to
      */
    val producer =
      KafkaProducer.stream(producerSettings[F]).flatMap { producer =>
        Stream
          .fromQueueUnterminated(queue)
          .evalMap { scheduleEvent =>
            val producerRecord = ScheduleEvent.toProducerRecord(scheduleEvent)
            logger.info(s"Producing $scheduleEvent") *>
              producer.produce(ProducerRecords.one(producerRecord))
          }
          .parEvalMapUnbounded(identity)
      }

    consumer.drain.merge(producer.void)
  }

  /*
  Goal: Consumer consumes, defers adding an item to the queue until elapsed time. Adds to the MapRef and cancels if delete is sent.

  Producer: Consumes from the queue, produces to Kafka
   */

  override def run: IO[Unit] =
    for {
      otel4s                 <- OtelJava.global[IO]
      given LoggerFactory[IO] = Slf4jFactory.create[IO]
      scheduleRef            <- MapRef.ofScalaConcurrentTrieMap[IO, String, DeferredSchedule[IO]]
      queue                  <- Queue.unbounded[IO, ScheduleEvent]
      _                      <- stream[IO](scheduleRef, queue).compile.drain
    } yield ()

}
