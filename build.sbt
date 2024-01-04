import sbt.Keys.*

lazy val projectName = "fin"

name     := projectName
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/sndnv/fin"))

ThisBuild / scalaVersion := "2.13.12"

lazy val versions = new {
  // pekko
  val pekko         = "1.0.2"
  val pekkoHttp     = "1.0.0"
  val pekkoHttpCors = "1.0.0"
  val pekkoJson     = "2.3.3"

  // persistence
  val slick    = "3.4.1"
  val postgres = "42.7.1"
  val h2       = "2.2.224"

  // telemetry
  val openTelemetry           = "1.33.0"
  val openTelemetryPrometheus = s"$openTelemetry-alpha"
  val prometheus              = "0.16.0"

  // testing
  val scalaCheck = "1.17.0"
  val scalaTest  = "3.2.17"
  val mockito    = "1.17.30"

  // misc
  val playJson    = "2.10.3"
  val jose4j      = "0.9.4"
  val logback     = "1.4.14"
  val jaxbApi     = "2.3.1"
  val scalaXml    = "2.2.0"
  val scalaParser = "2.3.0"
}

lazy val jdkDockerImage = "openjdk:17-slim-bullseye"
lazy val dockerRegistry = "ghcr.io/sndnv/fin"

lazy val server = (project in file("./server"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.pekko"       %% "pekko-actor"                       % versions.pekko,
      "org.apache.pekko"       %% "pekko-actor-typed"                 % versions.pekko,
      "org.apache.pekko"       %% "pekko-stream"                      % versions.pekko,
      "org.apache.pekko"       %% "pekko-slf4j"                       % versions.pekko,
      "org.apache.pekko"       %% "pekko-http"                        % versions.pekkoHttp,
      "org.apache.pekko"       %% "pekko-http-core"                   % versions.pekkoHttp,
      "org.bitbucket.b_c"       % "jose4j"                            % versions.jose4j,
      "com.typesafe.play"      %% "play-json"                         % versions.playJson,
      "com.github.pjfanning"   %% "pekko-http-play-json"              % versions.pekkoJson,
      "com.typesafe.slick"     %% "slick"                             % versions.slick,
      "com.h2database"          % "h2"                                % versions.h2,
      "org.postgresql"          % "postgresql"                        % versions.postgres,
      "org.apache.pekko"       %% "pekko-http-cors"                   % versions.pekkoHttpCors,
      "io.opentelemetry"        % "opentelemetry-sdk"                 % versions.openTelemetry,
      "io.opentelemetry"        % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus,
      "ch.qos.logback"          % "logback-classic"                   % versions.logback,
      "io.prometheus"           % "simpleclient_hotspot"              % versions.prometheus,
      "javax.xml.bind"          % "jaxb-api"                          % versions.jaxbApi,
      "org.scala-lang.modules" %% "scala-xml"                         % versions.scalaXml,
      "org.scala-lang.modules" %% "scala-parser-combinators"          % versions.scalaParser,
      "org.scalacheck"         %% "scalacheck"                        % versions.scalaCheck % Test,
      "org.scalatest"          %% "scalatest"                         % versions.scalaTest  % Test,
      "org.apache.pekko"       %% "pekko-testkit"                     % versions.pekko      % Test,
      "org.apache.pekko"       %% "pekko-stream-testkit"              % versions.pekko      % Test,
      "org.apache.pekko"       %% "pekko-http-testkit"                % versions.pekkoHttp  % Test,
      "org.mockito"            %% "mockito-scala"                     % versions.mockito    % Test,
      "org.mockito"            %% "mockito-scala-scalatest"           % versions.mockito    % Test
    ),
    dockerBaseImage := jdkDockerImage
  )
  .enablePlugins(
    JavaAppPackaging,
    BuildInfoPlugin,
    ScalaxbPlugin,
    SbtTwirl
  )

lazy val excludedWarts = Seq(
  Wart.Any // too many false positives; more info - https://github.com/wartremover/wartremover/issues/454
)

lazy val commonSettings = Seq(
  Test / logBuffered       := false,
  Test / parallelExecution := false,
  Compile / compile / wartremoverWarnings ++= Warts.unsafe.filterNot(excludedWarts.contains),
  artifact                 := {
    val previous: Artifact = artifact.value
    previous.withName(name = s"$projectName-${previous.name}")
  },
  publishArtifact          := false,
  Docker / publish         := {},
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-unchecked",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused",
    "-Ywarn-extra-implicit",
    "-Ywarn-unused:implicits",
    "-Xlint:constant",
    "-Xlint:delayedinit-select",
    "-Xlint:doc-detached",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    "-Wconf:src=src_managed/.*:silent",
    "-Wconf:src=twirl/.*:silent",
    s"-P:wartremover:excluded:${(Compile / sourceManaged).value}"
  ),
  coverageExcludedPackages := "generated.*;scalaxb.*;(?:html|js).*;components\\.html.*"
)

lazy val dockerSettings = Seq(
  packageName          := s"$projectName-${name.value}",
  executableScriptName := s"$projectName-${name.value}",
  dockerRepository     := Some(dockerRegistry)
)

lazy val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](
    name,
    version,
    BuildInfoKey.action("time") { System.currentTimeMillis }
  ),
  buildInfoPackage := s"fin.${name.value}"
)

Global / concurrentRestrictions += Tags.limit(
  Tags.Test,
  sys.env.getOrElse("CI", "").trim.toLowerCase match {
    case "true" => 1
    case _      => 2
  }
)

addCommandAlias(
  name = "prepare",
  value = "; clean; compile; test:compile"
)

addCommandAlias(
  name = "check",
  value = "; dependencyUpdates; scalafmtSbtCheck; scalafmtCheck; test:scalafmtCheck"
)

addCommandAlias(
  name = "qa",
  value = "; prepare; check; coverage; test; coverageReport; coverageAggregate"
)
