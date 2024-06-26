package fin.server.service

import com.typesafe.{config => typesafe}
import fin.server.api.ApiEndpoint
import fin.server.persistence.DefaultServerPersistence
import fin.server.security.authenticators.{DefaultUserAuthenticator, UserAuthenticator}
import fin.server.security.jwt.DefaultJwtAuthenticator
import fin.server.security.keys.RemoteKeyProvider
import fin.server.security.tls.EndpointContext
import fin.server.telemetry.metrics.MetricsExporter
import fin.server.telemetry.{DefaultTelemetryContext, TelemetryContext}
import fin.server.{api, BuildInfo}
import io.prometheus.client.hotspot.DefaultExports
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.{Logger, LoggerFactory}

import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

trait Service {
  import Service._

  private val serviceState: AtomicReference[State] = new AtomicReference[State](State.Starting)

  private implicit val system: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "fin-server-service"
  )

  private val rawConfig: typesafe.Config = system.settings.config.getConfig("fin.server")
  private val apiConfig: Config = Config(rawConfig.getConfig("service.api"))
  private val metricsConfig: Config = Config(rawConfig.getConfig("service.telemetry.metrics"))

  private val persistenceConfig = rawConfig.getConfig("persistence")
  private val authenticatorConfig = Config.UserAuthenticator(rawConfig.getConfig("authenticators.users"))

  private val apiEndpointConfig = ApiEndpoint.Config(config = rawConfig.getConfig("service.api.endpoint"))

  private implicit val ec: ExecutionContext = system.executionContext
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
  private val exporter: MetricsExporter = Telemetry.createMetricsExporter(config = metricsConfig)

  private implicit val telemetry: TelemetryContext = DefaultTelemetryContext(
    metricsProviders = Set(
      api.Metrics.default(meter = exporter.meter, namespace = Telemetry.Instrumentation)
    ).flatten
  )

  private implicit val serviceMode: ServiceMode = ServiceMode(rawConfig.getConfig("service"))

  private val authenticationEndpointContext: Option[EndpointContext] =
    EndpointContext(rawConfig.getConfig("clients.authentication.context"))

  private val authenticator: UserAuthenticator = DefaultUserAuthenticator(
    underlying = DefaultJwtAuthenticator(
      provider = RemoteKeyProvider(
        jwksEndpoint = authenticatorConfig.jwksEndpoint,
        context = authenticationEndpointContext,
        refreshInterval = authenticatorConfig.refreshInterval,
        refreshRetryInterval = authenticatorConfig.refreshRetryInterval,
        issuer = authenticatorConfig.issuer
      ),
      audience = authenticatorConfig.audience,
      identityClaim = authenticatorConfig.identityClaim,
      expirationTolerance = authenticatorConfig.expirationTolerance
    )
  )

  val serverPersistence: DefaultServerPersistence = DefaultServerPersistence(
    persistenceConfig = persistenceConfig
  )

  val endpoint: ApiEndpoint = ApiEndpoint(
    config = apiEndpointConfig,
    persistence = serverPersistence,
    authenticator = authenticator
  )

  log.info(
    s"""
       |Build(
       |  name:    ${BuildInfo.name}
       |  version: ${BuildInfo.version}
       |  time:    ${Instant.ofEpochMilli(BuildInfo.time).toString}
       |)""".stripMargin
  )

  log.info(
    s"""
       |Config(
       |  service:
       |    mode:         ${serviceMode.toString}
       |
       |    api:
       |      interface:  ${apiConfig.interface}
       |      port:       ${apiConfig.port.toString}
       |      context:
       |        enabled:  ${apiConfig.context.nonEmpty.toString}
       |        protocol: ${apiConfig.context.map(_.config.protocol).getOrElse("none")}
       |        keystore: ${apiConfig.context.flatMap(_.config.keyStoreConfig).map(_.storePath).getOrElse("none")}
       |
       |      endpoint:
       |        manage:
       |          ui:
       |            authorization-endpoint: ${apiEndpointConfig.manage.ui.authorizationEndpoint}
       |            token-endpoint:         ${apiEndpointConfig.manage.ui.tokenEndpoint}
       |            authentication:
       |              client-id:            ${apiEndpointConfig.manage.ui.authentication.clientId}
       |              redirect-uri:         ${apiEndpointConfig.manage.ui.authentication.redirectUri}
       |              scope:                ${apiEndpointConfig.manage.ui.authentication.scope}
       |              state-size:           ${apiEndpointConfig.manage.ui.authentication.stateSize.toString}
       |              code-verifier-size:   ${apiEndpointConfig.manage.ui.authentication.codeVerifierSize.toString}
       |            cookies:
       |              authentication-token: ${apiEndpointConfig.manage.ui.cookies.authenticationToken}
       |              code-verifier:        ${apiEndpointConfig.manage.ui.cookies.codeVerifier}
       |              state:                ${apiEndpointConfig.manage.ui.cookies.state}
       |              secure:               ${apiEndpointConfig.manage.ui.cookies.secure.toString}
       |              expiration-tolerance: ${apiEndpointConfig.manage.ui.cookies.expirationTolerance.toString}
       |
       |    telemetry:
       |      metrics:
       |        namespace: ${Telemetry.Instrumentation}
       |        interface: ${metricsConfig.interface}
       |        port:      ${metricsConfig.port.toString}
       |
       |  authenticators:
       |    users:
       |      issuer:                 ${authenticatorConfig.issuer}
       |      audience:               ${authenticatorConfig.audience}
       |      identity-claim:         ${authenticatorConfig.identityClaim}
       |      jwks-endpoint:          ${authenticatorConfig.jwksEndpoint}
       |      refresh-interval:       ${authenticatorConfig.refreshInterval.toSeconds.toString} s
       |      refresh-retry-interval: ${authenticatorConfig.refreshRetryInterval.toMillis.toString} ms
       |      expiration-tolerance:   ${authenticatorConfig.expirationTolerance.toMillis.toString} ms
       |
       |  persistence:
       |    database:
       |      server:
       |        profile:    ${serverPersistence.profile.getClass.getSimpleName}
       |        url:        ${serverPersistence.databaseUrl}
       |        driver:     ${serverPersistence.databaseDriver}
       |        keep-alive: ${serverPersistence.databaseKeepAlive.toString}
       |        init:       ${serverPersistence.databaseInit.toString}
       |)""".stripMargin
  )

  locally {
    val _ = start()
  }

  locally {
    val _ = sys.addShutdownHook(stop())
  }

  def start(): Future[Done] =
    for {
      _ <- serverPersistence.init()
      _ <- serverPersistence.migrate()
      _ = log.info("Service API starting on [{}:{}]...", apiConfig.interface, apiConfig.port)
      _ <- endpoint.start(
        interface = apiConfig.interface,
        port = apiConfig.port,
        context = apiConfig.context
      )
    } yield {
      serviceState.set(State.Started)
      Done
    }

  def stop(): Unit = {
    log.info("Service stopping...")
    locally { val _ = exporter.shutdown() }
    locally { val _ = system.terminate() }
  }

  def state: State = serviceState.get()
}

object Service {
  final case class Config(
    interface: String,
    port: Int,
    context: Option[EndpointContext]
  )

  object Config {
    def apply(config: typesafe.Config): Config =
      Config(
        interface = config.getString("interface"),
        port = config.getInt("port"),
        context = EndpointContext(config.getConfig("context"))
      )

    final case class UserAuthenticator(
      issuer: String,
      audience: String,
      identityClaim: String,
      jwksEndpoint: String,
      refreshInterval: FiniteDuration,
      refreshRetryInterval: FiniteDuration,
      expirationTolerance: FiniteDuration
    )

    object UserAuthenticator {
      def apply(config: typesafe.Config): UserAuthenticator =
        UserAuthenticator(
          issuer = config.getString("issuer"),
          audience = config.getString("audience"),
          identityClaim = config.getString("identity-claim"),
          jwksEndpoint = config.getString("jwks-endpoint"),
          refreshInterval = config.getDuration("refresh-interval").toMillis.millis,
          refreshRetryInterval = config.getDuration("refresh-retry-interval").toMillis.millis,
          expirationTolerance = config.getDuration("expiration-tolerance").toMillis.millis
        )
    }
  }

  object Telemetry {
    final val Instrumentation: String = "fin_server"

    def createMetricsExporter(config: Config): MetricsExporter =
      MetricsExporter.Prometheus.asProxyRegistry(
        instrumentation = Instrumentation,
        interface = config.interface,
        port = config.port
      ) { registry => DefaultExports.register(registry) }
  }

  sealed trait State
  object State {
    case object Starting extends State
    case object Started extends State
  }
}
