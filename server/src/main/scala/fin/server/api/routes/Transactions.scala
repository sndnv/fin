package fin.server.api.routes

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import fin.server.api.directives.XmlDirectives
import fin.server.api.requests.{CreateTransaction, UpdateTransaction}
import fin.server.api.responses.TransactionImportResult
import fin.server.imports
import fin.server.model.Period
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.CurrentUser

class Transactions()(implicit ctx: RoutesContext) extends ApiRoutes with XmlDirectives {
  import Transactions._

  private val store: TransactionStore = ctx.persistence.transactions

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
        import fin.server.api.Formats._

        concat(
          get {
            parameters(
              "period".as[Period].?(default = Period.current),
              "include_removed".as[Boolean].?(default = false)
            ) { (period, includeRemoved) =>
              val result = if (includeRemoved) store.all(forPeriod = period) else store.available(forPeriod = period)
              onSuccess(result) { transactions =>
                log.debugN(
                  "User [{}] successfully retrieved [{}] transactions for period [{}]",
                  currentUser,
                  transactions.size,
                  period
                )
                discardEntity & complete(transactions)
              }
            }
          },
          post {
            entity(as[CreateTransaction]) { request =>
              val transaction = request.toTransaction
              onSuccess(store.create(transaction)) { _ =>
                log.debugN("User [{}] successfully created transaction [{}]", currentUser, transaction.id)
                complete(StatusCodes.OK)
              }
            }
          }
        )
      },
      path(JavaUUID) { transactionId =>
        import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
        import fin.server.api.Formats._

        concat(
          get {
            onSuccess(store.get(transactionId)) {
              case Some(transaction) =>
                log.debugN("User [{}] successfully retrieved transaction [{}]", currentUser, transactionId)
                discardEntity & complete(transaction)

              case None =>
                log.warnN("User [{}] failed to retrieve transaction [{}]", currentUser, transactionId)
                discardEntity & complete(StatusCodes.NotFound)
            }
          },
          put {
            entity(as[UpdateTransaction]) { request =>
              onSuccess(store.get(transactionId)) {
                case Some(existing) =>
                  onSuccess(store.update(request.toTransaction(existing))) { _ =>
                    log.debugN("User [{}] successfully updated transaction [{}]", currentUser, transactionId)
                    complete(StatusCodes.OK)
                  }
                case None =>
                  log.warnN("User [{}] failed to update missing transaction [{}]", currentUser, transactionId)
                  complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            onSuccess(store.delete(transactionId)) { deleted =>
              if (deleted) {
                log.debugN("User [{}] successfully deleted transaction [{}]", currentUser, transactionId)
              } else {
                log.warnN("User [{}] failed to delete transaction [{}]", currentUser, transactionId)
              }
              discardEntity & complete(StatusCodes.OK)
            }
          }
        )
      },
      pathPrefix("import") {
        post {
          parameters(
            "import_type".as[ImportType],
            "for_account".as[Int],
            "upload_type".as[XmlDirectives.UploadType]
          ) { case (ImportType.Camt053, forAccount, uploadType) =>
            xmlUpload[generated.Document](uploadType)(implicitly, log) { documents =>
              extractExecutionContext { implicit ec =>
                val data = for {
                  accounts <- ctx.persistence.accounts.all()
                  mappings <- ctx.persistence.categoryMappings.available()
                } yield {
                  (accounts, mappings)
                }

                onSuccess(data) {
                  case (accounts, mappings) if accounts.exists(_.id == forAccount) =>
                    val transactions = documents
                      .flatMap { document =>
                        imports.FromCamt053.transactions(
                          forAccount = forAccount,
                          fromStatements = document.BkToCstmrStmt.Stmt,
                          withTargetAccountMapping = { target => accounts.find(_.externalId == target).map(_.id) }
                        )(log)
                      }
                      .map { transaction =>
                        mappings.filter(_.matches(transaction.notes)).toList.sortBy(_.id) match {
                          case _ :+ mapping => transaction.copy(category = mapping.category)
                          case _            => transaction
                        }
                      }

                    onSuccess(store.load(transactions)) { case (successful, existing) =>
                      import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
                      import fin.server.api.Formats._

                      log.debugN(
                        "User [{}] successfully imported [{}] CAMT.053 transaction(s) from [{}] document(s)",
                        currentUser,
                        transactions.length,
                        documents.length
                      )

                      complete(
                        TransactionImportResult(
                          provided = TransactionImportResult.Provided(
                            documents = documents.length,
                            statements = documents.map(_.BkToCstmrStmt.Stmt.length).sum,
                            entries = documents.map(_.BkToCstmrStmt.Stmt.map(_.Ntry.length).sum).sum
                          ),
                          imported = TransactionImportResult.Imported(
                            successful = successful,
                            existing = existing
                          )
                        )
                      )
                    }

                  case _ =>
                    log.warnN(
                      "User [{}] failed to import CAMT.053 transactions; account [{}] does not exist",
                      currentUser,
                      forAccount
                    )
                    complete(StatusCodes.BadRequest)
                }
              }
            }
          }
        }
      },
      path("search") {
        get {
          parameter("query".as[String]) { query =>
            query.trim match {
              case trimmed if trimmed.nonEmpty =>
                onSuccess(store.search(query)) { transactions =>
                  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
                  import fin.server.api.Formats._

                  log.debugN("User [{}] retrieved [{}] transactions for query [{}]", currentUser, transactions.length, query)
                  discardEntity & complete(transactions)
                }
              case _ =>
                log.debugN("User [{}] provided an invalid or empty query parameter: [{}]", currentUser, query)
                discardEntity & complete(StatusCodes.BadRequest)
            }
          }
        }
      },
      path("categories") {
        get {
          onSuccess(store.categories()) { categories =>
            import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

            log.debugN("User [{}] retrieved [{}] transaction categories", currentUser, categories.length)
            discardEntity & complete(categories)
          }
        }
      }
    )
}

object Transactions {
  def apply()(implicit ctx: RoutesContext): Transactions = new Transactions()

  implicit val stringToImportType: Unmarshaller[String, ImportType] = Unmarshaller.strict(ImportType.apply)

  sealed trait ImportType
  object ImportType {
    case object Camt053 extends ImportType

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def apply(value: String): ImportType =
      value.trim.toLowerCase match {
        case "camt053" => Camt053
        case _         => throw new IllegalArgumentException(s"Unsupported import type provided: [$value]")
      }
  }
}
