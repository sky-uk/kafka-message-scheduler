package uk.sky.scheduler.grpc

import java.util.concurrent.TimeUnit

import cats.effect.MonadCancelThrow
import cats.syntax.all.*
import io.grpc.Status
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.otel.Otel
import uk.sky.scheduler.proto.v1.{ScheduleEvent, ScheduleRequest, SchedulerFs2Grpc}
import uk.sky.scheduler.repository.Repository

class SchedulerGrpc[F[_] : MonadCancelThrow](repository: Repository[F, String, ScheduleEvent])
    extends SchedulerFs2Grpc[F, Metadata] {
  override def schedule(request: ScheduleRequest, ctx: Metadata): F[ScheduleEvent] =
    repository.get(request.id).flatMap {
      case Some(scheduleEvent) =>
        scheduleEvent
          .transformInto[ScheduleEvent]
          .pure[F]

      case None =>
        Status.NOT_FOUND
          .withDescription(s"[${request.id}] not found")
          .asRuntimeException()
          .raiseError
    }
}

object Scheduler {
  def observed[F[_] : MonadCancelThrow : Otel](delegate: SchedulerGrpc[F]): F[SchedulerFs2Grpc[F, Metadata]] =
    Otel[F].meter.histogram[Double]("scheduler-grpc-request").create.map { histogram =>
      val logger = Otel[F].loggerFactory.getLogger

      new SchedulerFs2Grpc[F, Metadata] {
        override def schedule(request: ScheduleRequest, ctx: Metadata): F[ScheduleEvent] =
          Otel[F].tracer.joinOrRoot(ctx) {
            histogram.recordDuration(TimeUnit.MILLISECONDS).surround {
              Otel[F].tracer.span("scheduler-grpc-request").surround {
                logger.debug(s"SchedulerGrpc#schedule with request $request and context $ctx") >> delegate
                  .schedule(request, ctx)
                  .onError(t => logger.warn(t)(s"SchedulerGrpc#schedule with request $request failed: ${t.getMessage}"))
              }
            }
          }
      }
    }
}
