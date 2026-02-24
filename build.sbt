ThisBuild / organization := "com.pcpitman"
ThisBuild / version      := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(javaLib, app)
  .settings(
    name := "auth",
    publish / skip := true
  )

lazy val javaLib = (project in file("auth-core"))
  .settings(
    name := "auth-core",
    crossPaths := false,
    autoScalaLibrary := false,
    javacOptions ++= Seq("-source", "17", "-target", "17")
  )

lazy val app = (project in file("auth-backend"))
  .dependsOn(javaLib)
  .settings(
    name := "auth-backend",
    scalaVersion := "3.8.1",
    crossScalaVersions := Seq("2.13.18", "3.8.1")
  )
