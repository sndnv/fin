package fin.server.persistence.forecasts

import fin.server.model.{ForecastBreakdownEntry, Period}
import fin.server.persistence.Store
import org.apache.pekko.Done

import scala.concurrent.Future

trait ForecastBreakdownEntryStore extends Store {
  def create(entry: ForecastBreakdownEntry): Future[Done]
  def update(entry: ForecastBreakdownEntry): Future[Done]
  def delete(entry: ForecastBreakdownEntry.Id): Future[Boolean]
  def get(entry: ForecastBreakdownEntry.Id): Future[Option[ForecastBreakdownEntry]]
  def available(forPeriod: Period): Future[Seq[ForecastBreakdownEntry]]
  def all(forPeriod: Period): Future[Seq[ForecastBreakdownEntry]]
  def categories(): Future[Seq[String]]
}
