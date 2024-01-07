package uk.sky.scheduler.message

import cats.laws.discipline.FunctorTests
import cats.tests.CatsSuite
import uk.sky.scheduler.util.Generator.given

final class MessageLawsSpec extends CatsSuite {
  checkAll("Headers.MonoidLaws", FunctorTests[Message].functor[String, String, String])
}
