package uk.sky.scheduler.core

import cats.data.ReaderT
import cats.effect.Resource

type ResourceReader[F[_], A, B] = ReaderT[Resource[F, *], A, B]
val ResourceReader = ReaderT
