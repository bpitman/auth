ThisBuild / organization := "com.pcpitman"
ThisBuild / version := {
  import scala.sys.process._
  def gitSilent(cmd: String): Option[String] = {
    val out = new StringBuilder
    val logger = ProcessLogger(s => out.append(s), _ => ())
    val exitCode = cmd.!(logger)
    if (exitCode == 0) Some(out.toString.trim) else None
  }
  def stripInit(v: String): String =
    if (v.endsWith("-init")) v.dropRight(5) else v
  def incrementPatch(v: String): String = {
    val parts = v.split('.')
    if (parts.length == 3) s"${parts(0)}.${parts(1)}.${parts(2).toInt + 1}"
    else v
  }
  val branch = "git rev-parse --abbrev-ref HEAD".!!.trim
  val commitTag = gitSilent("git describe --tags --exact-match HEAD")
  val lastTag = gitSilent("git describe --tags --abbrev=0")
  val commit = "git rev-parse HEAD".!!.trim.take(8)
  val timestamp = {
    val fmt = new java.text.SimpleDateFormat("yyyyMMddHHmmss")
    fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    fmt.format(new java.util.Date())
  }
  def snapshot(base: String): String =
    s"$base-$branch-$timestamp-$commit-SNAPSHOT"

  commitTag match {
    case Some(tag) if tag.endsWith("-init") =>
      snapshot(stripInit(tag))
    case Some(tag) if branch == "main" =>
      tag
    case Some(tag) =>
      snapshot(tag)
    case None =>
      lastTag match {
        case Some(tag) if tag.endsWith("-init") =>
          snapshot(stripInit(tag))
        case Some(tag) =>
          snapshot(incrementPatch(tag))
        case None =>
          snapshot("0.0.0")
      }
  }
}

ThisBuild / resolvers += "GitHub Packages" at "https://maven.pkg.github.com/bpitman/mock-clients"
ThisBuild / credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  sys.env.getOrElse("GITHUB_ACTOR", ""),
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

val noSnapshotDeps = taskKey[Unit]("Fail if release build has SNAPSHOT dependencies")

val npmBuild = taskKey[Unit]("Run npm build for frontend UI")

lazy val root = (project in file("."))
  .aggregate(`auth-core`, `auth-backend`, `auth-frontend`, `integration-tests`)
  .settings(
    name := "auth",
    publish / skip := true
  )

lazy val `auth-core` = (project in file("auth-core"))
  .settings(
    name := "auth-core",
    crossPaths := false,
    autoScalaLibrary := false,
    javacOptions ++= Seq("-source", "17", "-target", "17"),
    noSnapshotDeps := {
      if (!isSnapshot.value) {
        val snapshots = libraryDependencies.value.filter(_.revision.endsWith("-SNAPSHOT"))
        if (snapshots.nonEmpty) {
          val desc = snapshots.map(d => s"  ${d.organization}:${d.name}:${d.revision}").mkString("\n")
          sys.error(s"Release build ${version.value} has SNAPSHOT dependencies:\n$desc")
        }
      }
    },
    publish := (publish dependsOn noSnapshotDeps).value,
    publishLocal := (publishLocal dependsOn noSnapshotDeps).value
  )

lazy val `auth-backend` = (project in file("auth-backend"))
  .dependsOn(`auth-core`)
  .settings(
    name := "auth-backend",
    scalaVersion := "3.8.1",
    crossScalaVersions := Seq("2.13.18", "3.8.1"),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature"),
    libraryDependencies ++= Seq(
      Dependencies.aws2DynamoDB,
      Dependencies.aws2KMS,
      Dependencies.aws2SES,
      Dependencies.aws2SNS,
      Dependencies.lettuce,
      Dependencies.atlasSpringPekko,
      Dependencies.iepDynConfig,
      Dependencies.iepSpring,
      Dependencies.scalaLogging,
      Dependencies.typesafeConfig,
      Dependencies.slf4jApi,
      Dependencies.log4jApi,
      Dependencies.log4jCore,
      Dependencies.log4jSlf4j,
      Dependencies.spectatorApi,
      Dependencies.pekkoHttpTestkit  % Test,
      Dependencies.pekkoTestkit      % Test,
      Dependencies.atlasPekkoTestkit % Test,
      Dependencies.munit             % Test,
      Dependencies.mockClientsDynamodb(scalaBinaryVersion.value) % Test,
      Dependencies.mockClientsKms(scalaBinaryVersion.value) % Test,
      Dependencies.mockClientsSes(scalaBinaryVersion.value) % Test,
      Dependencies.mockClientsSns(scalaBinaryVersion.value) % Test,
      Dependencies.mockClientsRedis(scalaBinaryVersion.value) % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    noSnapshotDeps := {
      if (!isSnapshot.value) {
        val snapshots = libraryDependencies.value.filter(_.revision.endsWith("-SNAPSHOT"))
        if (snapshots.nonEmpty) {
          val desc = snapshots.map(d => s"  ${d.organization}:${d.name}:${d.revision}").mkString("\n")
          sys.error(s"Release build ${version.value} has SNAPSHOT dependencies:\n$desc")
        }
      }
    },
    publish := (publish dependsOn noSnapshotDeps).value,
    publishLocal := (publishLocal dependsOn noSnapshotDeps).value
  )

lazy val `auth-frontend` = (project in file("auth-frontend"))
  .settings(
    name := "auth-frontend",
    scalaVersion := "3.8.1",
    libraryDependencies ++= Seq(
      Dependencies.springBootWeb
    )
  )

lazy val `integration-tests` = (project in file("integration-tests"))
  .dependsOn(`auth-backend` % "test->test", `auth-backend`, `auth-frontend`)
  .settings(
    name := "integration-tests",
    scalaVersion := "3.8.1",
    publish / skip := true,
    libraryDependencies ++= Seq(
      Dependencies.munit      % Test,
      Dependencies.playwright % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    Test / parallelExecution := false,
    // Generate a properties file with classpaths so tests can launch server processes
    Test / resourceGenerators += Def.task {
      val backendCp  = (`auth-backend` / Test / fullClasspath).value.files.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
      val frontendCp = (`auth-frontend` / Runtime / fullClasspath).value.files.map(_.getAbsolutePath).mkString(java.io.File.pathSeparator)
      val dir        = (Test / resourceManaged).value / "com" / "pcpitman" / "auth" / "integration"
      IO.createDirectory(dir)
      val propsFile = dir / "server-classpaths.properties"
      IO.write(propsFile, s"backend.classpath=$backendCp\nfrontend.classpath=$frontendCp\n")
      Seq(propsFile)
    }.taskValue,
    // Run npm build before tests
    npmBuild := {
      import scala.sys.process._
      val uiDir = baseDirectory.value / ".." / "auth-frontend" / "ui"
      val exitCode = Process(Seq("npm", "run", "build"), uiDir).!
      if (exitCode != 0) sys.error("npm run build failed")
    },
    Test / test := (Test / test).dependsOn(npmBuild).value
  )
