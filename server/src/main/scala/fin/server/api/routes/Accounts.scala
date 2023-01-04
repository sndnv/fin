package fin.server.api.routes

import akka.Done
import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import fin.server.api.directives.JsonDirectives
import fin.server.api.requests.{CreateAccount, UpdateAccount}
import fin.server.persistence.accounts.AccountStore
import fin.server.security.CurrentUser

import scala.concurrent.Future

class Accounts()(implicit ctx: RoutesContext) extends ApiRoutes with JsonDirectives {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  private val store: AccountStore = ctx.persistence.accounts

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            parameter("include_removed".as[Boolean].?(default = false)) { includeRemoved =>
              val result = if (includeRemoved) store.all() else store.available()
              onSuccess(result) { accounts =>
                log.debugN("User [{}] successfully retrieved [{}] accounts", currentUser, accounts.size)
                discardEntity & complete(accounts)
              }
            }
          },
          post {
            entity(as[CreateAccount]) { request =>
              val account = request.toAccount
              onSuccess(store.create(account)) { _ =>
                log.debugN("User [{}] successfully created account [{}]", currentUser, account.name)
                complete(StatusCodes.OK)
              }
            }
          }
        )
      },
      path(IntNumber) { accountId =>
        concat(
          get {
            onSuccess(store.get(accountId)) {
              case Some(account) =>
                log.debugN("User [{}] successfully retrieved account [{}]", currentUser, accountId)
                discardEntity & complete(account)

              case None =>
                log.warnN("User [{}] failed to retrieve account [{}]", currentUser, accountId)
                discardEntity & complete(StatusCodes.NotFound)
            }
          },
          put {
            entity(as[UpdateAccount]) { request =>
              onSuccess(store.get(accountId)) {
                case Some(existing) =>
                  onSuccess(store.update(request.toAccount(existing))) { _ =>
                    log.debugN("User [{}] successfully updated account [{}]", currentUser, accountId)
                    complete(StatusCodes.OK)
                  }

                case None =>
                  log.warnN("User [{}] failed to update missing account [{}]", currentUser, accountId)
                  complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            onSuccess(store.delete(accountId)) { deleted =>
              if (deleted) {
                log.debugN("User [{}] successfully deleted account [{}]", currentUser, accountId)
              } else {
                log.warnN("User [{}] failed to delete account [{}]", currentUser, accountId)
              }
              discardEntity & complete(StatusCodes.OK)
            }
          }
        )
      },
      path("import") {
        post {
          jsonUpload[CreateAccount](implicitly) { request =>
            extractExecutionContext { implicit ec =>
              val accounts = request.map(_.toAccount)

              onSuccess(store.all()) {
                case existing if accounts.exists(account => existing.exists(_.externalId == account.externalId)) =>
                  log.warnN(
                    "User [{}] failed to import [{}] accounts; one or more accounts already exist",
                    currentUser,
                    accounts.length
                  )
                  complete(StatusCodes.Conflict)

                case _ =>
                  onSuccess(Future.sequence(accounts.map(store.create)): Future[Seq[Done]]) { _ =>
                    log.debugN("User [{}] successfully imported [{}] accounts", currentUser, accounts.length)
                    complete(StatusCodes.OK)
                  }
              }
            }
          }
        }
      }
    )
}

object Accounts {
  def apply()(implicit ctx: RoutesContext): Accounts = new Accounts()
}
