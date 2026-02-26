package com.pcpitman.auth

import java.time.Clock

import munit.FunSuite

import software.amazon.awssdk.services.kms.MockKmsAsyncClient

class PasswordSuite extends FunSuite {

  private val kmsClient = MockKmsAsyncClient.newProxy(Clock.systemUTC())
  private val pw = new Password(kmsClient)

  // validate

  test("valid password") {
    assertEquals(pw.validate("Abcdef1234!"), Nil)
  }

  test("too short") {
    assert(pw.validate("Ab1!").exists(_.contains("at least 10")))
  }

  test("missing uppercase") {
    assert(pw.validate("abcdef1234!").exists(_.contains("uppercase")))
  }

  test("missing lowercase") {
    assert(pw.validate("ABCDEF1234!").exists(_.contains("lowercase")))
  }

  test("missing digit") {
    assert(pw.validate("Abcdefghij!").exists(_.contains("digit")))
  }

  test("missing special character") {
    assert(pw.validate("Abcdef12345").exists(_.contains("special")))
  }

  test("multiple errors") {
    val errors = pw.validate("abc")
    assert(errors.length >= 3)
  }

  // encrypt / decrypt

  test("encrypt and decrypt round-trip") {
    val password = "MySecret123!"
    val encrypted = pw.encrypt(password)
    assert(encrypted != password)
    assertEquals(pw.decrypt(encrypted), password)
  }

  test("different passwords produce different ciphertexts") {
    val enc1 = pw.encrypt("Password1!aa")
    val enc2 = pw.encrypt("Password2!bb")
    assertNotEquals(enc1, enc2)
  }
}
