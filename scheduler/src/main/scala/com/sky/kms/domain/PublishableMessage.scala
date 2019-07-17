package com.sky.kms.domain

sealed trait PublishableMessage extends Product with Serializable

object PublishableMessage {

  final case class ScheduledMessage(inputTopic: String, outputTopic: String, key: Array[Byte], value: Option[Array[Byte]], headers: Map[String, Array[Byte]]) extends PublishableMessage

  final case class ScheduleDeletion(scheduleId: ScheduleId, scheduleTopic: String, headers: Map[String, Array[Byte]]) extends PublishableMessage

}
