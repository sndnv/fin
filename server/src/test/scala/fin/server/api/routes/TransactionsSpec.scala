package fin.server.api.routes

import java.io.File
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ContentTypes, Multipart, RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec
import fin.server.api.requests.{CreateTransaction, UpdateTransaction}
import fin.server.model.{Account, CategoryMapping, Period, Transaction}
import fin.server.persistence.ServerPersistence
import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.persistence.forecasts.ForecastStore
import fin.server.persistence.mocks.{MockAccountStore, MockCategoryMappingStore, MockForecastStore, MockTransactionStore}
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.CurrentUser
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.concurrent.Future

class TransactionsSpec extends UnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  "Transactions routes" should "support parsing import types" in {
    Transactions.ImportType("camt053") should be(Transactions.ImportType.Camt053)
    an[IllegalArgumentException] should be thrownBy Transactions.ImportType("other")
  }

  they should "respond with all transactions (current period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Transaction]] should contain theSameElementsAs transactions.take(2)
    }
  }

  they should "respond with all transactions, including removed ones (current period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

    Get("/?include_removed=true") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Transaction]] should contain theSameElementsAs transactions.take(3)
    }
  }

  they should "respond with all transactions (previous period)" in {
    val fixtures = new TestFixtures {}
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

    Get("/?period=2020-03") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[Transaction]] should contain theSameElementsAs transactions.takeRight(1)
    }
  }

  they should "create new transactions" in {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.transactionStore
        .get(transaction = fixtures.transactionStore.all(forPeriod = Period.current).await.head.id)
        .map { transaction => transaction.isDefined should be(true) }
    }
  }

  they should "respond with existing transactions" in {
    val fixtures = new TestFixtures {}

    fixtures.transactionStore.create(transactions.head).await

    Get(s"/${transactions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Transaction] should be(transactions.head)
    }
  }

  they should "fail if a transaction is missing" in {
    val fixtures = new TestFixtures {}
    Get(s"/${UUID.randomUUID()}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing transactions" in {
    val fixtures = new TestFixtures {}
    fixtures.transactionStore.create(transactions.head).await

    Put(s"/${transactions.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.transactionStore
        .get(transactions.head.id)
        .map(_.map(_.category) should be(Some("other-category")))
    }
  }

  they should "fail to update if a transaction is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${transactions.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing transactions" in {
    val fixtures = new TestFixtures {}
    fixtures.transactionStore.create(transactions.head).await

    Delete(s"/${transactions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.transactionStore
        .get(transactions.head.id)
        .map {
          case Some(transaction) => transaction.removed should not be empty
          case None              => fail("Expected a transaction but none was found")
        }
    }
  }

  they should "not delete missing transactions" in {
    val fixtures = new TestFixtures {}

    fixtures.transactionStore.get(transactions.head.id).await should be(None)

    Delete(s"/${transactions.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.transactionStore
        .get(transactions.head.id)
        .map { transaction => transaction should be(None) }
    }
  }

  they should "import transactions" in {
    val fixtures = new TestFixtures {}

    val forAccount = Account(
      id = 2,
      externalId = "2",
      name = "test-name",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    val targetAccount = Account(
      id = 3,
      externalId = "XYZ123",
      name = "test-name",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    fixtures.accountStore.create(forAccount).await
    fixtures.accountStore.create(targetAccount).await

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/camt/sample_gs_camt.053.zip")
    )

    Post("/import?import_type=camt053&for_account=2&upload_type=archive", content) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val currentPeriodTransactions = fixtures.transactionStore.all(forPeriod = Period.current).await
      val oldTransactions = fixtures.transactionStore.all(forPeriod = Period("2021-08")).await
      val transactionsForTargetAccount = (currentPeriodTransactions ++ oldTransactions).filter(_.to.exists(_ == targetAccount.id))

      currentPeriodTransactions.length should be(1)
      oldTransactions.length should be(9)
      transactionsForTargetAccount.length should be(1)

      currentPeriodTransactions.map(_.category).distinct should be(Seq("imported"))
      oldTransactions.map(_.category).distinct should be(Seq("imported"))
      transactionsForTargetAccount.map(_.category).distinct should be(Seq("imported"))
    }
  }

  they should "import transactions and apply category mappings" in {
    val fixtures = new TestFixtures {}

    val forAccount = Account(
      id = 2,
      externalId = "2",
      name = "test-name",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    val targetAccount = Account(
      id = 3,
      externalId = "XYZ123",
      name = "test-name",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    val mapping1 = CategoryMapping(
      id = 1,
      condition = CategoryMapping.Condition.Equals,
      matcher = "abcdef",
      category = "mapped-category-1",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    val mapping2 = CategoryMapping(
      id = 2,
      condition = CategoryMapping.Condition.Equals,
      matcher = "xyz123",
      category = "mapped-category-2",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    val mapping3 = CategoryMapping(
      id = 3,
      condition = CategoryMapping.Condition.Equals,
      matcher = "xyz123",
      category = "mapped-category-3",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    fixtures.accountStore.create(forAccount).await
    fixtures.accountStore.create(targetAccount).await
    fixtures.categoryMappingStore.create(mapping1).await
    fixtures.categoryMappingStore.create(mapping2).await
    fixtures.categoryMappingStore.create(mapping3).await

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/camt/sample_gs_camt.053.zip")
    )

    Post("/import?import_type=camt053&for_account=2&upload_type=archive", content) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val currentPeriodTransactions = fixtures.transactionStore.all(forPeriod = Period.current).await
      val oldTransactions = fixtures.transactionStore.all(forPeriod = Period("2021-08")).await
      val transactionsForTargetAccount = (currentPeriodTransactions ++ oldTransactions).filter(_.to.exists(_ == targetAccount.id))

      currentPeriodTransactions.length should be(1)
      oldTransactions.length should be(9)
      transactionsForTargetAccount.length should be(1)

      currentPeriodTransactions.map(_.category).distinct.sorted should be(Seq("imported"))
      oldTransactions.map(_.category).distinct.sorted should be(Seq("imported", "mapped-category-1", "mapped-category-3"))
      transactionsForTargetAccount.map(_.category).distinct.sorted should be(Seq("mapped-category-3"))
    }
  }

  they should "fail to import transactions for missing accounts" in {
    val fixtures = new TestFixtures {}

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/camt/sample_gs_camt.053.zip")
    )

    Post("/import?import_type=camt053&for_account=2&upload_type=archive", content) ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
      fixtures.transactionStore.all(forPeriod = Period.current).await should be(empty)
    }
  }

  they should "search for transactions" in {
    val fixtures = new TestFixtures {}
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

    Get("/search?query=notes") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      entityAs[Seq[Transaction]].sortBy(_.id) should be(transactions.sortBy(_.id))
    }
  }

  they should "reject empty search queries" in {
    val fixtures = new TestFixtures {}
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

    Get("/search?query=%20%20") ~> fixtures.routes ~> check {
      status should be(StatusCodes.BadRequest)
    }
  }

  they should "retrieve all transaction categories" in {
    val fixtures = new TestFixtures {}
    Future.sequence(transactions.map(fixtures.transactionStore.create)).await

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

    lazy val routes: Route = new Transactions().routes
  }

  private implicit val user: CurrentUser = CurrentUser(subject = "test-subject")

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
      removed = Some(Instant.now())
    ),
    Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-4",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.parse("2020-03-01"),
      category = "test-category-1",
      notes = Some("some-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )
  )

  private val createRequest = CreateTransaction(
    `type` = Transaction.Type.Debit,
    from = 1,
    to = Some(2),
    amount = 123.4,
    currency = "EUR",
    date = LocalDate.now(),
    category = "test-category",
    notes = Some("test-notes")
  )

  private val updateRequest = UpdateTransaction(
    externalId = "other-id",
    `type` = Transaction.Type.Credit,
    from = 3,
    to = Some(4),
    amount = 456.7,
    currency = "EUR",
    date = LocalDate.now(),
    category = "other-category",
    notes = Some("other-notes")
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateTransaction): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateTransaction): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
