package uk.sky.scheduler

import cats.kernel.laws.discipline.MonoidTests
import cats.tests.CatsSuite
import uk.sky.scheduler.Message.Headers
import uk.sky.scheduler.util.Generator.given

final class HeadersLawsSpec extends CatsSuite {
  checkAll("Headers.MonoidLaws", MonoidTests[Headers].monoid)
}
