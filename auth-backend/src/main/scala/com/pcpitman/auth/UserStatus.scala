package com.pcpitman.auth

enum UserStatus(val value: String) {
  case Registered extends UserStatus("REGISTERED")
  case EmailValidated extends UserStatus("EMAIL_VALIDATED")
  case MobilePending extends UserStatus("MOBILE_PENDING")
  case MobileValidated extends UserStatus("MOBILE_VALIDATED")
}

object UserStatus {
  def fromString(s: String): UserStatus =
    values.find(_.value == s).getOrElse(throw IllegalArgumentException(s"Unknown status: $s"))
}
