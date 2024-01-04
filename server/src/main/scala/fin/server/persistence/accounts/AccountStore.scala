package fin.server.persistence.accounts

import fin.server.model.Account
import org.apache.pekko.Done

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
