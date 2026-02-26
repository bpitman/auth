package com.pcpitman.auth.frontend

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class FrontendApplication

object Main {
  def main(args: Array[String]): Unit =
    SpringApplication.run(classOf[FrontendApplication], args*)
}
