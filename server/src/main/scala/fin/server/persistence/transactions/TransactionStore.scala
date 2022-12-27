package fin.server.persistence.transactions

import akka.Done
import fin.server.model.{Period, Transaction}

import scala.concurrent.Future

trait TransactionStore {
  def create(transaction: Transaction): Future[Done]
  def load(transactions: Seq[Transaction]): Future[(Int, Int)]
  def update(transaction: Transaction): Future[Done]
  def delete(transaction: Transaction.Id): Future[Boolean]
  def get(transaction: Transaction.Id): Future[Option[Transaction]]
  def available(forPeriod: Period): Future[Seq[Transaction]]
  def all(forPeriod: Period): Future[Seq[Transaction]]
  def search(query: String): Future[Seq[Transaction]]
  def categories(): Future[Seq[String]]

  def init(): Future[Done]
  def drop(): Future[Done]
}
