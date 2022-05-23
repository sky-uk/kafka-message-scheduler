package com.sky.kms.base

import cats.scalatest.EitherValues
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait SpecBase
    extends AnyWordSpec
    with Matchers
    with EitherValues
    with BeforeAndAfterAll
    with RandomDataGenerator
    with ScalaFutures
    with Eventually
