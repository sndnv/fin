package fin.server.api.routes

import fin.server.UnitSpec
import fin.server.api.requests.{CreateAccount, UpdateAccount}
import fin.server.model.Account
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
import org.apache.pekko.http.scaladsl.model.{ContentTypes, Multipart, RequestEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import java.time.Instant
import scala.concurrent.Future

class AccountsSpec extends UnitSpec with ScalatestRouteTest {
  import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
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
        .get(account = 1)
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

  they should "support importing accounts" in {
    val fixtures = new TestFixtures {}

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/json/accounts.json")
    )

    Post("/import", content) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.accountStore.all().map(_.toList.sortBy(_.externalId)).map {
        case first :: second :: third :: Nil =>
          first.externalId should be("test-external-id-1")
          first.name should be("test-name-1")
          first.description should be("test-description-1")

          second.externalId should be("test-external-id-2")
          second.name should be("test-name-2")
          second.description should be("test-description-2")

          third.externalId should be("test-external-id-3")
          third.name should be("test-name-3")
          third.description should be("test-description-3")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
    }
  }

  they should "fail to import already existing accounts" in {
    val fixtures = new TestFixtures {}

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/json/accounts.json")
    )

    Post("/import", content) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      fixtures.accountStore.all().await.length should be(3)
    }

    Post("/import", content) ~> fixtures.routes ~> check {
      status should be(StatusCodes.Conflict)
      fixtures.accountStore.all().await.length should be(3)
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
