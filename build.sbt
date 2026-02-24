ThisBuild / organization := "com.bpitman"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(javaLib, app)
  .settings(
    name := "auth",
    publish / skip := true
  )

lazy val javaLib = (project in file("java-lib"))
  .settings(
    name := "auth-java-lib",
    crossPaths := false,
    autoScalaLibrary := false,
    javacOptions ++= Seq("-source", "11", "-target", "11")
  )

lazy val app = (project in file("app"))
  .dependsOn(javaLib)
  .settings(
    name := "auth-app",
    scalaVersion := "3.3.4",
    crossScalaVersions := Seq("2.13.15", "3.3.4")
  )
