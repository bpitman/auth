package com.pcpitman.auth

import java.time.LocalDate

import munit.FunSuite

import com.pcpitman.mock.redis.MockRedisClient

class SessionSuite extends FunSuite {

  private var session: Session = null

  override def beforeAll(): Unit = {
    val connection = MockRedisClient.newProxy()
    session = new Session(connection)
  }

  test("create stores session and returns token") {
    val token = session.create("user-1", "test@example.com", UserStatus.Registered, "John", "Doe", LocalDate.of(1990, 1, 15))
    assert(token.nonEmpty)
    assertEquals(token.length, 64) // 32 bytes hex-encoded
  }

  test("get retrieves stored session data") {
    val token = session.create("user-2", "jane@example.com", UserStatus.EmailValidated, "Jane", "Smith", LocalDate.of(1985, 6, 20))
    val data = session.get(token)
    assert(data.isDefined)
    assertEquals(data.get.userId, "user-2")
    assertEquals(data.get.email, "jane@example.com")
    assertEquals(data.get.status, UserStatus.EmailValidated)
    assertEquals(data.get.firstName, "Jane")
    assertEquals(data.get.lastName, "Smith")
  }

  test("get returns None for unknown token") {
    assertEquals(session.get("nonexistent-token"), None)
  }

  test("delete removes session") {
    val token = session.create("user-3", "del@example.com", UserStatus.Registered, "Del", "User", LocalDate.of(1992, 3, 10))
    assert(session.get(token).isDefined)
    session.delete(token)
    assertEquals(session.get(token), None)
  }
}
