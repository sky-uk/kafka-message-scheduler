package uk.sky.scheduler.grpc

import com.google.protobuf.ByteString
import io.scalaland.chimney.Transformer

object Converters {
  private[grpc] given Transformer[Array[Byte], ByteString] = (src: Array[Byte]) => ByteString.copyFrom(src)
}
