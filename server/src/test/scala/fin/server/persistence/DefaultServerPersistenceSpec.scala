package fin.server.persistence

import com.typesafe.config.Config
import fin.server.UnitSpec
import fin.server.model._
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDate}

class DefaultServerPersistenceSpec extends UnitSpec {
  "DefaultServerPersistence" should "support initializing the data stores based on config (init enabled)" in {
    val persistence = new DefaultServerPersistence(
      persistenceConfig = config.getConfig("persistence.with-init")
    )

    val expectedAccount = Account(
      id = 0,
      externalId = "test-id",
      name = "test-name",
      description = "test-description",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    val expectedTransaction = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id",
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

    val expectedForecast = Forecast(
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

    val expectedCategoryMapping = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher",
      category = "test-category",
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    for {
      _ <- persistence.init()
      _ <- persistence.accounts.create(expectedAccount)
      accounts <- persistence.accounts.all()
      _ <- persistence.transactions.create(expectedTransaction)
      transactions <- persistence.transactions.all(forPeriod = Period.current)
      _ <- persistence.forecasts.create(expectedForecast)
      forecasts <- persistence.forecasts.all(forPeriod = Period.current)
      _ <- persistence.categoryMappings.create(expectedCategoryMapping)
      categoryMappings <- persistence.categoryMappings.all()
      _ <- persistence.drop()
    } yield {
      accounts should be(Seq(expectedAccount.copy(id = 1)))
      transactions should be(Seq(expectedTransaction))
      forecasts should be(Seq(expectedForecast.copy(id = 1)))
      categoryMappings should be(Seq(expectedCategoryMapping.copy(id = 1)))
    }
  }

  it should "support initializing the data stores based on config (init disabled)" in {
    val persistence = new DefaultServerPersistence(
      persistenceConfig = config.getConfig("persistence.without-init")
    )

    for {
      _ <- persistence.init()
      accountsFailure <- persistence.accounts.all().failed
      transactionsFailure <- persistence.transactions.all(forPeriod = Period.current).failed
      forecastsFailure <- persistence.forecasts.all(forPeriod = Period.current).failed
      categoryMappingsFailure <- persistence.categoryMappings.all().failed
    } yield {
      accountsFailure.getMessage should startWith("Table \"ACCOUNTS\" not found")
      transactionsFailure.getMessage should startWith("Table \"TRANSACTIONS\" not found")
      forecastsFailure.getMessage should startWith("Table \"FORECASTS\" not found")
      categoryMappingsFailure.getMessage should startWith("Table \"CATEGORY_MAPPINGS\" not found")
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "DefaultServerPersistenceSpec"
  )

  private val config: Config = system.settings.config.getConfig("fin.test.server")

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
