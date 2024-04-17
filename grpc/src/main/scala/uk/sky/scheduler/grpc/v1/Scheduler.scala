package uk.sky.scheduler.grpc.v1

import java.util.concurrent.TimeUnit

import cats.effect.syntax.all.*
import cats.effect.{Async, MonadCancelThrow, Resource}
import cats.syntax.all.*
import com.google.protobuf.ByteString
import io.grpc.{ServerServiceDefinition, Status}
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.grpc.Converters.given
import uk.sky.scheduler.grpc.Metadata
import uk.sky.scheduler.otel.Otel
import uk.sky.scheduler.proto.v1
import uk.sky.scheduler.repository.Repository

class SchedulerGrpc[F[_] : MonadCancelThrow](scheduleEventRepository: Repository[F, String, ScheduleEvent])
    extends v1.SchedulerFs2Grpc[F, Metadata] {
  override def schedule(request: v1.ScheduleRequest, ctx: Metadata): F[v1.ScheduleEvent] =
    scheduleEventRepository.get(request.id).flatMap {
      case Some(scheduleEvent) =>
        scheduleEvent
          .transformInto[v1.ScheduleEvent]
          .pure[F]

      case None =>
        Status.NOT_FOUND
          .withDescription(s"[${request.id}] not found")
          .asRuntimeException()
          .raiseError
    }
}

object Scheduler {

  def observed[F[_] : MonadCancelThrow : Otel](delegate: SchedulerGrpc[F]): F[v1.SchedulerFs2Grpc[F, Metadata]] =
    Otel[F].meter.histogram[Double]("scheduler-grpc-request").create.map { histogram =>
      val logger = Otel[F].loggerFactory.getLogger

      new v1.SchedulerFs2Grpc[F, Metadata] {
        override def schedule(request: v1.ScheduleRequest, ctx: Metadata): F[v1.ScheduleEvent] =
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

  def live[F[_] : Async : Otel](
      scheduleEventRepository: Repository[F, String, ScheduleEvent]
  ): Resource[F, ServerServiceDefinition] =
    for {
      observed                <- Scheduler.observed[F](SchedulerGrpc(scheduleEventRepository)).toResource
      serverServiceDefinition <- v1.SchedulerFs2Grpc.serviceResource(observed, Metadata.from)
    } yield serverServiceDefinition
}
