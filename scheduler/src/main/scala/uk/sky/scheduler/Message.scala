package uk.sky.scheduler

case class Message[V](key: String, source: String, value: V, headers: Map[String, String])

object Message {
  val expiredHeader: String = "schedule:expired"

  extension (message: Message[_]) {
    def expired: Boolean = message.headers.get(expiredHeader).contains("true")
  }
}
