package com.sky.kms.base

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

trait SpecBase
    extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll
    with RandomDataGenerator
    with ScalaFutures
    with Eventually
