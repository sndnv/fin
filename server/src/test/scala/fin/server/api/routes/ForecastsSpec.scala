package fin.server.api.routes

import fin.server.UnitSpec
import fin.server.api.requests.{CreateForecast, CreateForecastBreakdownEntry, UpdateForecast, UpdateForecastBreakdownEntry}
import fin.server.model.{Forecast, ForecastBreakdownEntry, Transaction}
import fin.server.persistence.ServerPersistence
import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.persistence.forecasts.{ForecastBreakdownEntryStore, ForecastStore}
import fin.server.persistence.mocks.{
  MockAccountStore,
  MockCategoryMappingStore,
  MockForecastBreakdownEntryStore,
  MockForecastStore,
  MockTransactionStore
}
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.CurrentUser
import org.apache.pekko.http.scaladsl.marshalling.Marshal
import org.apache.pekko.http.scaladsl.model.{RequestEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDate}
import scala.concurrent.Future

class ForecastsSpec extends UnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  "Forecasts routes" should "respond with all forecasts (current period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecasts.map(fixtures.forecastStore.create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Forecast]] should contain theSameElementsAs forecasts.take(2)
    }
  }

  they should "respond with all forecasts, include removed ones (current period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecasts.map(fixtures.forecastStore.create)).await

    Get("/?include_removed=true") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Forecast]] should contain theSameElementsAs forecasts.take(3)
    }
  }

  they should "respond with all forecasts (previous period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecasts.map(fixtures.forecastStore.create)).await

    Get("/?period=2020-03") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Forecast]] should contain theSameElementsAs forecasts.takeRight(1)
    }
  }

  they should "respond with all forecasts, disregarding some based on a provided day" in {
    val fixtures = new TestFixtures {}
    val updatedForecasts = forecasts.map(_.copy(date = None))
    Future.sequence(updatedForecasts.map(fixtures.forecastStore.create)).await

    Get("/?include_removed=true&disregard_after=10") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Forecast]] should contain theSameElementsAs updatedForecasts.drop(1)
    }
  }

  they should "create new forecasts" in {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createForecastRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastStore
        .get(forecast = 1)
        .map { forecast => forecast.isDefined should be(true) }
    }
  }

  they should "respond with existing forecasts" in {
    val fixtures = new TestFixtures {}

    fixtures.forecastStore.create(forecasts.head).await

    Get(s"/${forecasts.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Forecast] should be(forecasts.head)
    }
  }

  they should "fail if a forecast is missing" in {
    val fixtures = new TestFixtures {}
    Get("/1") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing forecasts" in {
    val fixtures = new TestFixtures {}
    fixtures.forecastStore.create(forecasts.head).await

    Put(s"/${forecasts.head.id}")
      .withEntity(updateForecastRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastStore
        .get(forecasts.head.id)
        .map(_.flatMap(_.notes) should be(Some("other-notes")))
    }
  }

  they should "fail to update if a forecast is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${forecasts.head.id}")
      .withEntity(updateForecastRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing forecasts" in {
    val fixtures = new TestFixtures {}
    fixtures.forecastStore.create(forecasts.head).await

    Delete(s"/${forecasts.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastStore
        .get(forecasts.head.id)
        .map {
          case Some(forecast) => forecast.removed should not be empty
          case None           => fail("Expected a forecast but none was found")
        }
    }
  }

  they should "not delete missing forecasts" in {
    val fixtures = new TestFixtures {}

    fixtures.forecastStore.get(forecasts.head.id).await should be(None)

    Delete(s"/${forecasts.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastStore
        .get(forecasts.head.id)
        .map { forecast => forecast should be(None) }
    }
  }

  they should "retrieve all forecast categories" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecasts.map(fixtures.forecastStore.create)).await

    Get("/categories") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[String]].sorted should be(Seq("test-category-1", "test-category-2"))
    }
  }

  they should "respond with all forecast breakdown entries (current period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecastBreakdownEntries.map(fixtures.forecastBreakdownEntryStore.create)).await

    Get("/breakdown") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[ForecastBreakdownEntry]] should contain theSameElementsAs forecastBreakdownEntries.take(2)
    }
  }

  they should "respond with all forecast breakdown entries, include removed ones (current period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecastBreakdownEntries.map(fixtures.forecastBreakdownEntryStore.create)).await

    Get("/breakdown?include_removed=true") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[ForecastBreakdownEntry]] should contain theSameElementsAs forecastBreakdownEntries.take(3)
    }
  }

  they should "respond with all forecast breakdown entries (previous period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecastBreakdownEntries.map(fixtures.forecastBreakdownEntryStore.create)).await

    Get("/breakdown?period=2020-03") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[ForecastBreakdownEntry]] should contain theSameElementsAs forecastBreakdownEntries.takeRight(1)
    }
  }

  they should "create new forecast breakdown entries" in {
    val fixtures = new TestFixtures {}
    Post("/breakdown").withEntity(createBreakdownEntryRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastBreakdownEntryStore
        .get(entry = 1)
        .map { entry => entry.isDefined should be(true) }
    }
  }

  they should "respond with existing forecast breakdown entries" in {
    val fixtures = new TestFixtures {}

    fixtures.forecastBreakdownEntryStore.create(forecastBreakdownEntries.head).await

    Get(s"/breakdown/${forecastBreakdownEntries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[ForecastBreakdownEntry] should be(forecastBreakdownEntries.head)
    }
  }

  they should "fail if a forecast breakdown entry is missing" in {
    val fixtures = new TestFixtures {}
    Get("/breakdown/1") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing forecast breakdown entries" in {
    val fixtures = new TestFixtures {}
    fixtures.forecastBreakdownEntryStore.create(forecastBreakdownEntries.head).await

    Put(s"/breakdown/${forecastBreakdownEntries.head.id}")
      .withEntity(updateBreakdownEntryRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastBreakdownEntryStore
        .get(forecastBreakdownEntries.head.id)
        .map(_.flatMap(_.notes) should be(Some("other-notes")))
    }
  }

  they should "fail to update if a forecast breakdown entry is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/breakdown/${forecastBreakdownEntries.head.id}")
      .withEntity(updateBreakdownEntryRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing forecast breakdown entries" in {
    val fixtures = new TestFixtures {}
    fixtures.forecastBreakdownEntryStore.create(forecastBreakdownEntries.head).await

    Delete(s"/breakdown/${forecastBreakdownEntries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastBreakdownEntryStore
        .get(forecastBreakdownEntries.head.id)
        .map {
          case Some(entry) => entry.removed should not be empty
          case None        => fail("Expected a forecast breakdown entry but none was found")
        }
    }
  }

  they should "not delete missing forecast breakdown entries" in {
    val fixtures = new TestFixtures {}

    fixtures.forecastBreakdownEntryStore.get(forecastBreakdownEntries.head.id).await should be(None)

    Delete(s"/breakdown/${forecastBreakdownEntries.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastBreakdownEntryStore
        .get(forecastBreakdownEntries.head.id)
        .map { forecast => forecast should be(None) }
    }
  }

  they should "retrieve all forecast breakdown entry categories" in {
    val fixtures = new TestFixtures {}
    Future.sequence(forecastBreakdownEntries.map(fixtures.forecastBreakdownEntryStore.create)).await

    Get("/breakdown/categories") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[String]].sorted should be(Seq("test-category-1", "test-category-2"))
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

    lazy val routes: Route = new Forecasts().routes
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
      disregardAfter = 5,
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
      category = "test-category-1",
      notes = Some("test-notes"),
      disregardAfter = 10,
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    Forecast(
      id = 3,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category-1",
      notes = Some("test-notes"),
      disregardAfter = 20,
      created = Instant.now(),
      updated = Instant.now(),
      removed = Some(Instant.now())
    ),
    Forecast(
      id = 4,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = Some(LocalDate.parse("2020-03-01")),
      category = "test-category-2",
      notes = Some("test-notes"),
      disregardAfter = 20,
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
      category = "test-category-1",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    ForecastBreakdownEntry(
      id = 2,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-1",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    ForecastBreakdownEntry(
      id = 3,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-1",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = Some(Instant.now())
    ),
    ForecastBreakdownEntry(
      id = 4,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.parse("2020-03-01"),
      category = "test-category-2",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )
  )

  private val createForecastRequest = CreateForecast(
    `type` = Transaction.Type.Debit,
    account = 1,
    amount = 123.4,
    currency = "EUR",
    date = Some(LocalDate.now()),
    category = "test-category",
    notes = Some("test-notes"),
    disregardAfter = 1
  )

  private val updateForecastRequest = UpdateForecast(
    `type` = Transaction.Type.Credit,
    account = 3,
    amount = 456.7,
    currency = "EUR",
    date = Some(LocalDate.now()),
    category = "test-category",
    notes = Some("other-notes"),
    disregardAfter = 1
  )

  private val createBreakdownEntryRequest = CreateForecastBreakdownEntry(
    `type` = Transaction.Type.Debit,
    account = 1,
    amount = 123.4,
    currency = "EUR",
    date = LocalDate.now(),
    category = "test-category",
    notes = Some("test-notes")
  )

  private val updateBreakdownEntryRequest = UpdateForecastBreakdownEntry(
    `type` = Transaction.Type.Credit,
    account = 3,
    amount = 456.7,
    currency = "EUR",
    date = LocalDate.now(),
    category = "test-category",
    notes = Some("other-notes")
  )

  import scala.language.implicitConversions

  private implicit def createForecastRequestToEntity(request: CreateForecast): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateForecastRequestToEntity(request: UpdateForecast): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def createBreakdownEntryRequestToEntity(request: CreateForecastBreakdownEntry): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateBreakdownEntryRequestToEntity(request: UpdateForecastBreakdownEntry): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
