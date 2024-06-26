package fin.server.persistence.forecasts

import fin.server.model.{Forecast, Period}
import fin.server.persistence.Store
import org.apache.pekko.Done

import scala.concurrent.Future

trait ForecastStore extends Store {
  def create(forecast: Forecast): Future[Done]
  def update(forecast: Forecast): Future[Done]
  def delete(forecast: Forecast.Id): Future[Boolean]
  def get(forecast: Forecast.Id): Future[Option[Forecast]]
  def available(forPeriod: Period): Future[Seq[Forecast]]
  def all(forPeriod: Period): Future[Seq[Forecast]]
  def categories(): Future[Seq[String]]
}
