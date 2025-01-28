package uk.sky.scheduler.message

import cats.kernel.laws.discipline.MonoidTests
import cats.tests.CatsSuite
import uk.sky.scheduler.util.Generator.given

final class MetadataLawsSpec extends CatsSuite {
  checkAll("Headers.MonoidLaws", MonoidTests[Metadata].monoid)
}
