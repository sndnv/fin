package fin.server.persistence.forecasts

import fin.server.model.{ForecastBreakdownEntry, Period, Transaction}
import fin.server.persistence.Migration
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.{ActorSystem, DispatcherSelector}
import slick.ast.BaseTypedType
import slick.jdbc.JdbcProfile
import slick.jdbc.meta.MTable
import slick.lifted.ProvenShape

import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}

class DefaultForecastBreakdownEntryStore(
  override val tableName: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#Database
)(implicit val system: ActorSystem[Nothing])
    extends ForecastBreakdownEntryStore {
  import profile.api._

  // this EC is only used for mapping ops after results are retrieved from the underlying DB;
  // Slick uses its own dispatcher(s) to handle blocking IO
  // - https://scala-slick.org/doc/3.3.1/database.html#database-thread-pool
  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.default())

  private class SlickForecastBreakdownEntryStore(tag: Tag) extends Table[ForecastBreakdownEntry](tag, tableName) {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def `type`: Rep[Transaction.Type] = column[Transaction.Type]("TYPE")
    def account: Rep[Int] = column[Int]("ACCOUNT")
    def amount: Rep[BigDecimal] = column[BigDecimal]("AMOUNT")
    def currency: Rep[String] = column[String]("CURRENCY")
    def date: Rep[LocalDate] = column[LocalDate]("DATE")
    def category: Rep[String] = column[String]("CATEGORY")
    def notes: Rep[Option[String]] = column[Option[String]]("NOTES")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")
    def removed: Rep[Option[Instant]] = column[Option[Instant]]("REMOVED")

    private def cols = (id, `type`, account, amount, currency, date, category, notes, created, updated, removed)

    def * : ProvenShape[ForecastBreakdownEntry] =
      cols <> ((ForecastBreakdownEntry.apply _).tupled, ForecastBreakdownEntry.unapply)
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

  private val store = TableQuery[SlickForecastBreakdownEntryStore]

  override val migrations: Seq[Migration] = Seq(
    Migration(
      version = 1,
      needed = MTable.getTables(namePattern = tableName).map(_.isEmpty),
      action = store.schema.create
    )
  )

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def create(entry: ForecastBreakdownEntry): Future[Done] =
    database.run(store.insertOrUpdate(entry).map(_ => Done))

  override def update(entry: ForecastBreakdownEntry): Future[Done] =
    database.run(store.insertOrUpdate(entry).map(_ => Done))

  override def delete(entry: ForecastBreakdownEntry.Id): Future[Boolean] =
    database.run(store.filter(_.id === entry).map(_.removed).update(value = Some(Instant.now)).map(_ == 1))

  override def get(entry: ForecastBreakdownEntry.Id): Future[Option[ForecastBreakdownEntry]] =
    database.run(store.filter(_.id === entry).map(_.value).result.headOption)

  override def available(forPeriod: Period): Future[Seq[ForecastBreakdownEntry]] = {
    val start = forPeriod.atFirstDayOfMonth
    val end = forPeriod.atLastDayOfMonth
    database.run(
      store.filter { e =>
        val dateMatches = e.date >= start && e.date <= end
        e.removed.isEmpty && dateMatches
      }.result
    )
  }

  override def all(forPeriod: Period): Future[Seq[ForecastBreakdownEntry]] = {
    val start = forPeriod.atFirstDayOfMonth
    val end = forPeriod.atLastDayOfMonth
    database.run(
      store.filter { e => e.date >= start && e.date <= end }.result
    )
  }

  override def categories(): Future[Seq[String]] =
    database.run(
      store
        .filter(_.removed.isEmpty)
        .distinctOn(_.category)
        .map(_.category)
        .result
    )
}
