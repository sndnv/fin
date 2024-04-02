package fin.server.persistence.mocks

import fin.server.model.{Account, Period, Transaction}
import fin.server.persistence.Migration
import fin.server.persistence.transactions.TransactionStore
import org.apache.pekko.Done

import java.time.temporal.ChronoField
import java.time.{Instant, LocalDate}
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class MockTransactionStore extends TransactionStore {
  private val store = new ConcurrentHashMap[UUID, Transaction]()

  override val tableName: String = "mock-transaction-store"

  override val migrations: Seq[Migration] = Seq.empty

  override def create(transaction: Transaction): Future[Done] = {
    store.put(transaction.id, transaction)
    Future.successful(Done)
  }

  override def load(transactions: Seq[Transaction]): Future[(Int, Int)] = {
    transactions.foreach(transaction => store.put(transaction.id, transaction))
    Future.successful(transactions.length -> 0)
  }

  override def update(transaction: Transaction): Future[Done] =
    if (store.containsKey(transaction.id)) {
      store.put(transaction.id, transaction)
      Future.successful(Done)
    } else {
      Future.failed(new IllegalAccessException(s"Transaction [${transaction.id}] does not exist; update failed"))
    }

  override def delete(transaction: Transaction.Id): Future[Boolean] =
    Option(store.get(transaction)) match {
      case Some(existing) =>
        store.put(existing.id, existing.copy(removed = Some(Instant.now())))
        Future.successful(true)

      case None =>
        Future.successful(false)
    }

  override def get(transaction: Transaction.Id): Future[Option[Transaction]] =
    Future.successful(Option(store.get(transaction)))

  override def available(forPeriod: Period): Future[Seq[Transaction]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter(t =>
          t.removed.isEmpty && t.date
            .get(ChronoField.YEAR) == forPeriod.year && t.date
            .get(ChronoField.MONTH_OF_YEAR) == forPeriod.month.getValue
        )
    )

  override def all(forPeriod: Period): Future[Seq[Transaction]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter(t =>
          t.date.get(ChronoField.YEAR) == forPeriod.year
            && t.date.get(ChronoField.MONTH_OF_YEAR) == forPeriod.month.getValue
        )
    )

  override def to(period: Period): Future[Seq[Transaction]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter(t => t.date.isBefore(period.atLastDayOfMonth) || t.date.isEqual(period.atLastDayOfMonth))
    )

  override def search(query: String): Future[Seq[Transaction]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter(t => t.externalId.contains(query) || t.category.contains(query) || t.notes.exists(_.contains(query)))
    )

  override def categories(): Future[Seq[String]] =
    Future.successful(store.values().asScala.toSeq.map(_.category).distinct)

  override def between(start: LocalDate, end: LocalDate, account: Account.Id): Future[Seq[Transaction]] =
    Future.successful(
      store
        .values()
        .asScala
        .toSeq
        .filter(t =>
          t.from == account && (t.date.isEqual(start) || t.date.isAfter(start)) && (t.date.isEqual(end) || t.date.isBefore(end))
        )
    )

  override def init(): Future[Done] = Future.successful(Done)

  override def drop(): Future[Done] = Future.successful(Done)
}

object MockTransactionStore {
  def apply(): MockTransactionStore = new MockTransactionStore()
}
