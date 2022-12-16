package fin.server.api

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import fin.server.api.directives.{EntityDiscardingDirectives, LoggingDirectives}
import fin.server.api.routes.{Accounts, RoutesContext, Service, Transactions}
import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.authenticators.UserAuthenticator
import fin.server.security.tls.EndpointContext
import fin.server.telemetry.TelemetryContext
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

class ApiEndpoint(
  accountStore: AccountStore,
  transactionStore: TransactionStore,
  authenticator: UserAuthenticator
)(implicit val system: ActorSystem[Nothing], override val telemetry: TelemetryContext)
    extends LoggingDirectives
    with EntityDiscardingDirectives {
  private implicit val ec: ExecutionContextExecutor = system.executionContext

  override protected val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private implicit val context: RoutesContext = RoutesContext(accountStore, transactionStore, ec, log)

  private val service = Service()
  private val accounts = Accounts()
  private val transactions = Transactions()

  private val sanitizingExceptionHandler: ExceptionHandler = handlers.Sanitizing.create(log)
  private val rejectionHandler: RejectionHandler = handlers.Rejection.create(log)

  val endpointRoutes: Route =
    (extractMethod & extractUri) { (method, uri) =>
      extractCredentials {
        case Some(credentials) =>
          onComplete(authenticator.authenticate(credentials)) {
            case Success(user) =>
              concat(
                pathPrefix("accounts") { accounts.routes(currentUser = user) },
                pathPrefix("transactions") { transactions.routes(currentUser = user) },
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
    accountStore: AccountStore,
    transactionStore: TransactionStore,
    authenticator: UserAuthenticator
  )(implicit system: ActorSystem[Nothing], telemetry: TelemetryContext) =
    new ApiEndpoint(
      accountStore = accountStore,
      transactionStore = transactionStore,
      authenticator = authenticator
    )
}
