package fin.server.api

import fin.server.api.directives.{EntityDiscardingDirectives, LoggingDirectives}
import fin.server.api.routes._
import fin.server.persistence.ServerPersistence
import fin.server.security.authenticators.UserAuthenticator
import fin.server.security.tls.EndpointContext
import fin.server.service.ServiceMode
import fin.server.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.http.cors.scaladsl.CorsDirectives._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server._
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.Future
import scala.util.{Failure, Success}

class ApiEndpoint(
  config: ApiEndpoint.Config,
  persistence: ServerPersistence,
  authenticator: UserAuthenticator
)(implicit val system: ActorSystem[Nothing], override val telemetry: TelemetryContext, mode: ServiceMode)
    extends LoggingDirectives
    with EntityDiscardingDirectives {
  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val context: RoutesContext = RoutesContext(persistence, log)

  private val manage = Manage(config = config.manage)
  private val accounts = Accounts()
  private val transactions = Transactions()
  private val forecasts = Forecasts()
  private val categories = Categories()
  private val reports = Reports()
  private val service = Service()

  private val sanitizingExceptionHandler: ExceptionHandler = handlers.Sanitizing.create(log)
  private val rejectionHandler: RejectionHandler = handlers.Rejection.create(log)

  val endpointRoutes: Route =
    (extractMethod & extractUri) { (method, uri) =>
      concat(
        pathPrefixTest(Slash | PathEnd | "manage") {
          concat(
            pathEndOrSingleSlash { redirect("manage", StatusCodes.PermanentRedirect) },
            pathPrefix("manage") { manage.routes }
          )
        },
        extractCredentials {
          case Some(credentials) =>
            onComplete(authenticator.authenticate(credentials)) {
              case Success(user) =>
                concat(
                  pathPrefix("accounts") { accounts.routes(currentUser = user) },
                  pathPrefix("transactions") { transactions.routes(currentUser = user) },
                  pathPrefix("forecasts") { forecasts.routes(currentUser = user) },
                  pathPrefix("categories") { categories.routes(currentUser = user) },
                  pathPrefix("reports") { reports.routes(currentUser = user) },
                  pathPrefix("service") { service.routes }
                )

              case Failure(e) =>
                log.warn(
                  "Rejecting [{}] request for [{}] with invalid credentials: [{} - {}]",
                  method.value,
                  uri,
                  e.getClass.getSimpleName,
                  e.getMessage
                )

                discardEntity & complete(StatusCodes.Unauthorized)
            }

          case None =>
            log.warn("Rejecting [{}] request for [{}] with no credentials", method.value, uri)

            discardEntity & complete(StatusCodes.Unauthorized)
        }
      )
    }

  def start(interface: String, port: Int, context: Option[EndpointContext]): Future[Http.ServerBinding] = {
    import EndpointContext._

    Http()
      .newServerAt(interface = interface, port = port)
      .withContext(context = context)
      .bindFlow(
        handlerFlow = withLoggedRequestAndResponse {
          handleRejections(corsRejectionHandler) {
            cors() {
              (handleExceptions(sanitizingExceptionHandler) & handleRejections(rejectionHandler)) {
                endpointRoutes
              }
            }
          }
        }
      )
  }
}

object ApiEndpoint {
  def apply(
    config: Config,
    persistence: ServerPersistence,
    authenticator: UserAuthenticator
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext, mode: ServiceMode) =
    new ApiEndpoint(config = config, persistence = persistence, authenticator = authenticator)

  final case class Config(
    manage: Manage.Config
  )

  object Config {
    def apply(config: com.typesafe.config.Config): Config = Config(
      manage = Manage.Config(config.getConfig("manage"))
    )
  }
}
