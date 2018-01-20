package com.sky.kms.base

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

trait BaseSpec extends WordSpecLike with Matchers with BeforeAndAfterAll with RandomDataGenerator