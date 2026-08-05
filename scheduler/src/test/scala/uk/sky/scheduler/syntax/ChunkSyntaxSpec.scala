package uk.sky.scheduler.syntax

import fs2.Chunk
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ChunkSyntaxSpec extends AnyWordSpec, Matchers, ChunkSyntax {

  "ChunkSyntax" when {
    "dedupeBy" should {
      final case class Item(id: Int, value: String)

      "return empty chunk when input is empty" in {
        val chunk = Chunk.empty[Item]
        chunk.dedupeBy(_.id).toList shouldBe empty
      }

      "return same chunk when all elements have unique keys" in {
        val chunk = Chunk(
          Item(1, "a"),
          Item(2, "b"),
          Item(3, "c")
        )
        chunk.dedupeBy(_.id).toList should contain theSameElementsInOrderAs List(
          Item(1, "a"),
          Item(2, "b"),
          Item(3, "c")
        )
      }

      "keep latest value and preserve insertion order" in {
        val chunk = Chunk(
          Item(1, "a"),
          Item(2, "b"),
          Item(3, "c"),
          Item(1, "a-updated"),
          Item(2, "b-updated")
        )
        chunk.dedupeBy(_.id).toList should contain theSameElementsInOrderAs List(
          Item(1, "a-updated"),
          Item(2, "b-updated"),
          Item(3, "c")
        )
      }

      "keep latest value when all elements have same key" in {
        val chunk = Chunk(
          Item(1, "first"),
          Item(1, "second"),
          Item(1, "third")
        )
        chunk.dedupeBy(_.id).toList should contain theSameElementsInOrderAs List(Item(1, "third"))
      }
    }
  }
}
