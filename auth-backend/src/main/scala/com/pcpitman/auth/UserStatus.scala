package com.pcpitman.auth

enum UserStatus(val value: String) {
  case Registered extends UserStatus("REGISTERED")
  case EmailValidated extends UserStatus("EMAIL_VALIDATED")
  case MobilePending extends UserStatus("MOBILE_PENDING")
  case Authenticated extends UserStatus("AUTHENTICATED")
}

object UserStatus {
  def fromString(s: String): UserStatus =
    values.find(_.value == s).getOrElse(throw IllegalArgumentException(s"Unknown status: $s"))
}
