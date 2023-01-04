package fin.server.persistence.mocks

import akka.Done
import fin.server.model.{Forecast, Period}
import fin.server.persistence.forecasts.ForecastStore

import java.time.Instant
import java.time.temporal.ChronoField
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class MockForecastStore extends ForecastStore {
  private val store = new ConcurrentHashMap[Int, Forecast]()

  override def create(forecast: Forecast): Future[Done] = {
    store.put(
      if (forecast.id != 0) { forecast.id }
      else { store.size() + 1 },
      forecast
    )
    Future.successful(Done)
  }

  override def update(forecast: Forecast): Future[Done] =
    if (store.containsKey(forecast.id)) {
      store.put(forecast.id, forecast)
      Future.successful(Done)
    } else {
      Future.failed(new IllegalAccessException(s"Forecast [${forecast.id}] does not exist; update failed"))
    }

  override def delete(forecast: Forecast.Id): Future[Boolean] =
    Option(store.get(forecast)) match {
      case Some(existing) =>
        store.put(existing.id, existing.copy(removed = Some(Instant.now())))
        Future.successful(true)

      case None =>
        Future.successful(false)
    }

  override def get(forecast: Forecast.Id): Future[Option[Forecast]] =
    Future.successful(Option(store.get(forecast)))

  override def available(forPeriod: Period): Future[Seq[Forecast]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter { t =>
          t.removed.isEmpty && t.date.forall(date =>
            date.get(ChronoField.YEAR) == forPeriod.year
              && date.get(ChronoField.MONTH_OF_YEAR) == forPeriod.month.getValue
          )
        }
    )

  override def all(forPeriod: Period): Future[Seq[Forecast]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter { t =>
          t.date.forall(date =>
            date.get(ChronoField.YEAR) == forPeriod.year
              && date.get(ChronoField.MONTH_OF_YEAR) == forPeriod.month.getValue
          )
        }
    )

  override def categories(): Future[Seq[String]] =
    Future.successful(store.values().asScala.toSeq.map(_.category).distinct)

  override def init(): Future[Done] = Future.successful(Done)

  override def drop(): Future[Done] = {
    store.clear()
    Future.successful(Done)
  }
}

object MockForecastStore {
  def apply(): MockForecastStore = new MockForecastStore()
}
