package fin.server.api.routes

import fin.server.UnitSpec
import fin.server.api.responses.{TransactionBreakdown, TransactionSummary}
import fin.server.model.{Forecast, ForecastBreakdownEntry, Transaction}
import fin.server.persistence.ServerPersistence
import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.persistence.forecasts.{ForecastBreakdownEntryStore, ForecastStore}
import fin.server.persistence.mocks._
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.CurrentUser
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.concurrent.Future

class ReportsSpec extends UnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  "Reports routes" should "retrieve all transaction and forecast categories" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecasts.map(fixtures.forecastStore.create)).await
    Future.sequence(forecastBreakdownEntries.map(fixtures.forecastBreakdownEntryStore.create)).await
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

    Get("/categories") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[String]].sorted should be(
        Seq(
          "test-category-1",
          "test-category-2",
          "test-category-3",
          "test-category-4"
        )
      )
    }
  }

  they should "provide transaction summaries (current period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

    Get("/summary") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionSummary] should be(
        TransactionSummary(
          currencies = Map(
            "USD" -> TransactionSummary.ForCurrency(income = 42.0, expenses = 0.0),
            "EUR" -> TransactionSummary.ForCurrency(income = 123.4, expenses = 246.8)
          )
        )
      )
    }
  }

  they should "provide transaction summaries (previous period)" in {
    val now = Instant.now()

    val transaction1 = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 100,
      currency = "EUR",
      date = LocalDate.parse("2020-03-01"),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    val transaction2 = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id-2",
      `type` = Transaction.Type.Credit,
      from = 1,
      to = Some(2),
      amount = 50,
      currency = "EUR",
      date = LocalDate.parse("2020-04-02"),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    val transaction3 = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id-3",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 200,
      currency = "EUR",
      date = LocalDate.parse("2020-06-03"),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    val fixtures = new TestFixtures {}
    fixtures.transactionStore.create(transaction1).await
    fixtures.transactionStore.create(transaction2).await
    fixtures.transactionStore.create(transaction3).await

    val expectedJan = TransactionSummary(currencies = Map.empty)

    val expectedFeb = TransactionSummary(currencies = Map.empty)

    val expectedMar = TransactionSummary(
      currencies = Map("EUR" -> TransactionSummary.ForCurrency(income = 0.0, expenses = 100.0))
    )

    val expectedApr = TransactionSummary(
      currencies = Map("EUR" -> TransactionSummary.ForCurrency(income = 50.0, expenses = 100.0))
    )

    val expectedMay = TransactionSummary(
      currencies = Map("EUR" -> TransactionSummary.ForCurrency(income = 50.0, expenses = 100.0))
    )

    val expectedJun = TransactionSummary(
      currencies = Map("EUR" -> TransactionSummary.ForCurrency(income = 50.0, expenses = 300.0))
    )

    val expectedJul = TransactionSummary(
      currencies = Map("EUR" -> TransactionSummary.ForCurrency(income = 50.0, expenses = 300.0))
    )

    Get("/summary?period=2020-01") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionSummary] should be(expectedJan)
    }

    Get("/summary?period=2020-02") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionSummary] should be(expectedFeb)
    }

    Get("/summary?period=2020-03") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionSummary] should be(expectedMar)
    }

    Get("/summary?period=2020-04") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionSummary] should be(expectedApr)
    }

    Get("/summary?period=2020-05") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionSummary] should be(expectedMay)
    }

    Get("/summary?period=2020-06") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionSummary] should be(expectedJun)
    }

    Get("/summary?period=2020-07") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionSummary] should be(expectedJul)
    }
  }

  they should "provide transaction breakdowns" in {
    val fixtures = new TestFixtures {}
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

    val year = LocalDate.now().getYear

    Get(s"/breakdown?type=by-year&account=1&start=$year-01-01&end=$year-12-31") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[TransactionBreakdown] should be(
        TransactionBreakdown(
          currencies = Map(
            "EUR" -> TransactionBreakdown.ForCurrency(
              periods = Map(
                year.toString -> TransactionBreakdown.ForPeriod(
                  income = TransactionBreakdown.Income(value = 123.4, transactions = 1),
                  expenses = TransactionBreakdown.Expenses(value = 246.8, transactions = 2),
                  categories = Map(
                    "test-category-1" -> TransactionBreakdown.ForCategory(
                      income = TransactionBreakdown.Income(value = 0, transactions = 0),
                      expenses = TransactionBreakdown.Expenses(value = 123.4, transactions = 1)
                    ),
                    "test-category-2" -> TransactionBreakdown.ForCategory(
                      income = TransactionBreakdown.Income(value = 123.4, transactions = 1),
                      expenses = TransactionBreakdown.Expenses(value = 123.4, transactions = 1)
                    )
                  )
                )
              )
            )
          )
        )
      )
    }
  }
  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private trait TestFixtures {
    lazy val accountStore: AccountStore = MockAccountStore()
    lazy val transactionStore: TransactionStore = MockTransactionStore()
    lazy val forecastStore: ForecastStore = MockForecastStore()
    lazy val forecastBreakdownEntryStore: ForecastBreakdownEntryStore = MockForecastBreakdownEntryStore()
    lazy val categoryMappingStore: CategoryMappingStore = MockCategoryMappingStore()

    lazy implicit val context: RoutesContext = RoutesContext.collect(
      new ServerPersistence {
        override val accounts: AccountStore = accountStore
        override val transactions: TransactionStore = transactionStore
        override val forecasts: ForecastStore = forecastStore
        override val forecastBreakdownEntries: ForecastBreakdownEntryStore = forecastBreakdownEntryStore
        override val categoryMappings: CategoryMappingStore = categoryMappingStore
      }
    )

    lazy val routes: Route = new Reports().routes
  }

  private implicit val user: CurrentUser = CurrentUser(subject = "test-subject")

  private val forecasts = Seq(
    Forecast(
      id = 1,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category-1",
      notes = Some("test-notes"),
      disregardAfter = 1,
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    Forecast(
      id = 2,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category-3",
      notes = Some("test-notes"),
      disregardAfter = 1,
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )
  )

  private val forecastBreakdownEntries = Seq(
    ForecastBreakdownEntry(
      id = 1,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-4",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )
  )

  private val transactions = Seq(
    Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-1",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-2",
      `type` = Transaction.Type.Credit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-2",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-3",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-2",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-4",
      `type` = Transaction.Type.Credit,
      from = 1,
      to = Some(2),
      amount = 42.0,
      currency = "USD",
      date = LocalDate.parse("2020-03-01"),
      category = "test-category-2",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )
  )
}
