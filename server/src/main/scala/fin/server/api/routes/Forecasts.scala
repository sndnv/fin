package fin.server.api.routes

import fin.server.api.requests.{CreateForecast, CreateForecastBreakdownEntry, UpdateForecast, UpdateForecastBreakdownEntry}
import fin.server.model.Period
import fin.server.persistence.forecasts.{ForecastBreakdownEntryStore, ForecastStore}
import fin.server.security.CurrentUser
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

class Forecasts()(implicit ctx: RoutesContext) extends ApiRoutes {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  private val forecasts: ForecastStore = ctx.persistence.forecasts
  private val breakdownEntries: ForecastBreakdownEntryStore = ctx.persistence.forecastBreakdownEntries

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
              val result = if (includeRemoved) forecasts.all(forPeriod = period) else forecasts.available(forPeriod = period)
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
              onSuccess(forecasts.create(forecast)) { _ =>
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
            onSuccess(forecasts.get(forecastId)) {
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
              onSuccess(forecasts.get(forecastId)) {
                case Some(existing) =>
                  onSuccess(forecasts.update(request.toForecast(existing))) { _ =>
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
            onSuccess(forecasts.delete(forecastId)) { deleted =>
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
          onSuccess(forecasts.categories()) { categories =>
            import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

            log.debugN("User [{}] retrieved [{}] forecast categories", currentUser, categories.length)
            discardEntity & complete(categories)
          }
        }
      },
      pathPrefix("breakdown") {
        concat(
          pathEndOrSingleSlash {
            concat(
              get {
                parameters(
                  "period".as[Period].?(default = Period.current),
                  "include_removed".as[Boolean].?(default = false)
                ) { (period, includeRemoved) =>
                  val result =
                    if (includeRemoved) breakdownEntries.all(forPeriod = period)
                    else breakdownEntries.available(forPeriod = period)

                  onSuccess(result) { entries =>
                    log.debugN(
                      "User [{}] successfully retrieved [{}] forecast breakdown entries for period [{}]",
                      currentUser,
                      entries.size,
                      period
                    )
                    discardEntity & complete(entries)
                  }
                }
              },
              post {
                entity(as[CreateForecastBreakdownEntry]) { request =>
                  val entry = request.toForecastBreakdownEntry
                  onSuccess(breakdownEntries.create(entry)) { _ =>
                    log.debugN(
                      "User [{}] successfully created forecast breakdown entry for account [{}]",
                      currentUser,
                      entry.account
                    )
                    complete(StatusCodes.OK)
                  }
                }
              }
            )
          },
          path(IntNumber) { entryId =>
            concat(
              get {
                onSuccess(breakdownEntries.get(entryId)) {
                  case Some(forecast) =>
                    log.debugN("User [{}] successfully retrieved forecast breakdown entry [{}]", currentUser, entryId)
                    discardEntity & complete(forecast)

                  case None =>
                    log.warnN("User [{}] failed to retrieve forecast breakdown entry [{}]", currentUser, entryId)
                    discardEntity & complete(StatusCodes.NotFound)
                }
              },
              put {
                entity(as[UpdateForecastBreakdownEntry]) { request =>
                  onSuccess(breakdownEntries.get(entryId)) {
                    case Some(existing) =>
                      onSuccess(breakdownEntries.update(request.toForecastBreakdownEntry(existing))) { _ =>
                        log.debugN("User [{}] successfully updated forecast breakdown entry [{}]", currentUser, entryId)
                        complete(StatusCodes.OK)
                      }

                    case None =>
                      log.warnN("User [{}] failed to update missing forecast breakdown entry [{}]", currentUser, entryId)
                      complete(StatusCodes.NotFound)
                  }
                }
              },
              delete {
                onSuccess(breakdownEntries.delete(entryId)) { deleted =>
                  if (deleted) {
                    log.debugN("User [{}] successfully deleted forecast breakdown entry [{}]", currentUser, entryId)
                  } else {
                    log.warnN("User [{}] failed to delete forecast breakdown entry [{}]", currentUser, entryId)
                  }
                  discardEntity & complete(StatusCodes.OK)
                }
              }
            )
          },
          path("categories") {
            get {
              onSuccess(breakdownEntries.categories()) { categories =>
                import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._

                log.debugN("User [{}] retrieved [{}] forecast breakdown entry categories", currentUser, categories.length)
                discardEntity & complete(categories)
              }
            }
          }
        )
      }
    )
}

object Forecasts {
  def apply()(implicit ctx: RoutesContext): Forecasts = new Forecasts()
}
