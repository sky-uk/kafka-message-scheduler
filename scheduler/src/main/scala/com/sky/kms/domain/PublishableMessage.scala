package com.sky.kms.domain

import com.sky.kms.kafka.ProducerRecordEncoder
import org.apache.kafka.clients.producer.ProducerRecord

sealed trait PublishableMessage

object PublishableMessage {

  case class ScheduledMessage(topic: String, key: Array[Byte], value: Option[Array[Byte]]) extends PublishableMessage

  case class ScheduleDeletion(scheduleId: ScheduleId, scheduleTopic: String) extends PublishableMessage

  implicit val scheduledMessageProducerRecordEnc: ProducerRecordEncoder[ScheduledMessage] =
    ProducerRecordEncoder.instance(message => new ProducerRecord(message.topic, message.key, message.value.orNull))

  implicit val scheduleDeletionProducerRecordEnc: ProducerRecordEncoder[ScheduleDeletion] =
    ProducerRecordEncoder.instance(deletion => new ProducerRecord(deletion.scheduleTopic, deletion.scheduleId.getBytes, null))
  
  implicit def publishableMessageToProducerRecord(msg: PublishableMessage): ProducerRecord[Array[Byte], Array[Byte]] =
    msg match {
      case scheduledMsg: ScheduledMessage => scheduledMessageProducerRecordEnc(scheduledMsg)
      case deletion: ScheduleDeletion => scheduleDeletionProducerRecordEnc(deletion)
    }
}
