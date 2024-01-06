package uk.sky.scheduler.kafka

import cats.syntax.all.*
import fs2.kafka.ProducerSettings
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.record.CompressionType

extension [F[_], K, V](settings: ProducerSettings[F, K, V]) {
  def atLeastOnce: ProducerSettings[F, K, V] =
    settings
      .withProperties(
        Map(
          ProducerConfig.RETRIES_CONFIG                        -> Int.MaxValue.toString,
          ProducerConfig.ACKS_CONFIG                           -> "all",
          ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION -> "1",
          ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG             -> "false"
        )
      )

  def performant: ProducerSettings[F, K, V] =
    settings.withProperties(
      Map(
        ProducerConfig.LINGER_MS_CONFIG        -> "10",
        ProducerConfig.BATCH_SIZE_CONFIG       -> "1000000",
        ProducerConfig.BUFFER_MEMORY_CONFIG    -> "80000000",
        ProducerConfig.COMPRESSION_TYPE_CONFIG -> CompressionType.ZSTD.name
      )
    )
}
