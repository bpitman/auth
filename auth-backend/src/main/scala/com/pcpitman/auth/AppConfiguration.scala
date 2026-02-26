package com.pcpitman.auth

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.{Bean, Configuration, Lazy, Scope}
import org.springframework.core.annotation.Order

import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.kms.KmsAsyncClient
import software.amazon.awssdk.services.ses.SesAsyncClient
import software.amazon.awssdk.services.sns.SnsAsyncClient

@Configuration
@Order(2)
class AppConfiguration {

  private val logger = LoggerFactory.getLogger(getClass)

  @Bean @Scope("singleton") @Lazy
  def dynamoDbAsyncClient: DynamoDbAsyncClient = {
    logger.info("Creating DynamoDbAsyncClient")
    DynamoDbAsyncClient.create()
  }

  @Bean @Scope("singleton") @Lazy
  def kmsAsyncClient: KmsAsyncClient = {
    logger.info("Creating KmsAsyncClient")
    KmsAsyncClient.create()
  }

  @Bean @Scope("singleton") @Lazy
  def sesAsyncClient: SesAsyncClient = {
    logger.info("Creating SesAsyncClient")
    SesAsyncClient.create()
  }

  @Bean @Scope("singleton") @Lazy
  def snsAsyncClient: SnsAsyncClient = {
    logger.info("Creating SnsAsyncClient")
    SnsAsyncClient.create()
  }

  @Bean @Scope("singleton") @Lazy
  def redisConnection: StatefulRedisConnection[String, String] = {
    logger.info("Creating Redis connection to {}", AuthConfig.redisUri)
    RedisClient.create(AuthConfig.redisUri).connect()
  }

  @Bean @Scope("singleton")
  def ses(sesAsyncClient: SesAsyncClient): Ses = {
    logger.info("Creating Ses")
    new Ses(sesAsyncClient)
  }

  @Bean @Scope("singleton")
  def sns(snsAsyncClient: SnsAsyncClient): Sns = {
    logger.info("Creating Sns")
    new Sns(snsAsyncClient)
  }

  @Bean @Scope("singleton")
  def dynamoDb(dynamoDbAsyncClient: DynamoDbAsyncClient): DynamoDb = {
    logger.info("Creating DynamoDb")
    new DynamoDb(dynamoDbAsyncClient)
  }

  @Bean @Scope("singleton")
  def password(kmsAsyncClient: KmsAsyncClient): Password = {
    logger.info("Creating Password")
    new Password(kmsAsyncClient)
  }

  @Bean @Scope("singleton")
  def session(redisConnection: StatefulRedisConnection[String, String]): Session = {
    logger.info("Creating Session")
    new Session(redisConnection)
  }

  @Bean @Scope("singleton")
  def user(password: Password, dynamoDb: DynamoDb, ses: Ses, sns: Sns, session: Session): User = {
    logger.info("Creating User")
    new User(password, dynamoDb, ses, sns, session)
  }

  @Bean @Scope("singleton")
  def authApi(user: User): AuthApi = {
    logger.info("Creating AuthApi")
    new AuthApi(user)
  }
}
