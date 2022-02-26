package com.sky.kms.base

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

trait SpecBase extends AnyWordSpec with Matchers with BeforeAndAfterAll with RandomDataGenerator with ScalaFutures
