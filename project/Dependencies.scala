import sbt._

// format: off

object Dependencies {
  object Versions {
    val atlas     = "1.9.0-rc.1"
    val iep       = "6.0.1"
    val log4j     = "2.25.3"
    val pekko     = "1.4.0"
    val pekkoHttp = "1.3.0"
    val slf4j     = "2.0.17"
    val spectator = "1.9.4"
  }

  import Versions._

  val atlasSpringPekko  = "com.netflix.atlas_v1"       %% "atlas-spring-pekko"  % atlas
  val iepDynConfig      = "com.netflix.iep"              % "iep-dynconfig"       % iep
  val iepSpring         = "com.netflix.iep"              % "iep-spring"          % iep
  val log4jApi          = "org.apache.logging.log4j"     % "log4j-api"           % log4j
  val log4jCore         = "org.apache.logging.log4j"     % "log4j-core"          % log4j
  val log4jSlf4j        = "org.apache.logging.log4j"     % "log4j-slf4j2-impl"   % log4j
  val scalaLogging      = "com.typesafe.scala-logging" %% "scala-logging"       % "3.9.6"
  val slf4jApi          = "org.slf4j"                    % "slf4j-api"           % slf4j
  val spectatorApi      = "com.netflix.spectator"        % "spectator-api"       % spectator
  val aws2DynamoDB      = "software.amazon.awssdk"        % "dynamodb"            % "2.41.22"
  val aws2KMS           = "software.amazon.awssdk"        % "kms"                 % "2.41.22"
  val aws2SES           = "software.amazon.awssdk"        % "ses"                 % "2.41.22"
  val aws2SNS           = "software.amazon.awssdk"        % "sns"                 % "2.41.22"
  val lettuce           = "io.lettuce"                    % "lettuce-core"        % "6.5.5.RELEASE"
  val typesafeConfig    = "com.typesafe"                 % "config"              % "1.4.5"
  val springBootWeb     = "org.springframework.boot"     % "spring-boot-starter-web" % "3.4.3"

  // mock-clients
  val mockClientsVersion    = "0.1.8"
  val mockClientsDynamodb   = "com.pcpitman" %% "mock-clients-dynamodb" % mockClientsVersion
  val mockClientsKms        = "com.pcpitman" %% "mock-clients-kms"      % mockClientsVersion
  val mockClientsSes        = "com.pcpitman" %% "mock-clients-ses"      % mockClientsVersion
  val mockClientsSns        = "com.pcpitman" %% "mock-clients-sns"      % mockClientsVersion
  val mockClientsRedis      = "com.pcpitman" %% "mock-clients-redis"    % mockClientsVersion

  // Test
  val atlasPekkoTestkit = "com.netflix.atlas_v1"       %% "atlas-pekko-testkit" % atlas
  val munit             = "org.scalameta"              %% "munit"               % "1.2.2"
  val pekkoHttpTestkit  = "org.apache.pekko"           %% "pekko-http-testkit"  % pekkoHttp
  val pekkoTestkit      = "org.apache.pekko"           %% "pekko-testkit"       % pekko
  val playwright        = "com.microsoft.playwright"    % "playwright"          % "1.50.0"
}

// format: on
