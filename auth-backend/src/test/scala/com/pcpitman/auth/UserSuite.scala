package com.pcpitman.auth

import java.time.Clock
import java.time.LocalDate

import munit.FunSuite

import software.amazon.awssdk.services.dynamodb.MockDynamoDbAsyncClient
import software.amazon.awssdk.services.kms.MockKmsAsyncClient
import software.amazon.awssdk.services.kms.model.*
import software.amazon.awssdk.services.ses.MockSesAsyncClient
import software.amazon.awssdk.services.sns.MockSnsAsyncClient

import com.pcpitman.mock.redis.MockRedisClient

class UserSuite extends FunSuite {

  private var user: User = null
  private var session: Session = null

  override def beforeAll(): Unit = {
    val dynamoClient = MockDynamoDbAsyncClient.newProxy(Clock.systemUTC())
    DynamoDb.init(dynamoClient)
    val dynamoDb = new DynamoDb(dynamoClient)

    val kmsClient = MockKmsAsyncClient.newProxy(Clock.systemUTC())
    val kmsKeyId = kmsClient.createKey(
      CreateKeyRequest.builder().description("auth-password").build()
    ).get().keyMetadata().keyId()
    kmsClient.createAlias(
      CreateAliasRequest.builder()
        .aliasName(AuthConfig.kmsAlias)
        .targetKeyId(kmsKeyId)
        .build()
    ).get()
    val password = new Password(kmsClient)

    val sesClient = MockSesAsyncClient.newProxy(Clock.systemUTC())
    val ses = new Ses(sesClient)

    val snsClient = MockSnsAsyncClient.newProxy(Clock.systemUTC())
    val sns = new Sns(snsClient)

    val redisConnection = MockRedisClient.newProxy()
    session = new Session(redisConnection)

    user = new User(password, dynamoDb, ses, sns, session)
  }

  test("register returns token (auto-login)") {
    val result = user.register("Alice", "Wonder", "alice@example.com", "Password1!!", LocalDate.of(1990, 1, 15))
    assert(result.isRight)
    val reg = result.toOption.get
    assert(reg.id.nonEmpty)
    assert(reg.token.nonEmpty)
    // Verify session was created
    val data = session.get(reg.token)
    assert(data.isDefined)
    assertEquals(data.get.userId, reg.id)
    assertEquals(data.get.email, "alice@example.com")
    assertEquals(data.get.birthDate, "1990-01-15")
  }

  test("login with valid credentials returns token") {
    user.register("Bob", "Builder", "bob@example.com", "Password1!!", LocalDate.of(1985, 6, 20))
    val result = user.login("bob@example.com", "Password1!!")
    assert(result.isRight)
    val login = result.toOption.get
    assert(login.token.nonEmpty)
    assert(login.userId.nonEmpty)
    assertEquals(login.status, UserStatus.Registered)
    // Verify session was created
    val data = session.get(login.token)
    assert(data.isDefined)
    assertEquals(data.get.email, "bob@example.com")
    assertEquals(data.get.birthDate, "1985-06-20")
  }

  test("login with wrong password returns InvalidCredentials") {
    user.register("Carol", "Singer", "carol@example.com", "Password1!!", LocalDate.of(1992, 3, 10))
    val result = user.login("carol@example.com", "WrongPassword1!!")
    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, LoginError.InvalidCredentials)
  }

  test("login with unknown email returns InvalidCredentials") {
    val result = user.login("nobody@example.com", "Password1!!")
    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, LoginError.InvalidCredentials)
  }

  test("register rejects underage user") {
    val result = user.register("Young", "User", "young@example.com", "Password1!!", LocalDate.now().minusYears(15))
    assert(result.isLeft)
    assertEquals(result.swap.toOption.get, RegisterError.TooYoung)
  }

  test("logout deletes session") {
    user.register("Dan", "Dare", "dan@example.com", "Password1!!", LocalDate.of(1988, 12, 1))
    val loginResult = user.login("dan@example.com", "Password1!!").toOption.get
    assert(session.get(loginResult.token).isDefined)
    user.logout(loginResult.token)
    assertEquals(session.get(loginResult.token), None)
  }
}
