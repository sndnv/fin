package fin.server.api.routes

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{ContentTypes, Multipart, RequestEntity, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec
import fin.server.api.requests.{ApplyCategoryMappings, CreateCategoryMapping, UpdateCategoryMapping}
import fin.server.imports.Defaults
import fin.server.model.{CategoryMapping, Period, Transaction}
import fin.server.persistence.ServerPersistence
import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.persistence.forecasts.ForecastStore
import fin.server.persistence.mocks.{MockAccountStore, MockCategoryMappingStore, MockForecastStore, MockTransactionStore}
import fin.server.persistence.transactions.TransactionStore
import fin.server.security.CurrentUser
import org.slf4j.{Logger, LoggerFactory}

import java.io.File
import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.concurrent.Future

class CategoriesSpec extends UnitSpec with ScalatestRouteTest {
  import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
  import fin.server.api.Formats._

  "Categories routes" should "respond with all category mappings" in {
    val fixtures = new TestFixtures {}
    Future.sequence(mappings.map(fixtures.categoryMappingStore.create)).await

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[CategoryMapping]] should contain theSameElementsAs mappings.take(2)
    }
  }

  they should "respond with all category mappings, including removed ones" in {
    val fixtures = new TestFixtures {}
    Future.sequence(mappings.map(fixtures.categoryMappingStore.create)).await

    Get("/?include_removed=true") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[Seq[CategoryMapping]] should contain theSameElementsAs mappings
    }
  }

  they should "create new category mappings" in {
    val fixtures = new TestFixtures {}
    Post("/").withEntity(createRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.categoryMappingStore
        .get(categoryMapping = 1)
        .map { mapping => mapping.isDefined should be(true) }
    }
  }

  they should "respond with existing category mappings" in {
    val fixtures = new TestFixtures {}

    fixtures.categoryMappingStore.create(mappings.head).await

    Get(s"/${mappings.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      responseAs[CategoryMapping] should be(mappings.head)
    }
  }

  they should "fail if a category mapping is missing" in {
    val fixtures = new TestFixtures {}
    Get("/123") ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "update existing category mappings" in {
    val fixtures = new TestFixtures {}
    fixtures.categoryMappingStore.create(mappings.head).await

    Put(s"/${mappings.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.categoryMappingStore
        .get(mappings.head.id)
        .map(_.map(_.matcher) should be(Some("other-matcher")))
    }
  }

  they should "fail to update if a category mapping is missing" in {
    val fixtures = new TestFixtures {}
    Put(s"/${mappings.head.id}")
      .withEntity(updateRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.NotFound)
    }
  }

  they should "delete existing category mappings" in {
    val fixtures = new TestFixtures {}
    fixtures.categoryMappingStore.create(mappings.head).await

    Delete(s"/${mappings.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.categoryMappingStore
        .get(mappings.head.id)
        .map {
          case Some(mapping) => mapping.removed should not be empty
          case None          => fail("Expected a category mapping but none was found")
        }
    }
  }

  they should "not delete missing category mappings" in {
    val fixtures = new TestFixtures {}

    fixtures.categoryMappingStore.get(mappings.head.id).await should be(None)

    Delete(s"/${mappings.head.id}") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.categoryMappingStore
        .get(mappings.head.id)
        .map { mapping => mapping should be(None) }
    }
  }

  they should "apply category mappings to transactions (without overriding existing categories)" in {
    val fixtures = new TestFixtures {}

    val now = Instant.now()

    val transaction1 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = Defaults.Category,
      notes = Some("notes-a"),
      created = now,
      updated = now,
      removed = None
    )

    val transaction2 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-2",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = Defaults.Category,
      notes = Some("notes-b"),
      created = now,
      updated = now,
      removed = None
    )

    val transaction3 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-3",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "other-category",
      notes = Some("notes-c"),
      created = now,
      updated = now,
      removed = None
    )

    val mapping1 = CategoryMapping(
      id = 1,
      condition = CategoryMapping.Condition.StartsWith,
      matcher = "notes-",
      category = "test-category-1",
      created = now,
      updated = now,
      removed = None
    )

    val mapping2 = CategoryMapping(
      id = 2,
      condition = CategoryMapping.Condition.StartsWith,
      matcher = "notes-",
      category = "test-category-2",
      created = now,
      updated = now,
      removed = None
    )

    fixtures.transactionStore.create(transaction1).await
    fixtures.transactionStore.create(transaction2).await
    fixtures.transactionStore.create(transaction3).await
    fixtures.categoryMappingStore.create(mapping1).await
    fixtures.categoryMappingStore.create(mapping2).await

    Put("/apply").withEntity(applyRequest) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.transactionStore.available(forPeriod = Period.current).map(_.toList.sortBy(_.externalId)).map {
        case actualTransaction1 :: actualTransaction2 :: actualTransaction3 :: Nil =>
          actualTransaction1 should be(transaction1.copy(category = mapping2.category, updated = actualTransaction1.updated))
          actualTransaction2 should be(transaction2.copy(category = mapping2.category, updated = actualTransaction2.updated))
          actualTransaction3 should be(transaction3)

        case other => fail(s"Unexpected transcations found: [$other]")
      }
    }
  }

  they should "apply category mappings to transactions (overriding existing categories)" in {
    val fixtures = new TestFixtures {}

    val now = Instant.now()

    val transaction1 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = Defaults.Category,
      notes = Some("notes-a"),
      created = now,
      updated = now,
      removed = None
    )

    val transaction2 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-2",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = Defaults.Category,
      notes = Some("notes-b"),
      created = now,
      updated = now,
      removed = None
    )

    val transaction3 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-3",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "other-category",
      notes = Some("notes-c"),
      created = now,
      updated = now,
      removed = None
    )

    val mapping1 = CategoryMapping(
      id = 1,
      condition = CategoryMapping.Condition.StartsWith,
      matcher = "notes-",
      category = "test-category-1",
      created = now,
      updated = now,
      removed = None
    )

    val mapping2 = CategoryMapping(
      id = 2,
      condition = CategoryMapping.Condition.StartsWith,
      matcher = "notes-",
      category = "test-category-2",
      created = now,
      updated = now,
      removed = None
    )

    fixtures.transactionStore.create(transaction1).await
    fixtures.transactionStore.create(transaction2).await
    fixtures.transactionStore.create(transaction3).await
    fixtures.categoryMappingStore.create(mapping1).await
    fixtures.categoryMappingStore.create(mapping2).await

    Put("/apply").withEntity(applyRequest.copy(overrideExisting = true)) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.transactionStore.available(forPeriod = Period.current).map(_.toList.sortBy(_.externalId)).map {
        case actualTransaction1 :: actualTransaction2 :: actualTransaction3 :: Nil =>
          actualTransaction1 should be(transaction1.copy(category = mapping2.category, updated = actualTransaction1.updated))
          actualTransaction2 should be(transaction2.copy(category = mapping2.category, updated = actualTransaction2.updated))
          actualTransaction3 should be(transaction3.copy(category = mapping2.category, updated = actualTransaction3.updated))

        case other => fail(s"Unexpected transcations found: [$other]")
      }
    }
  }

  they should "support importing category mappings" in {
    val fixtures = new TestFixtures {}

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/json/category_mappings.json")
    )

    Post("/import", content) ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      fixtures.categoryMappingStore.all().map(_.toList.sortBy(_.category)).map {
        case first :: second :: third :: Nil =>
          first.condition should be(CategoryMapping.Condition.StartsWith)
          first.matcher should be("test-matcher-1")
          first.category should be("test-category-1")

          second.condition should be(CategoryMapping.Condition.EndsWith)
          second.matcher should be("test-matcher-2")
          second.category should be("test-category-2")

          third.condition should be(CategoryMapping.Condition.Matches)
          third.matcher should be("test-matcher-3")
          third.category should be("test-category-3")

        case other =>
          fail(s"Unexpected result received: [$other]")
      }
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

    lazy val routes: Route = new Categories().routes
  }

  private implicit val user: CurrentUser = CurrentUser(subject = "test-subject")

  private val mappings = Seq(
    CategoryMapping(
      id = 1,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher-1",
      category = "test-category",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    CategoryMapping(
      id = 2,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher-2",
      category = "test-category",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    ),
    CategoryMapping(
      id = 3,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher-3",
      category = "test-category",
      created = Instant.now(),
      updated = Instant.now(),
      removed = Some(Instant.now())
    )
  )

  private val createRequest = CreateCategoryMapping(
    condition = CategoryMapping.Condition.StartsWith,
    matcher = "test-matcher",
    category = "test-category"
  )

  private val updateRequest = UpdateCategoryMapping(
    condition = CategoryMapping.Condition.EndsWith,
    matcher = "other-matcher",
    category = "other-category"
  )

  private val applyRequest = ApplyCategoryMappings(
    forPeriod = Period.current,
    overrideExisting = false
  )

  import scala.language.implicitConversions

  private implicit def createRequestToEntity(request: CreateCategoryMapping): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def updateRequestToEntity(request: UpdateCategoryMapping): RequestEntity =
    Marshal(request).to[RequestEntity].await

  private implicit def applyRequestToEntity(request: ApplyCategoryMappings): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
