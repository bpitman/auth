package com.pcpitman.auth

object Main {

  def main(args: Array[String]): Unit = {
    System.setProperty("netflix.iep.spring.scanPackages", "com.netflix,com.pcpitman")
    com.netflix.iep.spring.Main.run(args)
  }
}
