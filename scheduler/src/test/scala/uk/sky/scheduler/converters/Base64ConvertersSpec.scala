package uk.sky.scheduler.converters

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class Base64ConvertersSpec extends AnyWordSpec, Matchers, ScalaCheckPropertyChecks, Base64Converter {

  "Base64Decoder" should {
    "roundtrip bytes" in forAll { (bytes: Array[Byte]) =>
      bytes.base64Encode.base64Decode shouldBe bytes
    }

    "roundtrip Strings" in forAll { (s: String) =>
      s.base64Encode.base64Decode shouldBe s.getBytes
    }
  }

}
