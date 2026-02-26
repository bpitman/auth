package com.pcpitman.auth

import java.time.Clock

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.annotation.Order

import io.lettuce.core.api.StatefulRedisConnection

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.MockDynamoDbAsyncClient
import software.amazon.awssdk.services.kms.KmsAsyncClient
import software.amazon.awssdk.services.kms.MockKmsAsyncClient
import software.amazon.awssdk.services.ses.SesAsyncClient
import software.amazon.awssdk.services.ses.MockSesAsyncClient
import software.amazon.awssdk.services.sns.SnsAsyncClient
import software.amazon.awssdk.services.sns.MockSnsAsyncClient

import com.pcpitman.mock.redis.MockRedisClient

@Configuration
@Order(1)
class TestConfiguration {

  private val logger = LoggerFactory.getLogger(getClass)

  @Bean(Array("mockDynamoDbAsyncClient"))
  @Primary
  def dynamoDbAsyncClient: DynamoDbAsyncClient = {
    logger.info("Creating mock DynamoDbAsyncClient")
    MockDynamoDbAsyncClient.newProxy(Clock.systemUTC())
  }

  @Bean(Array("mockKmsAsyncClient"))
  @Primary
  def kmsAsyncClient: KmsAsyncClient = {
    logger.info("Creating mock KmsAsyncClient")
    MockKmsAsyncClient.newProxy(Clock.systemUTC())
  }

  @Bean(Array("mockSesAsyncClient"))
  @Primary
  def sesAsyncClient: SesAsyncClient = {
    logger.info("Creating mock SesAsyncClient")
    MockSesAsyncClient.newProxy(Clock.systemUTC())
  }

  @Bean(Array("mockSnsAsyncClient"))
  @Primary
  def snsAsyncClient: SnsAsyncClient = {
    logger.info("Creating mock SnsAsyncClient")
    MockSnsAsyncClient.newProxy(Clock.systemUTC())
  }

  @Bean(Array("mockRedisConnection"))
  @Primary
  def redisConnection: StatefulRedisConnection[String, String] = {
    logger.info("Creating mock RedisConnection")
    MockRedisClient.newProxy()
  }
}
