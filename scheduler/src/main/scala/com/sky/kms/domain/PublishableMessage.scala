package com.sky.kms.domain

sealed trait PublishableMessage extends Product with Serializable

object PublishableMessage {

  final case class ScheduledMessage(inputTopic: String, outputTopic: String, key: Array[Byte], value: Option[Array[Byte]], headers: Set[Header]) extends PublishableMessage

  final case class ScheduleDeletion(scheduleId: ScheduleId, scheduleTopic: String, headers: Set[Header]) extends PublishableMessage

}
