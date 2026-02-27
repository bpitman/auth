package com.pcpitman.auth

import scala.jdk.CollectionConverters.*

import com.typesafe.scalalogging.LazyLogging

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import software.amazon.awssdk.services.dynamodb.model.*

class DynamoDb(client: DynamoDbAsyncClient) extends LazyLogging {

  import DynamoDb.*

  DynamoDb.init(client)

  def putUser(
    id: String,
    firstName: String,
    lastName: String,
    email: String,
    encryptedPassword: String,
    emailValidationToken: String
  ): Unit = {
    val userItem = Map(
      "id"                   -> AttributeValue.fromS(id),
      "firstName"            -> AttributeValue.fromS(firstName),
      "lastName"             -> AttributeValue.fromS(lastName),
      "email"                -> AttributeValue.fromS(email),
      "password"             -> AttributeValue.fromS(encryptedPassword),
      "status"               -> AttributeValue.fromS(UserStatus.Registered.value),
      "emailValidationToken" -> AttributeValue.fromS(emailValidationToken)
    ).asJava
    val emailItem = Map(
      "id" -> AttributeValue.fromS(s"EMAIL#$email")
    ).asJava
    val tokenItem = Map(
      "id" -> AttributeValue.fromS(s"TOKEN#$emailValidationToken")
    ).asJava
    val condition = "attribute_not_exists(id)"
    client.transactWriteItems(
      TransactWriteItemsRequest.builder()
        .transactItems(
          TransactWriteItem.builder().put(
            Put.builder().tableName(DynamoDbConfig.tableName).item(userItem)
              .conditionExpression(condition).build()
          ).build(),
          TransactWriteItem.builder().put(
            Put.builder().tableName(DynamoDbConfig.tableName).item(emailItem)
              .conditionExpression(condition).build()
          ).build(),
          TransactWriteItem.builder().put(
            Put.builder().tableName(DynamoDbConfig.tableName).item(tokenItem)
              .conditionExpression(condition).build()
          ).build()
        )
        .build()
    ).get()
  }

  def getUserByEmail(email: String): Option[Map[String, AttributeValue]] = {
    val response = client.query(
      QueryRequest.builder()
        .tableName(DynamoDbConfig.tableName)
        .indexName(emailIndex)
        .keyConditionExpression("email = :email")
        .expressionAttributeValues(Map(":email" -> AttributeValue.fromS(email)).asJava)
        .build()
    ).get()
    response.items().asScala.headOption.map(_.asScala.toMap)
  }

  def validateEmail(token: String): Boolean = {
    val response = client.query(
      QueryRequest.builder()
        .tableName(DynamoDbConfig.tableName)
        .indexName(tokenIndex)
        .keyConditionExpression("emailValidationToken = :token")
        .expressionAttributeValues(Map(":token" -> AttributeValue.fromS(token)).asJava)
        .build()
    ).get()
    response.items().asScala.headOption match {
      case Some(item) =>
        val id = item.get("id").s()
        client.transactWriteItems(
          TransactWriteItemsRequest.builder()
            .transactItems(
              TransactWriteItem.builder().update(
                Update.builder()
                  .tableName(DynamoDbConfig.tableName)
                  .key(Map("id" -> AttributeValue.fromS(id)).asJava)
                  .updateExpression("SET #s = :s REMOVE emailValidationToken")
                  .expressionAttributeNames(Map("#s" -> "status").asJava)
                  .expressionAttributeValues(Map(":s" -> AttributeValue.fromS(UserStatus.EmailValidated.value)).asJava)
                  .build()
              ).build(),
              TransactWriteItem.builder().delete(
                Delete.builder()
                  .tableName(DynamoDbConfig.tableName)
                  .key(Map("id" -> AttributeValue.fromS(s"TOKEN#$token")).asJava)
                  .build()
              ).build()
            )
            .build()
        ).get()
        true
      case None =>
        false
    }
  }
  def getUserById(id: String): Option[Map[String, AttributeValue]] = {
    try {
      val response = client.getItem(
        GetItemRequest.builder()
          .tableName(DynamoDbConfig.tableName)
          .key(Map("id" -> AttributeValue.fromS(id)).asJava)
          .build()
      ).get()
      if (response.hasItem && !response.item().isEmpty) Some(response.item().asScala.toMap)
      else None
    } catch {
      case e: java.util.concurrent.ExecutionException
        if e.getCause.isInstanceOf[ResourceNotFoundException] =>
        None
    }
  }

  def updateUser(
    id: String,
    firstName: String,
    lastName: String,
    newEmail: String,
    oldEmail: String,
    encryptedPassword: String,
    newEmailValidationToken: String,
    oldEmailValidationToken: String
  ): Unit = {
    val items = List.newBuilder[TransactWriteItem]
    // Update the main user record
    items += TransactWriteItem.builder().update(
      Update.builder()
        .tableName(DynamoDbConfig.tableName)
        .key(Map("id" -> AttributeValue.fromS(id)).asJava)
        .updateExpression("SET firstName = :fn, lastName = :ln, email = :e, #p = :p, emailValidationToken = :t")
        .conditionExpression("#s = :reg")
        .expressionAttributeNames(Map("#p" -> "password", "#s" -> "status").asJava)
        .expressionAttributeValues(Map(
          ":fn"  -> AttributeValue.fromS(firstName),
          ":ln"  -> AttributeValue.fromS(lastName),
          ":e"   -> AttributeValue.fromS(newEmail),
          ":p"   -> AttributeValue.fromS(encryptedPassword),
          ":t"   -> AttributeValue.fromS(newEmailValidationToken),
          ":reg" -> AttributeValue.fromS(UserStatus.Registered.value)
        ).asJava)
        .build()
    ).build()
    // Delete old token entry, create new one
    items += TransactWriteItem.builder().delete(
      Delete.builder()
        .tableName(DynamoDbConfig.tableName)
        .key(Map("id" -> AttributeValue.fromS(s"TOKEN#$oldEmailValidationToken")).asJava)
        .build()
    ).build()
    items += TransactWriteItem.builder().put(
      Put.builder()
        .tableName(DynamoDbConfig.tableName)
        .item(Map("id" -> AttributeValue.fromS(s"TOKEN#$newEmailValidationToken")).asJava)
        .conditionExpression("attribute_not_exists(id)")
        .build()
    ).build()
    // If email changed, swap EMAIL# entries
    if (newEmail != oldEmail) {
      items += TransactWriteItem.builder().delete(
        Delete.builder()
          .tableName(DynamoDbConfig.tableName)
          .key(Map("id" -> AttributeValue.fromS(s"EMAIL#$oldEmail")).asJava)
          .build()
      ).build()
      items += TransactWriteItem.builder().put(
        Put.builder()
          .tableName(DynamoDbConfig.tableName)
          .item(Map("id" -> AttributeValue.fromS(s"EMAIL#$newEmail")).asJava)
          .conditionExpression("attribute_not_exists(id)")
          .build()
      ).build()
    }
    client.transactWriteItems(
      TransactWriteItemsRequest.builder()
        .transactItems(items.result().asJava)
        .build()
    ).get()
  }

  def addMobile(userId: String, phoneNumber: String, otpToken: String): Unit = {
    client.transactWriteItems(
      TransactWriteItemsRequest.builder()
        .transactItems(
          TransactWriteItem.builder().update(
            Update.builder()
              .tableName(DynamoDbConfig.tableName)
              .key(Map("id" -> AttributeValue.fromS(userId)).asJava)
              .updateExpression("SET mobile = :m, mobileValidationCode = :c, #s = :s")
              .conditionExpression("#s = :cur")
              .expressionAttributeNames(Map("#s" -> "status").asJava)
              .expressionAttributeValues(Map(
                ":m"   -> AttributeValue.fromS(phoneNumber),
                ":c"   -> AttributeValue.fromS(otpToken),
                ":s"   -> AttributeValue.fromS(UserStatus.MobilePending.value),
                ":cur" -> AttributeValue.fromS(UserStatus.EmailValidated.value)
              ).asJava)
              .build()
          ).build(),
          TransactWriteItem.builder().put(
            Put.builder()
              .tableName(DynamoDbConfig.tableName)
              .item(Map("id" -> AttributeValue.fromS(s"MOBILE#$phoneNumber")).asJava)
              .conditionExpression("attribute_not_exists(id)")
              .build()
          ).build()
        )
        .build()
    ).get()
  }

  def validateMobile(userId: String, code: String): Boolean = {
    try {
      client.transactWriteItems(
        TransactWriteItemsRequest.builder()
          .transactItems(
            TransactWriteItem.builder().update(
              Update.builder()
                .tableName(DynamoDbConfig.tableName)
                .key(Map("id" -> AttributeValue.fromS(userId)).asJava)
                .updateExpression("SET #s = :s REMOVE mobileValidationCode")
                .conditionExpression("mobileValidationCode = :c AND #s = :cur")
                .expressionAttributeNames(Map("#s" -> "status").asJava)
                .expressionAttributeValues(Map(
                  ":s"   -> AttributeValue.fromS(UserStatus.MobileValidated.value),
                  ":c"   -> AttributeValue.fromS(code),
                  ":cur" -> AttributeValue.fromS(UserStatus.MobilePending.value)
                ).asJava)
                .build()
            ).build()
          )
          .build()
      ).get()
      true
    } catch {
      case e: java.util.concurrent.ExecutionException
        if e.getCause.isInstanceOf[TransactionCanceledException] =>
        false
    }
  }
}

object DynamoDb extends LazyLogging {

  val emailIndex = "email-index"
  val tokenIndex = "token-index"

  private val throughput = ProvisionedThroughput.builder()
    .readCapacityUnits(DynamoDbConfig.readCapacityUnits)
    .writeCapacityUnits(DynamoDbConfig.writeCapacityUnits)
    .build()

  private val allAttrDefs = List(
    AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
    AttributeDefinition.builder().attributeName("email").attributeType(ScalarAttributeType.S).build(),
    AttributeDefinition.builder().attributeName("emailValidationToken").attributeType(ScalarAttributeType.S).build()
  )

  private val expectedGsis = List(
    GlobalSecondaryIndex.builder()
      .indexName(emailIndex)
      .keySchema(KeySchemaElement.builder().attributeName("email").keyType(KeyType.HASH).build())
      .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
      .provisionedThroughput(throughput)
      .build(),
    GlobalSecondaryIndex.builder()
      .indexName(tokenIndex)
      .keySchema(KeySchemaElement.builder().attributeName("emailValidationToken").keyType(KeyType.HASH).build())
      .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
      .provisionedThroughput(throughput)
      .build()
  )

  def init(client: DynamoDbAsyncClient): Unit = {
    val tableName = DynamoDbConfig.tableName
    try {
      val response = client.describeTable(
        DescribeTableRequest.builder().tableName(tableName).build()
      ).get()
      logger.info(s"Table exists: ${response.table().tableName()}")
      val existingGsiNames = response.table().globalSecondaryIndexes().asScala
        .map(_.indexName()).toSet
      val missingGsis = expectedGsis.filterNot(g => existingGsiNames.contains(g.indexName()))
      if (missingGsis.nonEmpty) {
        val names = missingGsis.map(_.indexName()).mkString(", ")
        logger.info(s"Adding missing GSIs: $names")
        val updates = missingGsis.map { gsi =>
          GlobalSecondaryIndexUpdate.builder()
            .create(
              CreateGlobalSecondaryIndexAction.builder()
                .indexName(gsi.indexName())
                .keySchema(gsi.keySchema())
                .projection(gsi.projection())
                .provisionedThroughput(gsi.provisionedThroughput())
                .build()
            )
            .build()
        }
        client.updateTable(
          UpdateTableRequest.builder()
            .tableName(tableName)
            .attributeDefinitions(allAttrDefs.asJava)
            .globalSecondaryIndexUpdates(updates.asJava)
            .build()
        ).get()
        logger.info(s"Added GSIs: $names")
      }
    } catch {
      case e: java.util.concurrent.ExecutionException
        if e.getCause.isInstanceOf[ResourceNotFoundException] =>
        logger.info(s"Table $tableName not found, creating")
        val createRequest = CreateTableRequest.builder()
          .tableName(tableName)
          .keySchema(
            KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build()
          )
          .attributeDefinitions(allAttrDefs.asJava)
          .globalSecondaryIndexes(expectedGsis.asJava)
          .provisionedThroughput(throughput)
          .build()
        val createResponse = client.createTable(createRequest).get()
        logger.info(s"Created table: ${createResponse.tableDescription().tableName()}")
    }
  }
}
