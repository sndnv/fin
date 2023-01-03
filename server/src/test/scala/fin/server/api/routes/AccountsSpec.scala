package fin.server.api.routes

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec
import fin.server.api.requests.{CreateAccount, UpdateAccount}
import fin.server.model.Account
import fin.server.persistence.ServerPersistence
import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.persistence.forecasts.ForecastStore
import fin.server.persistence.mocks.{MockAccountStore, MockCategoryMappingStore, MockForecastStore, MockTransactionStore}
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.CurrentUser
import org.slf4j.{Logger, LoggerFactory}

import java.time.Instant
import scala.concurrent.Future

class AccountsSpec extends UnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  "Accounts routes" should "respond with all accounts" in {
    val fixtures = new TestFixtures {}
    Future.sequence(accounts.map(fixtures.accountStore.create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Account]] should contain theSameElementsAs accounts.take(2)
    }
  }

  they should "respond with all accounts, including removed ones" in {
    val fixtures = new TestFixtures {}
    Future.sequence(accounts.map(fixtures.accountStore.create)).await

    Get("/?include_removed=true") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Account]] should contain theSameElementsAs accounts
    }
  }

  they should "create new accounts" in {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.accountStore
        .get(account = 0)
        .map { account => account.isDefined should be(true) }
    }
  }

  they should "respond with existing accounts" in {
    val fixtures = new TestFixtures {}

    fixtures.accountStore.create(accounts.head).await

    Get(s"/${accounts.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Account] should be(accounts.head)
    }
  }

  they should "fail if an account is missing" in {
    val fixtures = new TestFixtures {}
    Get("/123") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing accounts" in {
    val fixtures = new TestFixtures {}
    fixtures.accountStore.create(accounts.head).await

    Put(s"/${accounts.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.accountStore
        .get(accounts.head.id)
        .map(_.map(_.name) should be(Some("other-name")))
    }
  }

  they should "fail to update if an account is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${accounts.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing accounts" in {
    val fixtures = new TestFixtures {}
    fixtures.accountStore.create(accounts.head).await

    Delete(s"/${accounts.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.accountStore
        .get(accounts.head.id)
        .map {
          case Some(account) => account.removed should not be empty
          case None          => fail("Expected an account but none was found")
        }
    }
  }

  they should "not delete missing accounts" in {
    val fixtures = new TestFixtures {}

    fixtures.accountStore.get(accounts.head.id).await should be(None)

    Delete(s"/${accounts.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.accountStore
        .get(accounts.head.id)
        .map { account => account should be(None) }
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

    lazy val routes: Route = new Accounts().routes
  }

  private implicit val user: CurrentUser = CurrentUser(subject = "test-subject")

  private val accounts = Seq(
    Account(
      id = 1,
      externalId = "test-id-1",
      name = "test-name-1",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    Account(
      id = 2,
      externalId = "test-id-2",
      name = "test-name-2",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    Account(
      id = 3,
      externalId = "test-id-3",
      name = "test-name-3",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = Some(Instant.now())
    )
  )

  private val createRequest = CreateAccount(
    externalId = "test-id",
    name = "test-name",
    description = "test-description"
  )

  private val updateRequest = UpdateAccount(
    externalId = "other-id",
    name = "other-name",
    description = "other-description"
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateAccount): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateAccount): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
