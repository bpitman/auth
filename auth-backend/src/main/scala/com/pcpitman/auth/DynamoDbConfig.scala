package com.pcpitman.auth

import com.netflix.iep.config.ConfigManager

object DynamoDbConfig {

  private val cfg = ConfigManager.get().getConfig("pcpitman.auth.dynamodb")

  def tableName: String = cfg.getString("table-name")
  def readCapacityUnits: Long = cfg.getLong("read-capacity-units")
  def writeCapacityUnits: Long = cfg.getLong("write-capacity-units")
}
