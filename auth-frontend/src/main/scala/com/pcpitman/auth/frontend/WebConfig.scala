package com.pcpitman.auth.frontend

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.core.io.{FileSystemResource, Resource}
import org.springframework.web.client.RestClient
import org.springframework.web.servlet.config.annotation.{ResourceHandlerRegistry, WebMvcConfigurer}
import org.springframework.web.servlet.resource.PathResourceResolver

import java.io.FileInputStream
import java.net.http.{HttpClient => JdkHttpClient}
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.{SSLContext, TrustManagerFactory}
import org.springframework.http.client.JdkClientHttpRequestFactory

@Configuration
class WebConfig extends WebMvcConfigurer:

  @Bean
  def restClient(@Value("${auth.backend.base-url}") baseUrl: String): RestClient =
    val cf = CertificateFactory.getInstance("X.509")
    val is = new FileInputStream("certs/root-ca.pem")
    val cert =
      try cf.generateCertificate(is)
      finally is.close()

    val ks = KeyStore.getInstance(KeyStore.getDefaultType)
    ks.load(null, null)
    ks.setCertificateEntry("root-ca", cert)

    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(ks)

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, tmf.getTrustManagers, null)

    val httpClient = JdkHttpClient.newBuilder()
      .sslContext(sslContext)
      .build()

    RestClient.builder()
      .baseUrl(baseUrl)
      .requestFactory(JdkClientHttpRequestFactory(httpClient))
      .build()

  override def addResourceHandlers(registry: ResourceHandlerRegistry): Unit =
    registry.addResourceHandler("/**")
      .addResourceLocations("file:auth-frontend/ui/dist/", "classpath:/static/")
      .resourceChain(true)
      .addResolver(new PathResourceResolver {
        override def getResource(resourcePath: String, location: Resource): Resource =
          val resource = location.createRelative(resourcePath)
          if resource.isReadable then resource
          else new FileSystemResource("auth-frontend/ui/dist/index.html")
      })
