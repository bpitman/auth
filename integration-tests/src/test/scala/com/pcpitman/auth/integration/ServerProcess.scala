package com.pcpitman.auth.integration

import java.io.{BufferedReader, File, InputStreamReader}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}

import scala.compiletime.uninitialized

class ServerProcess(
    name: String,
    classpath: String,
    mainClass: String,
    jvmArgs: Seq[String] = Nil,
    readyPattern: Option[String] = None
) {
  private val lines = new ConcurrentLinkedQueue[String]()
  private val lineCount = new AtomicInteger(0)
  private val readyLatch = new CountDownLatch(1)
  private val compiledReadyPattern = readyPattern.map(Pattern.compile)
  private var process: Process = uninitialized
  private var readerThread: Thread = uninitialized

  def start(): Unit = {
    val javaHome = System.getProperty("java.home")
    val javaBin = s"$javaHome/bin/java"
    val cmd = Seq(javaBin) ++ jvmArgs ++ Seq("-cp", classpath, mainClass)
    val pb = new ProcessBuilder(cmd*)
    pb.redirectErrorStream(true)
    pb.directory(new File(System.getProperty("user.dir")))
    process = pb.start()

    readerThread = new Thread(() => {
      val reader = new BufferedReader(new InputStreamReader(process.getInputStream))
      var line = reader.readLine()
      while (line != null) {
        println(s"[$name] $line")
        lines.add(line)
        lineCount.incrementAndGet()
        compiledReadyPattern.foreach { p =>
          if (p.matcher(line).find()) readyLatch.countDown()
        }
        line = reader.readLine()
      }
    })
    readerThread.setDaemon(true)
    readerThread.start()
  }

  def waitForReady(timeoutMs: Long): Unit = {
    if (readyPattern.isDefined) {
      if (!readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        throw new RuntimeException(s"$name did not become ready within ${timeoutMs}ms")
      }
    }
  }

  def waitUntilHealthy(url: String, timeoutMs: Long): Unit = {
    val deadline = System.currentTimeMillis() + timeoutMs
    val client = ServerProcess.trustAllHttpClient()
    val request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .GET()
      .build()

    while (System.currentTimeMillis() < deadline) {
      try {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 200 && response.statusCode() < 400) return
      } catch {
        case _: Exception => // ignore connection errors during startup
      }
      Thread.sleep(500)
    }
    throw new RuntimeException(s"$name health check $url did not pass within ${timeoutMs}ms")
  }

  def currentLineOffset: Int = lineCount.get()

  def findPattern(regex: String, startOffset: Int = 0, timeoutMs: Long = 10000): String = {
    val pattern = Pattern.compile(regex)
    val deadline = System.currentTimeMillis() + timeoutMs

    while (System.currentTimeMillis() < deadline) {
      val snapshot = lines.toArray(Array.empty[String])
      var i = startOffset
      while (i < snapshot.length) {
        val matcher = pattern.matcher(snapshot(i))
        if (matcher.find()) return matcher.group(1)
        i += 1
      }
      Thread.sleep(200)
    }
    throw new RuntimeException(
      s"$name: pattern '$regex' not found within ${timeoutMs}ms (searched from offset $startOffset, total lines: ${lineCount.get()})"
    )
  }

  def stop(): Unit = {
    if (process != null) {
      process.destroyForcibly()
      process.waitFor(10, TimeUnit.SECONDS)
    }
    if (readerThread != null) readerThread.interrupt()
  }
}

object ServerProcess {
  def trustAllHttpClient(): HttpClient = {
    val trustManager = new X509TrustManager {
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
      override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, Array[TrustManager](trustManager), new SecureRandom())
    HttpClient.newBuilder().sslContext(sslContext).build()
  }
}
