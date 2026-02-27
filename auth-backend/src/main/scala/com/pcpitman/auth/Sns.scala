package com.pcpitman.auth

import com.typesafe.scalalogging.LazyLogging

import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.model.*

class Sns(client: SnsAsyncClient) extends LazyLogging {

  def sendOtp(phoneNumber: String, code: String): Unit = {
    val request = PublishRequest.builder()
      .phoneNumber(phoneNumber)
      .message(s"Your verification code is: $code")
      .build()
    client.publish(request).get()
    logger.info(s"Sent OTP to $phoneNumber")
  }
}
