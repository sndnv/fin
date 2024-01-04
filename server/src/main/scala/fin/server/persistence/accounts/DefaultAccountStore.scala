package fin.server.persistence.accounts

import fin.server.model.Account
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector}
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class DefaultAccountStore(
  tableName: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#DatabaseDef
)(implicit val system: ActorSystem[Nothing])
    extends AccountStore {
  import profile.api._

  // this EC is only used for mapping ops after results are retrieved from the underlying DB;
  // Slick uses its own dispatcher(s) to handle blocking IO
  // - https://scala-slick.org/doc/3.3.1/database.html#database-thread-pool
  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.default())

  private class SlickAccountStore(tag: Tag) extends Table[Account](tag, tableName) {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def externalId: Rep[String] = column[String]("EXTERNAL_ID", O.Unique)
    def name: Rep[String] = column[String]("NAME")
    def description: Rep[String] = column[String]("DESCRIPTION")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")
    def removed: Rep[Option[Instant]] = column[Option[Instant]]("REMOVED")

    def * : ProvenShape[Account] =
      (id, externalId, name, description, created, updated, removed) <> ((Account.apply _).tupled, Account.unapply)
  }

  private val store = TableQuery[SlickAccountStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def create(account: Account): Future[Done] =
    database.run(store.insertOrUpdate(account).map(_ => Done))

  override def update(account: Account): Future[Done] =
    database.run(store.insertOrUpdate(account).map(_ => Done))

  override def delete(account: Account.Id): Future[Boolean] =
    database.run(store.filter(_.id === account).map(_.removed).update(value = Some(Instant.now())).map(_ == 1))

  override def get(account: Account.Id): Future[Option[Account]] =
    database.run(store.filter(_.id === account).map(_.value).result.headOption)

  override def available(): Future[Seq[Account]] =
    database.run(store.filter(_.removed.isEmpty).result)

  override def all(): Future[Seq[Account]] =
    database.run(store.result)
}
