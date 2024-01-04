package fin.server.api.routes

import fin.server.api.directives.JsonDirectives
import fin.server.api.requests.{ApplyCategoryMappings, CreateCategoryMapping, UpdateCategoryMapping}
import fin.server.api.responses.CategoryMappingsApplicationResult
import fin.server.imports.Defaults
import fin.server.model.{CategoryMapping, Transaction}
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.security.CurrentUser
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

import java.time.Instant
import scala.concurrent.Future

class Categories()(implicit ctx: RoutesContext) extends ApiRoutes with JsonDirectives {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  private val store: CategoryMappingStore = ctx.persistence.categoryMappings

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            parameter("include_removed".as[Boolean].?(default = false)) { includeRemoved =>
              val result = if (includeRemoved) store.all() else store.available()
              onSuccess(result) { categoryMappings =>
                log.debugN("User [{}] successfully retrieved [{}] category mappings", currentUser, categoryMappings.size)
                discardEntity & complete(categoryMappings)
              }
            }
          },
          post {
            entity(as[CreateCategoryMapping]) { request =>
              val categoryMapping = request.toCategoryMapping
              onSuccess(store.create(categoryMapping)) { _ =>
                log.debugN("User [{}] successfully created category mapping [{}]", currentUser, categoryMapping.id)
                complete(StatusCodes.OK)
              }
            }
          }
        )
      },
      path(IntNumber) { categoryMappingId =>
        concat(
          get {
            onSuccess(store.get(categoryMappingId)) {
              case Some(categoryMapping) =>
                log.debugN("User [{}] successfully retrieved category mapping [{}]", currentUser, categoryMappingId)
                discardEntity & complete(categoryMapping)

              case None =>
                log.warnN("User [{}] failed to retrieve category mapping [{}]", currentUser, categoryMappingId)
                discardEntity & complete(StatusCodes.NotFound)
            }
          },
          put {
            entity(as[UpdateCategoryMapping]) { request =>
              onSuccess(store.get(categoryMappingId)) {
                case Some(existing) =>
                  onSuccess(store.update(request.toCategoryMapping(existing))) { _ =>
                    log.debugN("User [{}] successfully updated category mapping [{}]", currentUser, categoryMappingId)
                    complete(StatusCodes.OK)
                  }

                case None =>
                  log.warnN("User [{}] failed to update missing category mapping [{}]", currentUser, categoryMappingId)
                  complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            onSuccess(store.delete(categoryMappingId)) { deleted =>
              if (deleted) {
                log.debugN("User [{}] successfully deleted category mapping [{}]", currentUser, categoryMappingId)
              } else {
                log.warnN("User [{}] failed to delete category mapping [{}]", currentUser, categoryMappingId)
              }
              discardEntity & complete(StatusCodes.OK)
            }
          }
        )
      },
      path("apply") {
        put {
          entity(as[ApplyCategoryMappings]) { request =>
            extractExecutionContext { implicit ec =>
              val result = for {
                mappings <- store.available()
                original <- ctx.persistence.transactions.available(forPeriod = request.forPeriod)
                updated = Categories.applyMappingsToTransactions(mappings, original, request.overrideExisting)
                _ <- Future.sequence(updated.map(ctx.persistence.transactions.update))
              } yield {
                CategoryMappingsApplicationResult(
                  categoryMappingsFound = mappings.length,
                  transactionsFound = original.length,
                  transactionsUpdated = updated.length
                )
              }

              onSuccess(result) { result =>
                log.debugN(
                  "User [{}] successfully applied [{}] category mappings to [{} of {}] transactions",
                  currentUser,
                  result.categoryMappingsFound,
                  result.transactionsUpdated,
                  result.transactionsFound
                )

                complete(result)
              }
            }
          }
        }
      },
      path("import") {
        post {
          jsonUpload[CreateCategoryMapping](implicitly) { request =>
            extractExecutionContext { implicit ec =>
              val categoryMappings = request.map(_.toCategoryMapping)
              onSuccess(Future.sequence(categoryMappings.map(store.create)): Future[Seq[Done]]) { _ =>
                log.debugN("User [{}] successfully imported [{}] category mappings", currentUser, categoryMappings.length)
                complete(StatusCodes.OK)
              }
            }
          }
        }
      }
    )
}

object Categories {
  def apply()(implicit ctx: RoutesContext): Categories = new Categories()

  private def applyMappingsToTransactions(
    mappings: Seq[CategoryMapping],
    transactions: Seq[Transaction],
    overrideExisting: Boolean
  ): Seq[Transaction] = {
    val now = Instant.now()

    transactions
      .map(transaction => (transaction, mappings.filter(_.matches(transaction.notes)).toList.sortBy(_.id)))
      .collect {
        case (transaction, _ :+ mapping) if overrideExisting || transaction.category == Defaults.Category =>
          transaction.copy(category = mapping.category, updated = now)
      }
  }
}
