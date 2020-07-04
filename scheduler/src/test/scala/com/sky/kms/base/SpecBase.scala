package com.sky.kms.base

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, EitherValues, Matchers, OptionValues, WordSpecLike}

trait SpecBase
    extends WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with RandomDataGenerator
    with ScalaFutures
    with OptionValues
    with EitherValues
