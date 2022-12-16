package fin.server.persistence

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import com.typesafe.config.Config
import fin.server.UnitSpec
import fin.server.model.{Account, Period, Transaction}
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDate}

class ServerPersistenceSpec extends UnitSpec {
  "ServerPersistence" should "support initializing the data stores based on config (init enabled)" in {
    val persistence = new ServerPersistence(
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

    for {
      _ <- persistence.init()
      _ <- persistence.accounts.create(expectedAccount)
      accounts <- persistence.accounts.all()
      _ <- persistence.transactions.create(expectedTransaction)
      transactions <- persistence.transactions.all(forPeriod = Period.current)
      _ <- persistence.drop()
    } yield {
      accounts should be(Seq(expectedAccount.copy(id = 1)))
      transactions should be(Seq(expectedTransaction))
    }
  }

  it should "support initializing the data stores based on config (init disabled)" in {
    val persistence = new ServerPersistence(
      persistenceConfig = config.getConfig("persistence.without-init")
    )

    for {
      _ <- persistence.init()
      accountsFailure <- persistence.accounts.all().failed
      transactionsFailure <- persistence.transactions.all(forPeriod = Period.current).failed
    } yield {
      accountsFailure.getMessage should startWith("Table \"ACCOUNTS\" not found")
      transactionsFailure.getMessage should startWith("Table \"TRANSACTIONS\" not found")
    }
  }

  private implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ServerPersistenceSpec"
  )

  private val config: Config = system.settings.config.getConfig("fin.test.server")

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
