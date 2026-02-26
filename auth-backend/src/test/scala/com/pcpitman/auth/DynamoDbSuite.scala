package com.pcpitman.auth

import java.time.Clock
import java.util.concurrent.ExecutionException

import scala.jdk.CollectionConverters._

import munit.FunSuite

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.MockDynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model._

class DynamoDbSuite extends FunSuite {

  private var dynamoClient: DynamoDbAsyncClient = null
  private var db: DynamoDb = null

  override def beforeAll(): Unit = {
    dynamoClient = MockDynamoDbAsyncClient.newProxy(Clock.systemUTC())
    DynamoDb.init(dynamoClient)
    db = new DynamoDb(dynamoClient)
  }

  test("init creates table if it does not exist") {
    val client = MockDynamoDbAsyncClient.newProxy(Clock.systemUTC())
    DynamoDb.init(client)
    val response = client.describeTable(
      DescribeTableRequest.builder().tableName(DynamoDbConfig.tableName).build()
    ).get()
    assertEquals(response.table().tableName(), DynamoDbConfig.tableName)
  }

  test("init succeeds if table already exists") {
    val client = MockDynamoDbAsyncClient.newProxy(Clock.systemUTC())
    DynamoDb.init(client)
    DynamoDb.init(client) // second call should not throw
    val response = client.describeTable(
      DescribeTableRequest.builder().tableName(DynamoDbConfig.tableName).build()
    ).get()
    assertEquals(response.table().tableName(), DynamoDbConfig.tableName)
  }

  test("init adds missing GSIs to existing table") {
    import scala.jdk.CollectionConverters._
    val client = MockDynamoDbAsyncClient.newProxy(Clock.systemUTC())
    // Create table with only email-index, no token-index
    val throughput = ProvisionedThroughput.builder()
      .readCapacityUnits(5L).writeCapacityUnits(5L).build()
    client.createTable(CreateTableRequest.builder()
      .tableName(DynamoDbConfig.tableName)
      .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
      .attributeDefinitions(
        AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
        AttributeDefinition.builder().attributeName("email").attributeType(ScalarAttributeType.S).build()
      )
      .globalSecondaryIndexes(
        GlobalSecondaryIndex.builder()
          .indexName("email-index")
          .keySchema(KeySchemaElement.builder().attributeName("email").keyType(KeyType.HASH).build())
          .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
          .provisionedThroughput(throughput)
          .build()
      )
      .provisionedThroughput(throughput)
      .build()
    ).get()
    // init should detect missing token-index and add it
    DynamoDb.init(client)
    val response = client.describeTable(
      DescribeTableRequest.builder().tableName(DynamoDbConfig.tableName).build()
    ).get()
    val gsiNames = response.table().globalSecondaryIndexes().asScala.map(_.indexName()).toSet
    assert(gsiNames.contains("email-index"))
    assert(gsiNames.contains("token-index"))
  }

  test("putUser stores and getUserByEmail retrieves") {
    db.putUser("id-john", "John", "Doe", "john@example.com", "encrypted123", "token-john")
    val user = db.getUserByEmail("john@example.com")
    assert(user.isDefined)
    assertEquals(user.get("firstName").s(), "John")
    assertEquals(user.get("lastName").s(), "Doe")
    assertEquals(user.get("email").s(), "john@example.com")
    assertEquals(user.get("password").s(), "encrypted123")
    assertEquals(user.get("id").s(), "id-john")
  }

  test("putUser stores status=REGISTERED and emailValidationToken") {
    val token = "ab" * 32
    db.putUser("id-val", "Val", "User", "val@example.com", "enc", token)
    val user = db.getUserByEmail("val@example.com")
    assert(user.isDefined)
    assertEquals(user.get("status").s(), UserStatus.Registered.value)
    assertEquals(user.get("emailValidationToken").s(), token)
  }

  test("putUser with duplicate email fails") {
    db.putUser("id-dup1", "Dup", "One", "dup@example.com", "enc", "token-dup1")
    val ex = intercept[ExecutionException] {
      db.putUser("id-dup2", "Dup", "Two", "dup@example.com", "enc", "token-dup2")
    }
    assert(ex.getCause.isInstanceOf[TransactionCanceledException])
    val reasons = ex.getCause.asInstanceOf[TransactionCanceledException].cancellationReasons.asScala
    assertEquals(reasons(0).code, "None")
    assertEquals(reasons(1).code, "ConditionalCheckFailed")
    assertEquals(reasons(2).code, "None")
  }

  test("putUser with duplicate token fails") {
    db.putUser("id-tok1", "Tok", "One", "tok1@example.com", "enc", "shared-token")
    val ex = intercept[ExecutionException] {
      db.putUser("id-tok2", "Tok", "Two", "tok2@example.com", "enc", "shared-token")
    }
    assert(ex.getCause.isInstanceOf[TransactionCanceledException])
    val reasons = ex.getCause.asInstanceOf[TransactionCanceledException].cancellationReasons.asScala
    assertEquals(reasons(0).code, "None")
    assertEquals(reasons(1).code, "None")
    assertEquals(reasons(2).code, "ConditionalCheckFailed")
  }

  test("validateEmail returns true for valid token and sets status to EMAIL_VALIDATED") {
    db.putUser("id-token", "Token", "User", "tokenuser@example.com", "enc", "validtoken64chars" + "0" * 47)
    val user = db.getUserByEmail("tokenuser@example.com")
    val token = user.get("emailValidationToken").s()
    assert(db.validateEmail(token))
    val updated = db.getUserByEmail("tokenuser@example.com")
    assert(updated.isDefined)
    assertEquals(updated.get("status").s(), UserStatus.EmailValidated.value)
    assert(!updated.get.contains("emailValidationToken"))
    // Verify TOKEN# reservation was cleaned up
    val tokenKey = Map("id" -> AttributeValue.fromS(s"TOKEN#$token")).asJava
    val tokenItem = intercept[ExecutionException] {
      dynamoClient.getItem(
        GetItemRequest.builder().tableName(DynamoDbConfig.tableName).key(tokenKey).build()
      ).get()
    }
    assert(tokenItem.getCause.isInstanceOf[ResourceNotFoundException])
  }

  test("validateEmail returns false for invalid token") {
    assert(!db.validateEmail("0" * 64))
  }

  test("getUserByEmail returns None for unknown email") {
    assertEquals(db.getUserByEmail("unknown@example.com"), None)
  }

  test("getUserById retrieves a stored user") {
    db.putUser("id-byid", "ById", "User", "byid@example.com", "enc", "token-byid")
    val user = db.getUserById("id-byid")
    assert(user.isDefined)
    assertEquals(user.get("firstName").s(), "ById")
    assertEquals(user.get("email").s(), "byid@example.com")
  }

  test("getUserById returns None for unknown id") {
    assertEquals(db.getUserById("nonexistent-id"), None)
  }

  test("addMobile stores mobile and sets status to MOBILE_PENDING") {
    db.putUser("id-mob1", "Mob", "One", "mob1@example.com", "enc", "token-mob1")
    db.validateEmail("token-mob1")
    db.addMobile("id-mob1", "+15551234567", "123456")
    val user = db.getUserById("id-mob1")
    assert(user.isDefined)
    assertEquals(user.get("status").s(), UserStatus.MobilePending.value)
    assertEquals(user.get("mobile").s(), "+15551234567")
    assertEquals(user.get("mobileValidationCode").s(), "123456")
  }

  test("addMobile with duplicate phone number fails") {
    db.putUser("id-mob2", "Mob", "Two", "mob2@example.com", "enc", "token-mob2")
    db.validateEmail("token-mob2")
    db.addMobile("id-mob2", "+15559999999", "111111")

    db.putUser("id-mob3", "Mob", "Three", "mob3@example.com", "enc", "token-mob3")
    db.validateEmail("token-mob3")
    val ex = intercept[ExecutionException] {
      db.addMobile("id-mob3", "+15559999999", "222222")
    }
    assert(ex.getCause.isInstanceOf[TransactionCanceledException])
  }

  test("validateMobile sets status to MOBILE_VALIDATED and removes code") {
    db.putUser("id-mob4", "Mob", "Four", "mob4@example.com", "enc", "token-mob4")
    db.validateEmail("token-mob4")
    db.addMobile("id-mob4", "+15551111111", "654321")
    assert(db.validateMobile("id-mob4", "654321"))
    val user = db.getUserById("id-mob4")
    assert(user.isDefined)
    assertEquals(user.get("status").s(), UserStatus.MobileValidated.value)
    assert(!user.get.contains("mobileValidationCode"))
  }

  test("validateMobile with wrong code returns false") {
    db.putUser("id-mob5", "Mob", "Five", "mob5@example.com", "enc", "token-mob5")
    db.validateEmail("token-mob5")
    db.addMobile("id-mob5", "+15552222222", "999999")
    assert(!db.validateMobile("id-mob5", "000000"))
    // Status should remain MOBILE_PENDING
    val user = db.getUserById("id-mob5")
    assert(user.isDefined)
    assertEquals(user.get("status").s(), UserStatus.MobilePending.value)
  }
}
