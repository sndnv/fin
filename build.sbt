import sbt.Keys._

lazy val projectName = "fin"

name     := projectName
licenses := Seq("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
homepage := Some(url("https://github.com/sndnv/fin"))

ThisBuild / scalaVersion := "2.13.10"

lazy val versions = new {
  // akka
  val akka         = "2.6.20"
  val akkaHttp     = "10.2.10"
  val akkaHttpCors = "1.1.3"
  val akkaJson     = "1.39.2"

  // persistence
  val slick    = "3.4.1"
  val postgres = "42.5.1"
  val h2       = "2.1.214"

  // telemetry
  val openTelemetry           = "1.21.0"
  val openTelemetryPrometheus = s"$openTelemetry-alpha"
  val prometheus              = "0.16.0"

  // testing
  val scalaCheck = "1.17.0"
  val scalaTest  = "3.2.14"
  val mockito    = "1.17.12"

  // misc
  val playJson = "2.9.3"
  val jose4j   = "0.9.2"
  val logback  = "1.4.5"
  val jaxbApi  = "2.3.1"
  val scalaXml = "2.1.0"
}

lazy val jdkDockerImage = "openjdk:17-slim-bullseye"

lazy val server = (project in file("./server"))
  .settings(commonSettings)
  .settings(dockerSettings)
  .settings(buildInfoSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka"      %% "akka-actor"                        % versions.akka,
      "com.typesafe.akka"      %% "akka-actor-typed"                  % versions.akka,
      "com.typesafe.akka"      %% "akka-stream"                       % versions.akka,
      "com.typesafe.akka"      %% "akka-slf4j"                        % versions.akka,
      "com.typesafe.akka"      %% "akka-http"                         % versions.akkaHttp,
      "com.typesafe.akka"      %% "akka-http-core"                    % versions.akkaHttp,
      "com.typesafe.akka"      %% "akka-http2-support"                % versions.akkaHttp,
      "org.bitbucket.b_c"       % "jose4j"                            % versions.jose4j,
      "com.typesafe.play"      %% "play-json"                         % versions.playJson,
      "de.heikoseeberger"      %% "akka-http-play-json"               % versions.akkaJson,
      "com.typesafe.slick"     %% "slick"                             % versions.slick,
      "com.h2database"          % "h2"                                % versions.h2,
      "org.postgresql"          % "postgresql"                        % versions.postgres,
      "ch.megard"              %% "akka-http-cors"                    % versions.akkaHttpCors,
      "io.opentelemetry"        % "opentelemetry-sdk"                 % versions.openTelemetry,
      "io.opentelemetry"        % "opentelemetry-exporter-prometheus" % versions.openTelemetryPrometheus,
      "ch.qos.logback"          % "logback-classic"                   % versions.logback,
      "io.prometheus"           % "simpleclient_hotspot"              % versions.prometheus,
      "javax.xml.bind"          % "jaxb-api"                          % versions.jaxbApi,
      "org.scala-lang.modules" %% "scala-xml"                         % versions.scalaXml,
      "org.scalacheck"         %% "scalacheck"                        % versions.scalaCheck % Test,
      "org.scalatest"          %% "scalatest"                         % versions.scalaTest  % Test,
      "com.typesafe.akka"      %% "akka-testkit"                      % versions.akka       % Test,
      "com.typesafe.akka"      %% "akka-stream-testkit"               % versions.akka       % Test,
      "com.typesafe.akka"      %% "akka-http-testkit"                 % versions.akkaHttp   % Test,
      "org.mockito"            %% "mockito-scala"                     % versions.mockito    % Test,
      "org.mockito"            %% "mockito-scala-scalatest"           % versions.mockito    % Test
    ),
    dockerBaseImage := jdkDockerImage
  )
  .enablePlugins(JavaAppPackaging, BuildInfoPlugin, ScalaxbPlugin)

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
    s"-P:wartremover:excluded:${(Compile / sourceManaged).value}"
  ),
  dependencyUpdatesFilter -= moduleFilter(organization = "com.typesafe.akka"),
  coverageExcludedPackages := "generated.*;scalaxb.*"
)

lazy val dockerSettings = Seq(
  packageName          := s"$projectName-${name.value}",
  executableScriptName := s"$projectName-${name.value}"
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
