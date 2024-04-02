package fin.server.persistence.mocks

import fin.server.model.Account
import fin.server.persistence.Migration
import fin.server.persistence.accounts.AccountStore
import org.apache.pekko.Done

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class MockAccountStore extends AccountStore {
  private val store = new ConcurrentHashMap[Int, Account]()

  override val tableName: String = "mock-account-store"

  override val migrations: Seq[Migration] = Seq.empty

  override def create(account: Account): Future[Done] = {
    store.put(
      if (account.id != 0) { account.id }
      else { store.size() + 1 },
      account
    )
    Future.successful(Done)
  }

  override def update(account: Account): Future[Done] =
    if (store.containsKey(account.id)) {
      store.put(account.id, account)
      Future.successful(Done)
    } else {
      Future.failed(new IllegalAccessException(s"Account [${account.id}] does not exist; update failed"))
    }

  override def delete(account: Account.Id): Future[Boolean] =
    Option(store.get(account)) match {
      case Some(existing) =>
        store.put(existing.id, existing.copy(removed = Some(Instant.now())))
        Future.successful(true)

      case None =>
        Future.successful(false)
    }

  override def get(account: Account.Id): Future[Option[Account]] =
    Future.successful(Option(store.get(account)))

  override def available(): Future[Seq[Account]] =
    Future.successful(store.values().asScala.toSeq.filter(_.removed.isEmpty))

  override def all(): Future[Seq[Account]] =
    Future.successful(store.values().asScala.toSeq)

  override def init(): Future[Done] = Future.successful(Done)

  override def drop(): Future[Done] = {
    store.clear()
    Future.successful(Done)
  }
}

object MockAccountStore {
  def apply(): MockAccountStore = new MockAccountStore()
}
