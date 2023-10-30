package uk.sky.scheduler.error

enum ScheduleError(val message: String) {
  case DecodeError(key: String, errorMessage: String)
      extends ScheduleError(s"Failed to decode $key with error $errorMessage")
}
