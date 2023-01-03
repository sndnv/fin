package fin.server.api.routes

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec
import fin.server.api.requests.{CreateForecast, UpdateForecast}
import fin.server.model.{Forecast, Period, Transaction}
import fin.server.persistence.ServerPersistence
import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.persistence.forecasts.ForecastStore
import fin.server.persistence.mocks.{MockAccountStore, MockCategoryMappingStore, MockForecastStore, MockTransactionStore}
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.CurrentUser
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDate}
import scala.concurrent.Future

class ForecastsSpec extends UnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
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
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastStore
        .get(forecast = fixtures.forecastStore.all(forPeriod = Period.current).await.head.id)
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
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.forecastStore
        .get(forecasts.head.id)
        .map(_.flatMap(_.notes) should be(Some("other-notes")))
    }
  }

  they should "fail to update if a forecast is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${forecasts.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
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

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)

  private trait TestFixtures {
    lazy val accountStore: AccountStore = MockAccountStore()
    lazy val transactionStore: TransactionStore = MockTransactionStore()
    lazy val forecastStore: ForecastStore = MockForecastStore()
    lazy val categoryMappingStore: CategoryMappingStore = MockCategoryMappingStore()

    lazy implicit val context: RoutesContext = RoutesContext.collect(
      new ServerPersistence {
        override val accounts: AccountStore = accountStore
        override val transactions: TransactionStore = transactionStore
        override val forecasts: ForecastStore = forecastStore
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

  private val createRequest = CreateForecast(
    `type` = Transaction.Type.Debit,
    account = 1,
    amount = 123.4,
    currency = "EUR",
    date = Some(LocalDate.now()),
    category = "test-category",
    notes = Some("test-notes"),
    disregardAfter = 1
  )

  private val updateRequest = UpdateForecast(
    `type` = Transaction.Type.Credit,
    account = 3,
    amount = 456.7,
    currency = "EUR",
    date = Some(LocalDate.now()),
    category = "test-category",
    notes = Some("other-notes"),
    disregardAfter = 1
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateForecast): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateForecast): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
