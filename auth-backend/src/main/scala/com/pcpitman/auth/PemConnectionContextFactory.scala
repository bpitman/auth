package com.pcpitman.auth

import com.netflix.atlas.pekko.ConnectionContextFactory
import com.typesafe.config.Config
import org.apache.pekko.http.scaladsl.{ConnectionContext, HttpsConnectionContext}

import java.io.FileReader
import java.security.{KeyStore, SecureRandom}
import java.security.cert.{CertificateFactory, X509Certificate}
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory}

/** Builds an SSLContext directly from PEM cert and key files, no keystore needed. */
class PemConnectionContextFactory(config: Config) extends ConnectionContextFactory {

  private val certPath = config.getString("cert-path")
  private val keyPath = config.getString("key-path")
  private val caCertPath = config.getString("ca-cert-path")

  override val sslContext: SSLContext = {
    val cf = CertificateFactory.getInstance("X.509")

    // Load server certificate
    val certStream = java.io.FileInputStream(certPath)
    val cert = try cf.generateCertificate(certStream).asInstanceOf[X509Certificate]
    finally certStream.close()

    // Load CA certificate
    val caStream = java.io.FileInputStream(caCertPath)
    val caCert = try cf.generateCertificate(caStream).asInstanceOf[X509Certificate]
    finally caStream.close()

    // Load private key
    val keyPem = {
      val reader = FileReader(keyPath)
      try {
        val chars = new Array[Char](8192)
        val sb = new StringBuilder
        var n = reader.read(chars)
        while (n > 0) { sb.appendAll(chars, 0, n); n = reader.read(chars) }
        sb.toString
      } finally reader.close()
    }
    val keyBase64 = keyPem
      .replace("-----BEGIN PRIVATE KEY-----", "")
      .replace("-----END PRIVATE KEY-----", "")
      .replaceAll("\\s", "")
    val keyBytes = java.util.Base64.getDecoder.decode(keyBase64)
    val keySpec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes)
    val privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec)

    // Build KeyStore with cert chain + private key
    val ks = KeyStore.getInstance("PKCS12")
    ks.load(null)
    ks.setKeyEntry("server", privateKey, Array.emptyCharArray, Array(cert, caCert))

    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, Array.emptyCharArray)

    // Build TrustStore with CA cert
    val ts = KeyStore.getInstance("PKCS12")
    ts.load(null)
    ts.setCertificateEntry("ca", caCert)

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(ts)

    val ctx = SSLContext.getInstance("TLS")
    ctx.init(kmf.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
    ctx
  }

  override def sslEngine: SSLEngine = {
    val engine = sslContext.createSSLEngine()
    engine.setUseClientMode(false)
    engine
  }

  override def httpsConnectionContext: HttpsConnectionContext =
    ConnectionContext.httpsServer(() => sslEngine)
}
