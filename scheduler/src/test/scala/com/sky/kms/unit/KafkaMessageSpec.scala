package com.sky.kms.unit

import akka.kafka.ConsumerMessage.CommittableOffset
import cats.Eq
import cats.laws.discipline.{ComonadTests, FunctorTests, TraverseTests}
import cats.tests.CatsSuite
import com.danielasfregola.randomdatagenerator.RandomDataGenerator._
import com.sky.kms.kafka.KafkaMessage
import com.sky.kms.utils.StubOffset
import org.scalacheck.{Arbitrary, Cogen, Gen}
import org.scalatest.mockito.MockitoSugar

class KafkaMessageSpec extends CatsSuite with MockitoSugar {

  implicit val arbCommittableOffset: Arbitrary[CommittableOffset] = Arbitrary(Gen.const(StubOffset()))

  implicit val eq = new Eq[KafkaMessage[Int]] {
    override def eqv(x: KafkaMessage[Int], y: KafkaMessage[Int]): Boolean =
      x.offset.equals(y.offset) && x.value.equals(y.value)
  }

  implicit val eqFFA = new Eq[KafkaMessage[KafkaMessage[Int]]] {
    override def eqv(x: KafkaMessage[KafkaMessage[Int]], y: KafkaMessage[KafkaMessage[Int]]): Boolean =
      x.offset.equals(y.offset) && x.value.equals(y.value)
  }

  implicit val eqFFFA = new Eq[KafkaMessage[KafkaMessage[KafkaMessage[Int]]]] {
    override def eqv(x: KafkaMessage[KafkaMessage[KafkaMessage[Int]]], y: KafkaMessage[KafkaMessage[KafkaMessage[Int]]]): Boolean =
      x.offset.equals(y.offset) && x.value.equals(y.value)
  }

  implicit val cogen: Cogen[KafkaMessage[Int]] = Cogen[Int].contramap(_.value)

  checkAll("KafkaMessage.FunctorLaws", FunctorTests[KafkaMessage].functor[Int, Int, Int])
  checkAll("StreamMessage.TraversableLaws", TraverseTests[KafkaMessage].traverse[Int, Int, Int, Int, Option, Option])
  checkAll("StreamMessage.ComonadLaws", ComonadTests[KafkaMessage].comonad[Int, Int, Int])
}
