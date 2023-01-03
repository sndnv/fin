package fin.server.api.routes

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import fin.server.api.responses.TransactionSummary
import fin.server.model.Period
import fin.server.security.CurrentUser

class Reports()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      path("categories") {
        extractExecutionContext { implicit ec =>
          val result = for {
            fromTransactions <- ctx.persistence.transactions.categories()
            fromForecasts <- ctx.persistence.forecasts.categories()
          } yield {
            (fromTransactions ++ fromForecasts).distinct
          }

          onSuccess(result) { categories =>
            log.debugN("User [{}] retrieved [{}] categories", currentUser, categories.length)
            discardEntity & complete(categories)
          }
        }
      },
      path("summary") {
        parameter("period".as[Period].?(default = Period.current)) { period =>
          onSuccess(ctx.persistence.transactions.to(period = period)) { transactions =>
            val summary = transactions.foldLeft(TransactionSummary.empty)(_ withTransaction _)

            log.debugN("User [{}] retrieved summary for period [{}]", currentUser, period)
            discardEntity & complete(summary)
          }
        }
      }
    )
}

object Reports {
  def apply()(implicit ctx: RoutesContext): Reports = new Reports()
}
