package com.pcpitman.auth

import java.time.LocalDate

import org.apache.pekko.http.scaladsl.model.HttpEntity
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.MediaTypes
import org.apache.pekko.http.scaladsl.model.StatusCode
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import org.apache.pekko.http.scaladsl.server.Route
import com.netflix.atlas.pekko.CustomDirectives.*
import com.netflix.atlas.pekko.WebApi
import com.netflix.atlas.json3.Json
import org.slf4j.LoggerFactory

class AuthApi(user: User) extends WebApi {

  private val logger = LoggerFactory.getLogger(getClass)

  if (user == null) logger.error("AuthApi created with null user — dependency injection failure")

  private val exceptionHandler = ExceptionHandler {
    case e: Exception =>
      extractUri { uri =>
        logger.error(s"Request to $uri failed", e)
        complete(jsonResponse(StatusCodes.InternalServerError, Map("error" -> e.getMessage)))
      }
  }

  private val index = Map(
    "description" -> "Auth Service",
    "endpoints" -> List(
      "/api/v1/status",
      "/api/v1/register",
      "/api/v1/login",
      "/api/v1/logout",
      "/api/v1/validate-email",
      "/api/v1/me",
      "/api/v1/update-profile",
      "/api/v1/add-mobile",
      "/api/v1/validate-mobile"
    )
  )

  def routes: Route = handleExceptions(exceptionHandler) {
    pathEndOrSingleSlash {
      complete(jsonResponse(StatusCodes.OK, index))
    } ~
    endpointPath("api" / "v1" / "status") {
      get {
        complete(jsonResponse(StatusCodes.OK, Map("status" -> "ok")))
      }
    } ~
    endpointPath("api" / "v1" / "register") {
      put {
        parseEntity(json[RegisterRequest]) { req =>
          val birthDate = LocalDate.parse(req.birthDate)
          user.register(req.firstName, req.lastName, req.email, req.password, birthDate, req.validationUrlBase) match {
            case Right(result) =>
              complete(jsonResponse(StatusCodes.Created, Map("id" -> result.id, "email" -> req.email, "token" -> result.token)))
            case Left(RegisterError.InvalidPassword(errors)) =>
              complete(jsonResponse(StatusCodes.BadRequest, Map("errors" -> errors)))
            case Left(RegisterError.EmailExists) =>
              complete(jsonResponse(StatusCodes.Conflict, Map("error" -> "email already registered")))
            case Left(RegisterError.TooYoung) =>
              complete(jsonResponse(StatusCodes.BadRequest, Map("error" -> "must be at least 16 years old")))
          }
        }
      }
    } ~
    endpointPath("api" / "v1" / "login") {
      post {
        parseEntity(json[LoginRequest]) { req =>
          user.login(req.email, req.password) match {
            case Right(result) =>
              complete(jsonResponse(StatusCodes.OK, Map("userId" -> result.userId, "token" -> result.token, "status" -> result.status.value)))
            case Left(LoginError.InvalidCredentials) =>
              complete(jsonResponse(StatusCodes.Unauthorized, Map("error" -> "invalid credentials")))
          }
        }
      }
    } ~
    endpointPath("api" / "v1" / "me") {
      post {
        parseEntity(json[MeRequest]) { req =>
          user.getSession(req.token) match {
            case Some(data) =>
              complete(jsonResponse(StatusCodes.OK, Map(
                "userId" -> data.userId,
                "email" -> data.email,
                "status" -> data.status.value,
                "firstName" -> data.firstName,
                "lastName" -> data.lastName,
                "birthDate" -> data.birthDate
              )))
            case None =>
              complete(jsonResponse(StatusCodes.Unauthorized, Map("error" -> "invalid session")))
          }
        }
      }
    } ~
    endpointPath("api" / "v1" / "update-profile") {
      put {
        parseEntity(json[UpdateProfileRequest]) { req =>
          val birthDate = LocalDate.parse(req.birthDate)
          user.updateProfile(req.userId, req.firstName, req.lastName, req.email, req.password, birthDate, req.validationUrlBase) match {
            case Right(()) =>
              complete(jsonResponse(StatusCodes.OK, Map("message" -> "profile updated")))
            case Left(UpdateProfileError.InvalidPassword(errors)) =>
              complete(jsonResponse(StatusCodes.BadRequest, Map("errors" -> errors)))
            case Left(UpdateProfileError.NotFound) =>
              complete(jsonResponse(StatusCodes.NotFound, Map("error" -> "user not found")))
            case Left(UpdateProfileError.EmailExists) =>
              complete(jsonResponse(StatusCodes.Conflict, Map("error" -> "email already registered")))
            case Left(UpdateProfileError.TooYoung) =>
              complete(jsonResponse(StatusCodes.BadRequest, Map("error" -> "must be at least 16 years old")))
          }
        }
      }
    } ~
    endpointPath("api" / "v1" / "logout") {
      post {
        parseEntity(json[LogoutRequest]) { req =>
          user.logout(req.token)
          complete(jsonResponse(StatusCodes.OK, Map("message" -> "logged out")))
        }
      }
    } ~
    endpointPath("api" / "v1" / "validate-email") {
      get {
        parameter("token") { token =>
          optionalHeaderValueByName("X-Session-Token") { sessionToken =>
            if (user.validateEmail(token, sessionToken)) {
              complete(jsonResponse(StatusCodes.OK, Map("message" -> "email validated")))
            } else {
              complete(jsonResponse(StatusCodes.BadRequest, Map("error" -> "invalid token")))
            }
          }
        }
      }
    } ~
    endpointPath("api" / "v1" / "add-mobile") {
      post {
        parseEntity(json[AddMobileRequest]) { req =>
          optionalHeaderValueByName("X-Session-Token") { sessionToken =>
            user.addMobile(req.userId, req.phoneNumber, sessionToken) match {
              case Right(()) =>
                complete(jsonResponse(StatusCodes.OK, Map("message" -> "OTP sent")))
              case Left(MobileError.MobileExists) =>
                complete(jsonResponse(StatusCodes.Conflict, Map("error" -> "mobile number already registered")))
            }
          }
        }
      }
    } ~
    endpointPath("api" / "v1" / "validate-mobile") {
      post {
        parseEntity(json[ValidateMobileRequest]) { req =>
          optionalHeaderValueByName("X-Session-Token") { sessionToken =>
            if (user.validateMobile(req.userId, req.code, sessionToken)) {
              complete(jsonResponse(StatusCodes.OK, Map("message" -> "mobile validated")))
            } else {
              complete(jsonResponse(StatusCodes.BadRequest, Map("error" -> "invalid code")))
            }
          }
        }
      }
    }
  }

  private def jsonResponse(status: StatusCode, body: Any): HttpResponse = {
    HttpResponse(
      status,
      entity = HttpEntity(MediaTypes.`application/json`, Json.encode(body))
    )
  }
}

case class RegisterRequest(firstName: String, lastName: String, email: String, password: String, birthDate: String, validationUrlBase: Option[String] = None)
case class LoginRequest(email: String, password: String)
case class MeRequest(token: String)
case class UpdateProfileRequest(userId: String, firstName: String, lastName: String, email: String, password: String, birthDate: String, validationUrlBase: Option[String] = None)
case class AddMobileRequest(userId: String, phoneNumber: String)
case class LogoutRequest(token: String)
case class ValidateMobileRequest(userId: String, code: String)
