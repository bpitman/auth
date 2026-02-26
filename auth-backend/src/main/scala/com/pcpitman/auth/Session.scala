package com.pcpitman.auth

import java.security.SecureRandom

import com.typesafe.scalalogging.LazyLogging
import com.netflix.atlas.json3.Json

import io.lettuce.core.api.StatefulRedisConnection

class Session(connection: StatefulRedisConnection[String, String]) extends LazyLogging {

  private val commands = connection.async()

  def create(
    userId: String,
    email: String,
    status: UserStatus,
    firstName: String,
    lastName: String
  ): String = {
    val token = newToken()
    val data = SessionData(userId, email, status, firstName, lastName)
    val json = Json.encode(data)
    commands.setex(token, AuthConfig.sessionTtlSeconds, json).get()
    logger.info(s"Created session for userId=$userId")
    token
  }

  def get(token: String): Option[SessionData] = {
    val json = commands.get(token).get()
    if (json == null) None
    else Some(Json.decode[SessionData](json))
  }

  def updateStatus(token: String, status: UserStatus): Boolean = {
    val json = commands.get(token).get()
    if (json == null) return false
    val data = Json.decode[SessionData](json)
    val updated = data.copy(status = status)
    commands.setex(token, AuthConfig.sessionTtlSeconds, Json.encode(updated)).get()
    logger.info(s"Updated session status to ${status.value} for userId=${data.userId}")
    true
  }

  def delete(token: String): Unit = {
    commands.del(token).get()
    logger.info(s"Deleted session token")
  }

  private def newToken(): String = {
    val bytes = new Array[Byte](32)
    new SecureRandom().nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }
}

case class SessionData(
  userId: String,
  email: String,
  status: UserStatus,
  firstName: String,
  lastName: String
)
