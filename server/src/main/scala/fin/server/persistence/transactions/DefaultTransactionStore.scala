package fin.server.persistence.transactions

import fin.server.model.{Account, Period, Transaction}
import fin.server.persistence.Migration
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector}
import slick.ast.BaseTypedType
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DefaultTransactionStore(
  override val tableName: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing])
    extends TransactionStore {
  import profile.api._

  // this EC is only used for mapping ops after results are retrieved from the underlying DB;
  // Slick uses its own dispatcher(s) to handle blocking IO
  // - https://scala-slick.org/doc/3.3.1/database.html#database-thread-pool
  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.default())

  private class SlickTransactionStore(tag: Tag) extends Table[Transaction](tag, tableName) {
    def id: Rep[UUID] = column[UUID]("ID", O.PrimaryKey)
    def externalId: Rep[String] = column[String]("EXTERNAL_ID", O.Unique)
    def `type`: Rep[Transaction.Type] = column[Transaction.Type]("TYPE")
    def from: Rep[Int] = column[Int]("FROM")
    def to: Rep[Option[Int]] = column[Option[Int]]("TO")
    def amount: Rep[BigDecimal] = column[BigDecimal]("AMOUNT")
    def currency: Rep[String] = column[String]("CURRENCY")
    def date: Rep[LocalDate] = column[LocalDate]("DATE")
    def category: Rep[String] = column[String]("CATEGORY")
    def notes: Rep[Option[String]] = column[Option[String]]("NOTES")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")
    def removed: Rep[Option[Instant]] = column[Option[Instant]]("REMOVED")

    private def cols = (id, externalId, `type`, from, to, amount, currency, date, category, notes, created, updated, removed)

    def * : ProvenShape[Transaction] = cols <> ((Transaction.apply _).tupled, Transaction.unapply)
  }

  private implicit val typeColumnType: BaseTypedType[Transaction.Type] = MappedColumnType.base[Transaction.Type, String](
    tmap = {
      case Transaction.Type.Debit  => "debit"
      case Transaction.Type.Credit => "credit"
    },
    tcomap = {
      case "debit"  => Transaction.Type.Debit
      case "credit" => Transaction.Type.Credit
    }
  )

  private val store = TableQuery[SlickTransactionStore]

  override val migrations: Seq[Migration] = Seq.empty

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def create(transaction: Transaction): Future[Done] =
    database.run(store.insertOrUpdate(transaction).map(_ => Done))

  override def load(transactions: Seq[Transaction]): Future[(Int, Int)] = {
    val externalIds = transactions.map(_.externalId)
    val action = for {
      existing <- store.filter(_.externalId.inSet(externalIds)).map(_.externalId).result
      remaining = transactions.filterNot(t => existing.contains(t.externalId))
      successful <- (store ++= remaining).map(_.getOrElse(remaining.length))
    } yield {
      successful -> existing.length
    }

    database.run(action)
  }

  override def update(transaction: Transaction): Future[Done] =
    database.run(store.insertOrUpdate(transaction).map(_ => Done))

  override def delete(transaction: Transaction.Id): Future[Boolean] =
    database.run(store.filter(_.id === transaction).map(_.removed).update(value = Some(Instant.now)).map(_ == 1))

  override def get(transaction: Transaction.Id): Future[Option[Transaction]] =
    database.run(store.filter(_.id === transaction).map(_.value).result.headOption)

  override def available(forPeriod: Period): Future[Seq[Transaction]] = {
    val start = forPeriod.atFirstDayOfMonth
    val end = forPeriod.atLastDayOfMonth
    database.run(
      store
        .filter(e => e.removed.isEmpty && e.date >= start && e.date <= end)
        .result
    )
  }

  override def all(forPeriod: Period): Future[Seq[Transaction]] = {
    val start = forPeriod.atFirstDayOfMonth
    val end = forPeriod.atLastDayOfMonth
    database.run(
      store
        .filter(e => e.date >= start && e.date <= end)
        .result
    )
  }

  override def to(period: Period): Future[Seq[Transaction]] = {
    val maxDate = period.atLastDayOfMonth

    database.run(
      store
        .filter(e => e.removed.isEmpty && e.date <= maxDate)
        .result
    )
  }

  override def search(query: String): Future[Seq[Transaction]] =
    database.run(
      store
        .filter(e => e.externalId.like(s"%$query%") || e.category.like(s"%$query%") || e.notes.like(s"%$query%"))
        .result
    )

  override def categories(): Future[Seq[String]] =
    database.run(
      store
        .filter(_.removed.isEmpty)
        .distinctOn(_.category)
        .map(_.category)
        .result
    )

  override def between(start: LocalDate, end: LocalDate, account: Account.Id): Future[Seq[Transaction]] =
    database.run(
      store
        .filter(e => e.from === account && e.removed.isEmpty && e.date >= start && e.date <= end)
        .result
    )
}
