package com.pcpitman.auth

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ExecutionException

import scala.jdk.CollectionConverters.*

import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException

class User(password: Password, dynamoDb: DynamoDb, ses: Ses, sns: Sns, session: Session) {

  def register(firstName: String, lastName: String, email: String, rawPassword: String, validationUrlBase: Option[String] = None): Either[RegisterError, RegisterResult] = {
    val errors = password.validate(rawPassword)
    if (errors.nonEmpty) return Left(RegisterError.InvalidPassword(errors))
    val id = UUID.randomUUID().toString
    val token = newToken()
    val encrypted = password.encrypt(rawPassword)
    try {
      dynamoDb.putUser(id, firstName, lastName, email, encrypted, token)
    } catch {
      case e: ExecutionException if e.getCause.isInstanceOf[TransactionCanceledException] =>
        val reasons = e.getCause.asInstanceOf[TransactionCanceledException].cancellationReasons.asScala
        if (reasons(1).code == "ConditionalCheckFailed")
          return Left(RegisterError.EmailExists)
        else
          throw e
    }
    ses.sendValidationEmail(email, token, validationUrlBase)
    val sessionToken = session.create(id, email, UserStatus.Registered, firstName, lastName)
    Right(RegisterResult(id, sessionToken))
  }

  def login(email: String, rawPassword: String): Either[LoginError, LoginResult] = {
    val userOpt = dynamoDb.getUserByEmail(email)
    userOpt match {
      case None => Left(LoginError.InvalidCredentials)
      case Some(user) =>
        val storedPassword = password.decrypt(user("password").s())
        if (storedPassword != rawPassword) return Left(LoginError.InvalidCredentials)
        val userId = user("id").s()
        val status = UserStatus.fromString(user("status").s())
        val firstName = user("firstName").s()
        val lastName = user("lastName").s()
        val token = session.create(userId, email, status, firstName, lastName)
        Right(LoginResult(userId, token, status))
    }
  }

  def getSession(token: String): Option[SessionData] = {
    session.get(token)
  }

  def updateProfile(userId: String, firstName: String, lastName: String, email: String, rawPassword: String, validationUrlBase: Option[String] = None): Either[UpdateProfileError, Unit] = {
    val errors = password.validate(rawPassword)
    if (errors.nonEmpty) return Left(UpdateProfileError.InvalidPassword(errors))
    val userOpt = dynamoDb.getUserById(userId)
    userOpt match {
      case None => Left(UpdateProfileError.NotFound)
      case Some(user) =>
        val oldEmail = user("email").s()
        val oldToken = user("emailValidationToken").s()
        val encrypted = password.encrypt(rawPassword)
        val emailToken = newToken()
        try {
          dynamoDb.updateUser(userId, firstName, lastName, email, oldEmail, encrypted, emailToken, oldToken)
        } catch {
          case e: ExecutionException if e.getCause.isInstanceOf[TransactionCanceledException] =>
            val reasons = e.getCause.asInstanceOf[TransactionCanceledException].cancellationReasons.asScala
            if (reasons.exists(r => r.code == "ConditionalCheckFailed"))
              return Left(UpdateProfileError.EmailExists)
            else
              throw e
        }
        ses.sendValidationEmail(email, emailToken, validationUrlBase)
        Right(())
    }
  }

  def logout(token: String): Unit = {
    session.delete(token)
  }

  def validateEmail(token: String, sessionToken: Option[String] = None): Boolean = {
    val valid = dynamoDb.validateEmail(token)
    if (valid) {
      sessionToken.foreach(st => session.updateStatus(st, UserStatus.EmailValidated))
    }
    valid
  }

  def addMobile(userId: String, phoneNumber: String, sessionToken: Option[String] = None): Either[MobileError, Unit] = {
    val code = newOtp()
    try {
      dynamoDb.addMobile(userId, phoneNumber, code)
    } catch {
      case e: ExecutionException if e.getCause.isInstanceOf[TransactionCanceledException] =>
        val reasons = e.getCause.asInstanceOf[TransactionCanceledException].cancellationReasons.asScala
        if (reasons(1).code == "ConditionalCheckFailed")
          return Left(MobileError.MobileExists)
        else
          throw e
    }
    sns.sendOtp(phoneNumber, code)
    sessionToken.foreach(st => session.updateStatus(st, UserStatus.MobilePending))
    Right(())
  }

  def validateMobile(userId: String, code: String, sessionToken: Option[String] = None): Boolean = {
    val valid = dynamoDb.validateMobile(userId, code)
    if (valid) {
      sessionToken.foreach(st => session.updateStatus(st, UserStatus.MobileValidated))
    }
    valid
  }

  private def newToken(): String = {
    val bytes = new Array[Byte](32)
    new SecureRandom().nextBytes(bytes)
    bytes.map("%02x".format(_)).mkString
  }

  private def newOtp(): String = {
    val rng = new SecureRandom()
    val code = rng.nextInt(900000) + 100000
    code.toString
  }
}

sealed trait RegisterError
object RegisterError {
  case class InvalidPassword(errors: List[String]) extends RegisterError
  case object EmailExists extends RegisterError
}

case class RegisterResult(id: String, token: String)

sealed trait LoginError
object LoginError {
  case object InvalidCredentials extends LoginError
}

case class LoginResult(userId: String, token: String, status: UserStatus)

sealed trait UpdateProfileError
object UpdateProfileError {
  case class InvalidPassword(errors: List[String]) extends UpdateProfileError
  case object NotFound extends UpdateProfileError
  case object EmailExists extends UpdateProfileError
}

sealed trait MobileError
object MobileError {
  case object MobileExists extends MobileError
}
