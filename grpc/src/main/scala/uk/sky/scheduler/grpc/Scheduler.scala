package uk.sky.scheduler.grpc

import cats.Applicative
import cats.syntax.all.*
import io.grpc.Metadata as GrpcMetadata
import uk.sky.scheduler.proto.v1.{ScheduleEvent, ScheduleRequest, SchedulerFs2Grpc}
import uk.sky.scheduler.repository.Repository

class SchedulerGrpc[F[_] : Applicative](repository: Repository[F, String, ScheduleEvent])
    extends SchedulerFs2Grpc[F, GrpcMetadata] {

  override def schedule(request: ScheduleRequest, ctx: GrpcMetadata): F[ScheduleEvent] =
    repository.get(request.id).as(???)

}

object Scheduler {}
