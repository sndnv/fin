package fin.server.persistence.forecasts

import akka.Done
import fin.server.model.{Forecast, Period}

import scala.concurrent.Future

trait ForecastStore {
  def create(forecast: Forecast): Future[Done]
  def update(forecast: Forecast): Future[Done]
  def delete(forecast: Forecast.Id): Future[Boolean]
  def get(forecast: Forecast.Id): Future[Option[Forecast]]
  def available(forPeriod: Period): Future[Seq[Forecast]]
  def all(forPeriod: Period): Future[Seq[Forecast]]
  def categories(): Future[Seq[String]]

  def init(): Future[Done]
  def drop(): Future[Done]
}
