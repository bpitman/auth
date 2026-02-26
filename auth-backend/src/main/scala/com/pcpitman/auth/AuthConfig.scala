package com.pcpitman.auth

import com.netflix.iep.config.ConfigManager

object AuthConfig {

  private val cfg = ConfigManager.get().getConfig("pcpitman.auth")

  def kmsAlias: String = cfg.getString("kms-alias")

  def sesFromAddress: String = cfg.getString("ses.from-address")
  def defaultValidationUrlBase: String = cfg.getString("ses.default-validation-url-base")

  def redisUri: String = cfg.getString("redis.uri")
  def sessionTtlSeconds: Long = cfg.getLong("session.ttl-seconds")
}
