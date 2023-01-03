package fin.server.api.routes

import akka.actor.typed.scaladsl.LoggerOps
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import fin.server.api.requests.{CreateForecast, UpdateForecast}
import fin.server.model.Period
import fin.server.persistence.forecasts.ForecastStore
import fin.server.security.CurrentUser

class Forecasts()(implicit ctx: RoutesContext) extends ApiRoutes {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  private val store: ForecastStore = ctx.persistence.forecasts

  def routes(implicit currentUser: CurrentUser): Route =
    concat(
      pathEndOrSingleSlash {
        concat(
          get {
            parameters(
              "period".as[Period].?(default = Period.current),
              "include_removed".as[Boolean].?(default = false),
              "disregard_after".as[Int].?(default = 0)
            ) { (period, includeRemoved, disregardAfter) =>
              val result = if (includeRemoved) store.all(forPeriod = period) else store.available(forPeriod = period)
              onSuccess(result) { forecasts =>
                val filtered = forecasts.filter { forecast =>
                  forecast.date
                    .map(_.getDayOfMonth >= disregardAfter)
                    .getOrElse(forecast.disregardAfter >= disregardAfter)
                }

                log.debugN(
                  "User [{}] successfully retrieved [{} of {}] forecasts for period [{}]",
                  currentUser,
                  filtered.size,
                  forecasts.size,
                  period
                )
                discardEntity & complete(filtered)
              }
            }
          },
          post {
            entity(as[CreateForecast]) { request =>
              val forecast = request.toForecast
              onSuccess(store.create(forecast)) { _ =>
                log.debugN("User [{}] successfully created forecast for account [{}]", currentUser, forecast.account)
                complete(StatusCodes.OK)
              }
            }
          }
        )
      },
      path(IntNumber) { forecastId =>
        concat(
          get {
            onSuccess(store.get(forecastId)) {
              case Some(forecast) =>
                log.debugN("User [{}] successfully retrieved forecast [{}]", currentUser, forecastId)
                discardEntity & complete(forecast)

              case None =>
                log.warnN("User [{}] failed to retrieve forecast [{}]", currentUser, forecastId)
                discardEntity & complete(StatusCodes.NotFound)
            }
          },
          put {
            entity(as[UpdateForecast]) { request =>
              onSuccess(store.get(forecastId)) {
                case Some(existing) =>
                  onSuccess(store.update(request.toForecast(existing))) { _ =>
                    log.debugN("User [{}] successfully updated forecast [{}]", currentUser, forecastId)
                    complete(StatusCodes.OK)
                  }

                case None =>
                  log.warnN("User [{}] failed to update missing forecast [{}]", currentUser, forecastId)
                  complete(StatusCodes.NotFound)
              }
            }
          },
          delete {
            onSuccess(store.delete(forecastId)) { deleted =>
              if (deleted) {
                log.debugN("User [{}] successfully deleted forecast [{}]", currentUser, forecastId)
              } else {
                log.warnN("User [{}] failed to delete forecast [{}]", currentUser, forecastId)
              }
              discardEntity & complete(StatusCodes.OK)
            }
          }
        )
      },
      path("categories") {
        get {
          onSuccess(store.categories()) { categories =>
            import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

            log.debugN("User [{}] retrieved [{}] forecast categories", currentUser, categories.length)
            discardEntity & complete(categories)
          }
        }
      }
    )
}

object Forecasts {
  def apply()(implicit ctx: RoutesContext): Forecasts = new Forecasts()
}
