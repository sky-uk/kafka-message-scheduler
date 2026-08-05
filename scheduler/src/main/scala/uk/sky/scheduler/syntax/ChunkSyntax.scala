package uk.sky.scheduler.syntax

import fs2.Chunk

import scala.collection.mutable

trait ChunkSyntax {
  extension [A](chunk: Chunk[A]) {

    /** Deduplicates elements in the chunk by a key function, keeping the latest value for each key.
      *
      * Preserves the insertion order of the first occurrence of each key.
      *
      * @param key
      *   function to extract the deduplication key from each element
      * @tparam K
      *   the type of the deduplication key
      * @return
      *   a new chunk with duplicates removed
      */
    def dedupeBy[K](key: A => K): Chunk[A] = {
      val seen = mutable.LinkedHashMap.empty[K, A]
      chunk.foreach(a => seen.update(key(a), a))
      Chunk.from(seen.values)
    }
  }
}

object chunk extends ChunkSyntax
