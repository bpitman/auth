package com.pcpitman.auth

import scala.io.Source

import com.typesafe.scalalogging.LazyLogging

import software.amazon.awssdk.services.ses.SesAsyncClient
import software.amazon.awssdk.services.ses.model.*

class Ses(client: SesAsyncClient) extends LazyLogging {

  private val template: String = {
    val stream = getClass.getResourceAsStream("/validation-email.html")
    Source.fromInputStream(stream).mkString
  }

  def sendValidationEmail(email: String, token: String, validationUrlBase: Option[String] = None): Unit = {
    val base = validationUrlBase.getOrElse(AuthConfig.defaultValidationUrlBase)
    val validationUrl = s"$base?token=$token"
    val body = template.replace("{{validationUrl}}", validationUrl)
    val request = SendEmailRequest.builder()
      .source(AuthConfig.sesFromAddress)
      .destination(Destination.builder().toAddresses(email).build())
      .message(
        Message.builder()
          .subject(Content.builder().data("Validate your email").build())
          .body(Body.builder().html(Content.builder().data(body).build()).build())
          .build()
      )
      .build()
    client.sendEmail(request).get()
    logger.info(s"Sent validation email to $email")
  }
}
