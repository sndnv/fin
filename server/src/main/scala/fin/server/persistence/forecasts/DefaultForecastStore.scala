package fin.server.persistence.forecasts

import akka.Done
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import fin.server.model.{Forecast, Period, Transaction}
import slick.ast.BaseTypedType
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

import java.time.{Instant, LocalDate}
import scala.concurrent.{ExecutionContext, Future}

class DefaultForecastStore(
  tableName: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#DatabaseDef
)(implicit val system: ActorSystem[Nothing])
    extends ForecastStore {
  import profile.api._

  // this EC is only used for mapping ops after results are retrieved from the underlying DB;
  // Slick uses its own dispatcher(s) to handle blocking IO
  // - https://scala-slick.org/doc/3.3.1/database.html#database-thread-pool
  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.default())

  private class SlickForecastStore(tag: Tag) extends Table[Forecast](tag, tableName) {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def `type`: Rep[Transaction.Type] = column[Transaction.Type]("TYPE")
    def account: Rep[Int] = column[Int]("ACCOUNT")
    def amount: Rep[BigDecimal] = column[BigDecimal]("AMOUNT")
    def currency: Rep[String] = column[String]("CURRENCY")
    def date: Rep[Option[LocalDate]] = column[Option[LocalDate]]("DATE")
    def category: Rep[String] = column[String]("CATEGORY")
    def notes: Rep[Option[String]] = column[Option[String]]("NOTES")
    def disregardAfter: Rep[Int] = column[Int]("DISREGARD_AFTER")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")
    def removed: Rep[Option[Instant]] = column[Option[Instant]]("REMOVED")

    private def cols = (id, `type`, account, amount, currency, date, category, notes, disregardAfter, created, updated, removed)

    def * : ProvenShape[Forecast] = cols <> ((Forecast.apply _).tupled, Forecast.unapply)
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

  private val store = TableQuery[SlickForecastStore]

  private val extractYear = SimpleFunction.unary[LocalDate, Int]("year")
  private val extractMonth = SimpleFunction.unary[LocalDate, Int]("month")

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def create(forecast: Forecast): Future[Done] =
    database.run(store.insertOrUpdate(forecast).map(_ => Done))

  override def update(forecast: Forecast): Future[Done] =
    database.run(store.insertOrUpdate(forecast).map(_ => Done))

  override def delete(forecast: Forecast.Id): Future[Boolean] =
    database.run(store.filter(_.id === forecast).map(_.removed).update(value = Some(Instant.now)).map(_ == 1))

  override def get(forecast: Forecast.Id): Future[Option[Forecast]] =
    database.run(store.filter(_.id === forecast).map(_.value).result.headOption)

  override def available(forPeriod: Period): Future[Seq[Forecast]] =
    database.run(
      store.filter { e =>
        val dateMatchesOrIsEmpty = e.date
          .map(date => extractYear(date) === forPeriod.year && extractMonth(date) === forPeriod.month.getValue)
          .getOrElse(true)

        e.removed.isEmpty && dateMatchesOrIsEmpty
      }.result
    )

  override def all(forPeriod: Period): Future[Seq[Forecast]] =
    database.run(
      store.filter { e =>
        e.date
          .map(date => extractYear(date) === forPeriod.year && extractMonth(date) === forPeriod.month.getValue)
          .getOrElse(true)
      }.result
    )

  override def categories(): Future[Seq[String]] =
    database.run(
      store
        .filter(_.removed.isEmpty)
        .distinctOn(_.category)
        .map(_.category)
        .result
    )
}
