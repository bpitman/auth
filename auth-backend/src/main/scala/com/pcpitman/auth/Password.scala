package com.pcpitman.auth

import java.nio.charset.StandardCharsets
import java.util.Base64

import com.typesafe.scalalogging.LazyLogging

import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsAsyncClient
import software.amazon.awssdk.services.kms.model.*

class Password(kmsClient: KmsAsyncClient) extends LazyLogging {

  init()

  private def init(): Unit = {
    val alias = AuthConfig.kmsAlias
    try {
      val response = kmsClient.describeKey(
        DescribeKeyRequest.builder().keyId(alias).build()
      ).get()
      logger.info("KMS key exists: {} -> {}", alias, response.keyMetadata().keyId())
    } catch {
      case e: java.util.concurrent.ExecutionException
        if e.getCause.isInstanceOf[NotFoundException] =>
        logger.info("KMS key {} not found, creating", alias)
        val keyId = kmsClient.createKey(
          CreateKeyRequest.builder().description(alias).build()
        ).get().keyMetadata().keyId()
        kmsClient.createAlias(
          CreateAliasRequest.builder()
            .aliasName(alias)
            .targetKeyId(keyId)
            .build()
        ).get()
        logger.info("Created KMS key {} -> {}", alias, keyId)
    }
  }

  private val MinLength = 10

  def validate(password: String): List[String] = {
    val errors = List.newBuilder[String]
    if (password.length < MinLength)
      errors += s"must be at least $MinLength characters"
    if (!password.exists(_.isUpper))
      errors += "must contain at least one uppercase letter"
    if (!password.exists(_.isLower))
      errors += "must contain at least one lowercase letter"
    if (!password.exists(_.isDigit))
      errors += "must contain at least one digit"
    if (!password.exists(c => !c.isLetterOrDigit))
      errors += "must contain at least one special character"
    errors.result()
  }

  def encrypt(password: String): String = {
    val response = kmsClient.encrypt(
      EncryptRequest.builder()
        .keyId(AuthConfig.kmsAlias)
        .plaintext(SdkBytes.fromByteArray(password.getBytes(StandardCharsets.UTF_8)))
        .build()
    ).get()
    Base64.getEncoder.encodeToString(response.ciphertextBlob().asByteArray())
  }

  def decrypt(encrypted: String): String = {
    val response = kmsClient.decrypt(
      DecryptRequest.builder()
        .keyId(AuthConfig.kmsAlias)
        .ciphertextBlob(SdkBytes.fromByteArray(Base64.getDecoder.decode(encrypted)))
        .build()
    ).get()
    new String(response.plaintext().asByteArray(), StandardCharsets.UTF_8)
  }
}
