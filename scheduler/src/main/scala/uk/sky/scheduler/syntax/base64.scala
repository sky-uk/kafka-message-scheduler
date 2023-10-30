package uk.sky.scheduler.syntax

import java.nio.charset.StandardCharsets
import java.util.Base64

import cats.effect.Sync
import cats.syntax.all.*

trait base64 {
  extension (s: String) {
    def base64Decode[F[_] : Sync]: F[Array[Byte]] = Sync[F].delay {
      Base64.getDecoder.decode(s.getBytes(StandardCharsets.UTF_8))
    }

    def base64Encode[F[_] : Sync]: F[String] = Sync[F].delay {
      s.getBytes(StandardCharsets.UTF_8)
    }.flatMap(_.base64Encode)
  }

  extension (bytes: Array[Byte]) {
    def base64Encode[F[_] : Sync]: F[String] = Sync[F].delay {
      Base64.getEncoder.encodeToString(bytes)
    }
  }
}

object base64 extends base64
