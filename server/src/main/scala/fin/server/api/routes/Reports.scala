package fin.server.api.routes

import fin.server.api.responses.TransactionBreakdown.BreakdownType
import fin.server.api.responses.{TransactionBreakdown, TransactionSummary}
import fin.server.model.Period
import fin.server.security.CurrentUser
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller

import java.time.LocalDate

class Reports()(implicit ctx: RoutesContext) extends ApiRoutes {
  import Reports._
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
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

            log.debugN("User [{}] retrieved transaction summary for period [{}]", currentUser, period)
            discardEntity & complete(summary)
          }
        }
      },
      path("breakdown") {
        parameters(
          "type".as[BreakdownType],
          "start".as[LocalDate],
          "end".as[LocalDate],
          "account".as[Int]
        ) { case (breakdownType, start, end, account) =>
          onSuccess(ctx.persistence.transactions.between(start = start, end = end, account = account)) { transactions =>
            val breakdown = TransactionBreakdown(breakdownType, transactions)

            log.debugN(
              "User [{}] retrieved transaction breakdown for account [{}] with [type={},start={},end={}]",
              currentUser,
              account,
              breakdownType,
              start,
              end
            )

            discardEntity & complete(breakdown)
          }
        }
      }
    )
}

object Reports {
  def apply()(implicit ctx: RoutesContext): Reports = new Reports()

  implicit val stringToBreakdownType: Unmarshaller[String, BreakdownType] = Unmarshaller.strict(BreakdownType.apply)

  implicit val stringToLocalDate: Unmarshaller[String, LocalDate] = Unmarshaller.strict(LocalDate.parse)
}
