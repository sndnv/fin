package fin.server.persistence.accounts

import akka.Done
import fin.server.model.Account

import scala.concurrent.Future

trait AccountStore {
  def create(account: Account): Future[Done]
  def update(account: Account): Future[Done]
  def delete(account: Account.Id): Future[Boolean]
  def get(account: Account.Id): Future[Option[Account]]
  def available(): Future[Seq[Account]]
  def all(): Future[Seq[Account]]

  def init(): Future[Done]
  def drop(): Future[Done]
}
