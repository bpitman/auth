package com.pcpitman.auth.frontend

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse, Cookie}
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.http.{HttpStatus, MediaType, ResponseEntity}
import org.springframework.web.bind.annotation.{GetMapping, PostMapping, PutMapping, RequestBody, RequestParam, RestController}
import org.springframework.web.client.{HttpClientErrorException, HttpServerErrorException, RestClient}

@RestController
class AuthController(restClient: RestClient, objectMapper: ObjectMapper):

  private val logger = LoggerFactory.getLogger(getClass)

  private val indexHtml = FileSystemResource("auth-frontend/ui/dist/index.html")

  @GetMapping(Array("/"))
  def root(): ResponseEntity[Void] =
    ResponseEntity.status(HttpStatus.FOUND)
      .header("Location", "/login")
      .build()

  @GetMapping(Array("/login", "/session", "/register", "/forgot-password"))
  def spaRoutes(): ResponseEntity[FileSystemResource] =
    ResponseEntity.ok()
      .contentType(MediaType.TEXT_HTML)
      .body(indexHtml)

  @GetMapping(Array("/me"))
  def me(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity[String] =
    val token = getCookieValue(request, "auth_token")
    if token == null then
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .contentType(MediaType.APPLICATION_JSON)
        .body("""{"error":"not authenticated"}""")
    val result = proxyPost("/api/v1/me", s"""{"token":"$token"}""")
    if result.getStatusCode == HttpStatus.UNAUTHORIZED then
      clearAuthCookie(response)
    result

  @PostMapping(path = Array("/login"), consumes = Array(MediaType.APPLICATION_JSON_VALUE))
  def login(@RequestBody body: String, response: HttpServletResponse): ResponseEntity[String] =
    try
      val result = restClient.post()
        .uri("/api/v1/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(classOf[String])

      val json = objectMapper.readTree(result)
      val token = json.get("token").asText()
      setAuthCookie(response, token)

      ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(result)
    catch
      case e: HttpClientErrorException =>
        logger.warn("Login client error: {} {}", e.getStatusCode, e.getResponseBodyAsString)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: HttpServerErrorException =>
        logger.error("Login server error: {} {}", e.getStatusCode, e.getResponseBodyAsString, e)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: Exception =>
        logger.error("Login unexpected error", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body("""{"error":"internal server error"}""")

  @PutMapping(path = Array("/register"), consumes = Array(MediaType.APPLICATION_JSON_VALUE))
  def register(@RequestBody body: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity[String] =
    try
      val result = restClient.put()
        .uri("/api/v1/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(addValidationUrlBase(body, request))
        .retrieve()
        .body(classOf[String])

      val json = objectMapper.readTree(result)
      val token = json.get("token").asText()
      setAuthCookie(response, token)

      ResponseEntity.status(HttpStatus.CREATED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(result)
    catch
      case e: HttpClientErrorException =>
        logger.warn("Register client error: {} {}", e.getStatusCode, e.getResponseBodyAsString)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: HttpServerErrorException =>
        logger.error("Register server error: {} {}", e.getStatusCode, e.getResponseBodyAsString, e)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: Exception =>
        logger.error("Register unexpected error", e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body("""{"error":"internal server error"}""")

  @PutMapping(path = Array("/update-profile"), consumes = Array(MediaType.APPLICATION_JSON_VALUE))
  def updateProfile(@RequestBody body: String, request: HttpServletRequest): ResponseEntity[String] =
    proxyPut("/api/v1/update-profile", addValidationUrlBase(body, request))

  @PostMapping(path = Array("/add-mobile"), consumes = Array(MediaType.APPLICATION_JSON_VALUE))
  def addMobile(@RequestBody body: String, request: HttpServletRequest): ResponseEntity[String] =
    proxyPostWithSession("/api/v1/add-mobile", body, request)

  @PostMapping(path = Array("/validate-mobile"), consumes = Array(MediaType.APPLICATION_JSON_VALUE))
  def validateMobile(@RequestBody body: String, request: HttpServletRequest): ResponseEntity[String] =
    proxyPostWithSession("/api/v1/validate-mobile", body, request)

  @GetMapping(Array("/validate-email"))
  def validateEmail(@RequestParam token: String, request: HttpServletRequest): ResponseEntity[Void] =
    val sessionToken = getCookieValue(request, "auth_token")
    try
      val builder = restClient.get()
        .uri(s"/api/v1/validate-email?token=$token")
      if sessionToken != null then
        builder.header("X-Session-Token", sessionToken)
      builder.retrieve().body(classOf[String])
      ResponseEntity.status(HttpStatus.FOUND)
        .header("Location", "/register")
        .build()
    catch
      case e: HttpClientErrorException =>
        logger.warn("validate-email client error: {} {}", e.getStatusCode, e.getResponseBodyAsString)
        ResponseEntity.status(HttpStatus.FOUND)
          .header("Location", "/login")
          .build()
      case e: Exception =>
        logger.error("validate-email unexpected error", e)
        ResponseEntity.status(HttpStatus.FOUND)
          .header("Location", "/login")
          .build()

  @PostMapping(path = Array("/logout"))
  def logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity[String] =
    val token = getCookieValue(request, "auth_token")
    if token != null then
      try
        restClient.post()
          .uri("/api/v1/logout")
          .contentType(MediaType.APPLICATION_JSON)
          .body(s"""{"token":"$token"}""")
          .retrieve()
          .body(classOf[String])
      catch case _: Exception => ()
    clearAuthCookie(response)
    ResponseEntity.ok()
      .contentType(MediaType.APPLICATION_JSON)
      .body("""{"message":"logged out"}""")

  private def proxyGet(uri: String): ResponseEntity[String] =
    try
      val result = restClient.get()
        .uri(uri)
        .retrieve()
        .body(classOf[String])
      ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(result)
    catch
      case e: HttpClientErrorException =>
        logger.warn("{} client error: {} {}", uri, e.getStatusCode, e.getResponseBodyAsString)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: HttpServerErrorException =>
        logger.error("{} server error: {} {}", uri, e.getStatusCode, e.getResponseBodyAsString, e)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: Exception =>
        logger.error("{} unexpected error", uri, e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body("""{"error":"internal server error"}""")

  private def proxyPostWithSession(uri: String, body: String, request: HttpServletRequest): ResponseEntity[String] =
    val sessionToken = getCookieValue(request, "auth_token")
    try
      val builder = restClient.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
      if sessionToken != null then
        builder.header("X-Session-Token", sessionToken)
      val result = builder.retrieve().body(classOf[String])
      ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(result)
    catch
      case e: HttpClientErrorException =>
        logger.warn("{} client error: {} {}", uri, e.getStatusCode, e.getResponseBodyAsString)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: HttpServerErrorException =>
        logger.error("{} server error: {} {}", uri, e.getStatusCode, e.getResponseBodyAsString, e)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: Exception =>
        logger.error("{} unexpected error", uri, e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body("""{"error":"internal server error"}""")

  private def proxyPost(uri: String, body: String): ResponseEntity[String] =
    try
      val result = restClient.post()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(classOf[String])
      ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(result)
    catch
      case e: HttpClientErrorException =>
        logger.warn("{} client error: {} {}", uri, e.getStatusCode, e.getResponseBodyAsString)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: HttpServerErrorException =>
        logger.error("{} server error: {} {}", uri, e.getStatusCode, e.getResponseBodyAsString, e)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: Exception =>
        logger.error("{} unexpected error", uri, e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body("""{"error":"internal server error"}""")

  private def proxyPut(uri: String, body: String): ResponseEntity[String] =
    try
      val result = restClient.put()
        .uri(uri)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .retrieve()
        .body(classOf[String])
      ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(result)
    catch
      case e: HttpClientErrorException =>
        logger.warn("{} client error: {} {}", uri, e.getStatusCode, e.getResponseBodyAsString)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: HttpServerErrorException =>
        logger.error("{} server error: {} {}", uri, e.getStatusCode, e.getResponseBodyAsString, e)
        ResponseEntity.status(e.getStatusCode)
          .contentType(MediaType.APPLICATION_JSON)
          .body(e.getResponseBodyAsString)
      case e: Exception =>
        logger.error("{} unexpected error", uri, e)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .contentType(MediaType.APPLICATION_JSON)
          .body("""{"error":"internal server error"}""")

  private def addValidationUrlBase(body: String, request: HttpServletRequest): String =
    val node = objectMapper.readTree(body).asInstanceOf[ObjectNode]
    val scheme = if request.isSecure then "https" else "http"
    val host = request.getHeader("Host")
    node.put("validationUrlBase", s"$scheme://$host/validate-email")
    objectMapper.writeValueAsString(node)

  private def getCookieValue(request: HttpServletRequest, name: String): String =
    val cookies = request.getCookies
    if cookies == null then null
    else cookies.find(_.getName == name).map(_.getValue).orNull

  private def setAuthCookie(response: HttpServletResponse, token: String): Unit =
    val cookie = new Cookie("auth_token", token)
    cookie.setHttpOnly(true)
    cookie.setPath("/")
    cookie.setMaxAge(86400)
    response.addCookie(cookie)
    response.setHeader("Set-Cookie",
      response.getHeader("Set-Cookie") + "; SameSite=Strict")

  private def clearAuthCookie(response: HttpServletResponse): Unit =
    val cookie = new Cookie("auth_token", "")
    cookie.setHttpOnly(true)
    cookie.setPath("/")
    cookie.setMaxAge(0)
    response.addCookie(cookie)
