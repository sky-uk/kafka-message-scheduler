package com.sky.kafka.message.scheduler.domain

import com.sky.kafka.message.scheduler.kafka.ProducerRecordEncoder
import org.apache.kafka.clients.producer.ProducerRecord

sealed trait PublishableMessage

object PublishableMessage {

  case class ScheduledMessage(topic: String, key: Array[Byte], value: Array[Byte]) extends PublishableMessage

  case class ScheduleDeletion(scheduleId: ScheduleId, scheduleTopic: String) extends PublishableMessage

  implicit val scheduledMessageProducerRecordEnc: ProducerRecordEncoder[ScheduledMessage] =
    ProducerRecordEncoder.instance(message => new ProducerRecord(message.topic, message.key, message.value))

  implicit val scheduleDeletionProducerRecordEnc: ProducerRecordEncoder[ScheduleDeletion] =
    ProducerRecordEncoder.instance(deletion => new ProducerRecord(deletion.scheduleTopic, deletion.scheduleId.getBytes, null))


  implicit def scheduleDataToProducerRecord(msg: PublishableMessage): ProducerRecord[Array[Byte], Array[Byte]] =
    msg match {
      case scheduledMsg: ScheduledMessage => scheduledMessageProducerRecordEnc(scheduledMsg)
      case deletion: ScheduleDeletion => scheduleDeletionProducerRecordEnc(deletion)
    }
}
