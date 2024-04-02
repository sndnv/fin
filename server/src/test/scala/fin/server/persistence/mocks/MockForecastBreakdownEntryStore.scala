package fin.server.persistence.mocks

import fin.server.model.{ForecastBreakdownEntry, Period}
import fin.server.persistence.Migration
import fin.server.persistence.forecasts.ForecastBreakdownEntryStore
import org.apache.pekko.Done

import java.time.Instant
import java.time.temporal.ChronoField
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class MockForecastBreakdownEntryStore extends ForecastBreakdownEntryStore {
  private val store = new ConcurrentHashMap[Int, ForecastBreakdownEntry]()

  override val tableName: String = "mock-forecast-breakdown-entry-store"

  override val migrations: Seq[Migration] = Seq.empty

  override def create(entry: ForecastBreakdownEntry): Future[Done] = {
    store.put(
      if (entry.id != 0) { entry.id }
      else { store.size() + 1 },
      entry
    )
    Future.successful(Done)
  }

  override def update(entry: ForecastBreakdownEntry): Future[Done] =
    if (store.containsKey(entry.id)) {
      store.put(entry.id, entry)
      Future.successful(Done)
    } else {
      Future.failed(new IllegalAccessException(s"Forecast breakdown entry [${entry.id}] does not exist; update failed"))
    }

  override def delete(entry: ForecastBreakdownEntry.Id): Future[Boolean] =
    Option(store.get(entry)) match {
      case Some(existing) =>
        store.put(existing.id, existing.copy(removed = Some(Instant.now())))
        Future.successful(true)

      case None =>
        Future.successful(false)
    }

  override def get(entry: ForecastBreakdownEntry.Id): Future[Option[ForecastBreakdownEntry]] =
    Future.successful(Option(store.get(entry)))

  override def available(forPeriod: Period): Future[Seq[ForecastBreakdownEntry]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter { t =>
          t.removed.isEmpty && t.date.get(ChronoField.YEAR) == forPeriod.year && t.date
            .get(ChronoField.MONTH_OF_YEAR) == forPeriod.month.getValue
        }
    )

  override def all(forPeriod: Period): Future[Seq[ForecastBreakdownEntry]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter { t =>
          t.date.get(ChronoField.YEAR) == forPeriod.year && t.date.get(ChronoField.MONTH_OF_YEAR) == forPeriod.month.getValue
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

object MockForecastBreakdownEntryStore {
  def apply(): MockForecastBreakdownEntryStore = new MockForecastBreakdownEntryStore()
}
