package fin.server.api

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{Behavior, SpawnProtocol}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.unmarshalling.Unmarshal
import fin.server.UnitSpec
import fin.server.api.requests.CreateAccount
import fin.server.api.responses.MessageResponse
import fin.server.api.routes.Manage
import fin.server.model.{Account, CategoryMapping, Forecast, Transaction}
import fin.server.persistence.ServerPersistence
import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.persistence.forecasts.ForecastStore
import fin.server.persistence.mocks.{MockAccountStore, MockCategoryMappingStore, MockForecastStore, MockTransactionStore}
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.authenticators.UserAuthenticator
import fin.server.security.mocks.MockUserAuthenticator
import fin.server.service.ServiceMode
import fin.server.telemetry.TelemetryContext
import fin.server.telemetry.mocks.MockTelemetryContext

import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.Future

class ApiEndpointSpec extends UnitSpec with ScalatestRouteTest {
  "An ApiEndpoint" should "load its config" in {
    val actual = ApiEndpoint.Config(
      config = com.typesafe.config.ConfigFactory
        .load()
        .getConfig("fin.test.server.service.endpoint")
    )

    actual should be(config)
  }

  it should "successfully authenticate users" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    val fixtures = new TestFixtures {}

    val createAccount = CreateAccount(
      externalId = "test-id-1",
      name = "test-name-1",
      description = "test-description"
    )

    Get(s"/accounts", createAccount).addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Account]] should be(empty)
    }
  }

  it should "fail to authenticate users with no credentials" in {
    val fixtures = new TestFixtures {}

    Get("/accounts") ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate users with invalid credentials" in {
    val fixtures = new TestFixtures {}

    Get("/accounts")
      .addCredentials(testCredentials.copy(username = "invalid-username")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "fail to authenticate a user with invalid password" in {
    val fixtures = new TestFixtures {}

    Get("/accounts")
      .addCredentials(testCredentials.copy(password = "invalid-password")) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.Unauthorized)
    }
  }

  it should "provide routes for accounts" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    val fixtures = new TestFixtures {}

    val account = Account(
      id = 1,
      externalId = "test-id-1",
      name = "test-name-1",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    fixtures.accountStore.create(account).await

    Get("/accounts").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Account]] should be(Seq(account))
    }
  }

  it should "provide routes for transactions" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    val fixtures = new TestFixtures {}

    val transaction = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    fixtures.transactionStore.create(transaction).await

    Get("/transactions").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Transaction]] should be(Seq(transaction))
    }
  }

  it should "provide routes for forecasts" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    val fixtures = new TestFixtures {}

    val forecast = Forecast(
      id = 0,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = None,
      category = "test-category",
      notes = Some("test-notes"),
      disregardAfter = 1,
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    fixtures.forecastStore.create(forecast).await

    Get("/forecasts").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Forecast]] should be(Seq(forecast))
    }
  }

  it should "provide routes for category mappings" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    val fixtures = new TestFixtures {}

    val mapping = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher",
      category = "test-category",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    fixtures.categoryMappingStore.create(mapping).await

    Get("/categories").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[CategoryMapping]] should be(Seq(mapping))
    }
  }

  it should "provide routes for reports" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._

    val fixtures = new TestFixtures {}

    val forecast = Forecast(
      id = 0,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = None,
      category = "test-category",
      notes = Some("test-notes"),
      disregardAfter = 1,
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    fixtures.forecastStore.create(forecast).await

    Get("/reports/categories").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[String]] should be(Seq("test-category"))
    }
  }

  it should "provide routes for management" in {
    val fixtures = new TestFixtures {}

    Get("/manage") ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
      responseAs[String] should include("<!doctype html>")
    }
  }

  it should "redirect root requests to management" in {
    val fixtures = new TestFixtures {}

    Get("/") ~> fixtures.endpoint.endpointRoutes ~> check {
      headers.map(_.toString()) should be(Seq("Location: manage"))
      status should be(StatusCodes.PermanentRedirect)
    }
  }

  it should "provide service routes" in {
    val fixtures = new TestFixtures {}

    Get("/service/health").addCredentials(testCredentials) ~> fixtures.endpoint.endpointRoutes ~> check {
      status should be(StatusCodes.OK)
    }
  }

  it should "handle generic failures reported by routes" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    val endpoint = new ApiEndpoint(
      config = config,
      persistence = new ServerPersistence {
        override val accounts: AccountStore = new MockAccountStore() {
          override def available(): Future[Seq[Account]] = Future.failed(new RuntimeException("Test failure"))
        }
        override val transactions: TransactionStore = MockTransactionStore()
        override val forecasts: ForecastStore = MockForecastStore()
        override val categoryMappings: CategoryMappingStore = MockCategoryMappingStore()
      },
      authenticator = new MockUserAuthenticator(testUser, testPassword)
    )

    val endpointPort = ports.dequeue()
    val _ = endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"http://localhost:$endpointPort/accounts"
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.InternalServerError)
        Unmarshal(response).to[MessageResponse].await.message should startWith(
          "Failed to process request; failure reference is"
        )
      }
  }

  it should "reject requests with invalid entities" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    val endpoint = new ApiEndpoint(
      config = config,
      persistence = new ServerPersistence {
        override val accounts: AccountStore = MockAccountStore()
        override val transactions: TransactionStore = MockTransactionStore()
        override val forecasts: ForecastStore = MockForecastStore()
        override val categoryMappings: CategoryMappingStore = MockCategoryMappingStore()
      },
      authenticator = new MockUserAuthenticator(testUser, testPassword)
    )

    val endpointPort = ports.dequeue()
    val _ = endpoint.start(
      interface = "localhost",
      port = endpointPort,
      context = None
    )

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:$endpointPort/accounts",
          entity = HttpEntity(ContentTypes.`application/json`, "{\"a\":1}")
        ).addCredentials(testCredentials)
      )
      .map { response =>
        response.status should be(StatusCodes.BadRequest)
        Unmarshal(response).to[MessageResponse].await.message should startWith(
          "Provided data is invalid or malformed"
        )
      }
  }

  private implicit val typedSystem: akka.actor.typed.ActorSystem[SpawnProtocol.Command] = akka.actor.typed.ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ApiEndpointSpec"
  )

  private implicit val telemetry: TelemetryContext = MockTelemetryContext()

  private implicit val serviceMode: ServiceMode = ServiceMode.Production

  private trait TestFixtures {
    lazy val accountStore: AccountStore = MockAccountStore()
    lazy val transactionStore: TransactionStore = MockTransactionStore()
    lazy val forecastStore: ForecastStore = MockForecastStore()
    lazy val categoryMappingStore: CategoryMappingStore = MockCategoryMappingStore()

    private lazy val authenticator: UserAuthenticator = new MockUserAuthenticator(testUser, testPassword)

    lazy val endpoint: ApiEndpoint = new ApiEndpoint(
      config = config,
      persistence = new ServerPersistence {
        override val accounts: AccountStore = accountStore
        override val transactions: TransactionStore = transactionStore
        override val forecasts: ForecastStore = forecastStore
        override val categoryMappings: CategoryMappingStore = categoryMappingStore
      },
      authenticator = authenticator
    )
  }

  private val testUser = "test-user"
  private val testPassword = "test-password"

  private val testCredentials = BasicHttpCredentials(username = testUser, password = testPassword)

  private val ports: mutable.Queue[Int] = (13500 to 13600).to(mutable.Queue)

  private val config = ApiEndpoint.Config(
    manage = Manage.Config(
      ui = Manage.Config.Ui(
        authorizationEndpoint = "a",
        tokenEndpoint = "b",
        authentication = Manage.Config.Ui.Authentication(
          clientId = "c",
          redirectUri = "d",
          scope = "e",
          stateSize = 1,
          codeVerifierSize = 2
        ),
        cookies = Manage.Config.Ui.Cookies(
          authenticationToken = "f",
          codeVerifier = "g",
          state = "h",
          secure = true,
          expirationTolerance = 3
        )
      )
    )
  )
}
