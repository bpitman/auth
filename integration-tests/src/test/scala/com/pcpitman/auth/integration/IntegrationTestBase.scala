package com.pcpitman.auth.integration

import java.io.File
import java.net.ServerSocket
import java.nio.file.Files
import java.util.Properties

import com.microsoft.playwright.{Browser, BrowserType, Page, Playwright}

object IntegrationTestBase {
  @volatile private var initialized = false
  private var _backend: ServerProcess = null
  private var _frontend: ServerProcess = null
  private var _playwright: Playwright = null
  private var _browser: Browser = null
  private var _backendPort: Int = 0
  private var _frontendPort: Int = 0

  private val startupTimeoutMs = 90000L

  def backend: ServerProcess = { ensureStarted(); _backend }
  def frontend: ServerProcess = { ensureStarted(); _frontend }
  def browser: Browser = { ensureStarted(); _browser }
  def backendPort: Int = { ensureStarted(); _backendPort }
  def frontendPort: Int = { ensureStarted(); _frontendPort }
  def frontendBaseUrl: String = { ensureStarted(); s"https://localhost:${_frontendPort}" }

  private def findFreePort(): Int = {
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port
  }

  private def writeBackendConfig(port: Int): File = {
    val localConfigPath = new File(System.getProperty("user.dir"), "run-local/backend-application.conf").getAbsolutePath
    val content =
      s"""include file("$localConfigPath")
         |
         |atlas.pekko.ports = [
         |  {
         |    port = $port
         |    secure = true
         |    context-factory = "com.pcpitman.auth.PemConnectionContextFactory"
         |    ssl-config {
         |      cert-path = "certs/localhost.pem"
         |      key-path = "certs/localhost.key"
         |      ca-cert-path = "certs/root-ca.pem"
         |    }
         |  }
         |]
         |""".stripMargin
    val configFile = Files.createTempFile("integration-backend-", ".conf").toFile
    configFile.deleteOnExit()
    Files.writeString(configFile.toPath, content)
    configFile
  }

  def ensureStarted(): Unit = synchronized {
    if (initialized) return

    _backendPort = findFreePort()
    _frontendPort = findFreePort()

    val props = new Properties()
    val stream = getClass.getResourceAsStream("/com/pcpitman/auth/integration/server-classpaths.properties")
    if (stream == null) throw new RuntimeException("server-classpaths.properties not found on classpath")
    props.load(stream)
    stream.close()

    val backendCp = props.getProperty("backend.classpath")
    val frontendCp = props.getProperty("frontend.classpath")

    val backendConfigFile = writeBackendConfig(_backendPort)

    _backend = new ServerProcess(
      name = "backend",
      classpath = backendCp,
      mainClass = "com.pcpitman.auth.Main",
      jvmArgs = Seq(s"-Dconfig.file=${backendConfigFile.getAbsolutePath}")
    )

    _frontend = new ServerProcess(
      name = "frontend",
      classpath = frontendCp,
      mainClass = "com.pcpitman.auth.frontend.Main",
      jvmArgs = Seq(
        s"-Dserver.port=${_frontendPort}",
        s"-Dauth.backend.base-url=https://localhost:${_backendPort}"
      )
    )

    _backend.start()
    _frontend.start()

    val backendHealthUrl = s"https://localhost:${_backendPort}/api/v1/status"
    val frontendHealthUrl = s"https://localhost:${_frontendPort}/login"
    _backend.waitUntilHealthy(backendHealthUrl, startupTimeoutMs)
    _frontend.waitUntilHealthy(frontendHealthUrl, startupTimeoutMs)

    _playwright = Playwright.create()
    _browser = _playwright.chromium().launch(
      new BrowserType.LaunchOptions().setHeadless(true)
    )

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      if (_browser != null) _browser.close()
      if (_playwright != null) _playwright.close()
      if (_frontend != null) _frontend.stop()
      if (_backend != null) _backend.stop()
    }))

    initialized = true
  }
}

trait IntegrationTestBase extends munit.FunSuite {

  override def beforeAll(): Unit = {
    super.beforeAll()
    IntegrationTestBase.ensureStarted()
  }

  protected def backend: ServerProcess = IntegrationTestBase.backend
  protected def frontend: ServerProcess = IntegrationTestBase.frontend
  protected def frontendBaseUrl: String = IntegrationTestBase.frontendBaseUrl

  protected def newPage(): Page = {
    val context = IntegrationTestBase.browser.newContext(
      new Browser.NewContextOptions().setIgnoreHTTPSErrors(true)
    )
    context.newPage()
  }
}
