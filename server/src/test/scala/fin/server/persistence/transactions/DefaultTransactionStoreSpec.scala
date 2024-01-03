package fin.server.persistence.transactions

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import fin.server.UnitSpec
import fin.server.model.{Period, Transaction}
import org.scalatest.BeforeAndAfterAll
import slick.jdbc.H2Profile
import java.time.{Instant, LocalDate}
import java.util.UUID

class DefaultTransactionStoreSpec extends UnitSpec with BeforeAndAfterAll {
  "A DefaultTransactionStore" should "store and retrieve transactions" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transaction = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(transaction)
      existing <- store.get(transaction = transaction.id)
      _ <- store.drop()
    } yield {
      existing should be(Some(transaction))
    }
  }

  it should "support batch loading of transactions" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transaction = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    val transactions = Seq(
      transaction,
      transaction.copy(id = UUID.randomUUID(), externalId = "test-id-2"),
      transaction.copy(id = UUID.randomUUID(), externalId = "test-id-3")
    )

    for {
      _ <- store.init()
      (successful, existing) <- store.load(transactions)
      stored <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      successful should be(3)
      existing should be(0)
      stored.length should be(3)
    }
  }

  it should "skip loading transactions that already exist" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transaction = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    val transactions = Seq(
      transaction,
      transaction.copy(id = UUID.randomUUID(), externalId = "test-id-2"),
      transaction.copy(id = UUID.randomUUID(), externalId = "test-id-3")
    )

    for {
      _ <- store.init()
      (initialSuccessful, initialExisting) <- store.load(transactions)
      initialStored <- store.all(forPeriod = Period.current)
      (nextSuccessful, nextExisting) <- store.load(
        transactions :+ transaction.copy(id = UUID.randomUUID(), externalId = "test-id-4")
      )
      nextStored <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      initialSuccessful should be(3)
      initialExisting should be(0)
      initialStored.length should be(3)

      nextSuccessful should be(1)
      nextExisting should be(3)
      nextStored.length should be(4)
    }
  }

  it should "update existing transactions" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transaction = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id",
      `type` = Transaction.Type.Credit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(transaction)
      existing <- store.get(transaction = transaction.id).map {
        case Some(value) => value
        case None        => fail("Expected an existing transaction but none was found")
      }
      _ <- store.update(existing.copy(category = "other-category"))
      updated <- store.get(transaction = transaction.id)
      _ <- store.drop()
    } yield {
      existing should be(transaction)
      updated should be(Some(transaction.copy(category = "other-category")))
    }
  }

  it should "fail to retrieve missing transactions" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    for {
      _ <- store.init()
      missing <- store.get(transaction = Transaction.Id.generate())
      _ <- store.drop()
    } yield {
      missing should be(None)
    }
  }

  it should "retrieve all transactions" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transactionId1 = Transaction.Id.generate()
    val transactionId2 = Transaction.Id.generate()
    val transactionId3 = Transaction.Id.generate()

    val transaction = Transaction(
      id = transactionId1,
      externalId = "test-id",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(transaction)
      _ <- store.create(transaction.copy(id = transactionId2, externalId = transactionId2.toString, category = "test-category-2"))
      _ <- store.create(transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3"))
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          transaction.copy(id = transactionId1),
          transaction.copy(id = transactionId2, externalId = transactionId2.toString, category = "test-category-2"),
          transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3")
        )
      )

      all should be(
        Seq(
          transaction.copy(id = transactionId1),
          transaction.copy(id = transactionId2, externalId = transactionId2.toString, category = "test-category-2"),
          transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3")
        )
      )
    }
  }

  it should "retrieve transactions for a specific period" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transactionId1 = Transaction.Id.generate()
    val transactionId2 = Transaction.Id.generate()
    val transactionId3 = Transaction.Id.generate()
    val oldTimestamp = LocalDate.now().minusDays(60)

    val transaction = Transaction(
      id = transactionId1,
      externalId = "test-id",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(transaction)
      _ <- store.create(
        transaction.copy(
          id = transactionId2,
          externalId = transactionId2.toString,
          category = "test-category-2",
          date = oldTimestamp
        )
      )
      _ <- store.create(transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3"))
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          transaction.copy(id = transactionId1),
          transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3")
        )
      )

      all should be(
        Seq(
          transaction.copy(id = transactionId1),
          transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3")
        )
      )
    }
  }

  it should "retrieve transactions up to (and including) a specific period" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transaction1 = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
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
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
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
      amount = 3,
      currency = "EUR",
      date = LocalDate.parse("2020-06-03"),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(transaction1)
      _ <- store.create(transaction2)
      _ <- store.create(transaction3)
      prevYear <- store.to(period = Period("2019-12"))
      upToJan <- store.to(period = Period("2020-01"))
      upToFeb <- store.to(period = Period("2020-02"))
      upToMar <- store.to(period = Period("2020-03"))
      upToApr <- store.to(period = Period("2020-04"))
      upToMay <- store.to(period = Period("2020-05"))
      upToJun <- store.to(period = Period("2020-06"))
      upToJul <- store.to(period = Period("2020-07"))
      upToAug <- store.to(period = Period("2020-08"))
      upToSep <- store.to(period = Period("2020-09"))
      upToOct <- store.to(period = Period("2020-10"))
      upToNov <- store.to(period = Period("2020-11"))
      upToDec <- store.to(period = Period("2020-12"))
      nextYear <- store.to(period = Period("2021-01"))
      _ <- store.drop()
    } yield {
      prevYear should be(empty)
      upToJan should be(empty)
      upToFeb should be(empty)
      upToMar should be(Seq(transaction1))
      upToApr should be(Seq(transaction1, transaction2))
      upToMay should be(Seq(transaction1, transaction2))
      upToJun should be(Seq(transaction1, transaction2, transaction3))
      upToJul should be(Seq(transaction1, transaction2, transaction3))
      upToAug should be(Seq(transaction1, transaction2, transaction3))
      upToSep should be(Seq(transaction1, transaction2, transaction3))
      upToOct should be(Seq(transaction1, transaction2, transaction3))
      upToNov should be(Seq(transaction1, transaction2, transaction3))
      upToDec should be(Seq(transaction1, transaction2, transaction3))
      nextYear should be(Seq(transaction1, transaction2, transaction3))
    }
  }

  it should "mark transactions as removed" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transactionId1 = Transaction.Id.generate()
    val transactionId2 = Transaction.Id.generate()
    val transactionId3 = Transaction.Id.generate()

    val transaction = Transaction(
      id = transactionId1,
      externalId = "test-id",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(transaction)
      _ <- store.create(transaction.copy(id = transactionId2, externalId = transactionId2.toString, category = "test-category-2"))
      _ <- store.create(transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3"))
      _ <- store.delete(transaction = transactionId2)
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          transaction.copy(id = transactionId1),
          transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3")
        )
      )

      all should be(
        Seq(
          transaction.copy(id = transactionId1),
          transaction.copy(
            id = transactionId2,
            category = "test-category-2",
            externalId = transactionId2.toString,
            removed = all.find(_.id == transactionId2).flatMap(_.removed)
          ),
          transaction.copy(id = transactionId3, externalId = transactionId3.toString, category = "test-category-3")
        )
      )
    }
  }

  it should "support searching for transactions based on a query string" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transaction1 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-external-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-1",
      notes = Some("abcdefgh"),
      created = now,
      updated = now,
      removed = None
    )

    val transaction2 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-external-id-2",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-2",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    val transaction3 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-external-id-3",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-3",
      notes = Some("cdefghijkl"),
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(transaction1)
      _ <- store.create(transaction2)
      _ <- store.create(transaction3)
      all <- store.all(forPeriod = Period.current)
      query1 <- store.search(query = "test-external-id-1")
      query2 <- store.search(query = "test-category-2")
      query3 <- store.search(query = "test")
      query4 <- store.search(query = "abcd")
      query5 <- store.search(query = "efgh")
      query6 <- store.search(query = "ijkl")
      _ <- store.drop()
    } yield {
      all.length should be(3)
      query1 should be(Seq(transaction1))
      query2 should be(Seq(transaction2))
      query3 should be(Seq(transaction1, transaction2, transaction3))
      query4 should be(Seq(transaction1))
      query5 should be(Seq(transaction1, transaction3))
      query6 should be(Seq(transaction3))
    }
  }

  it should "support retrieving all unique transaction categories" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transaction = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-external-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-1",
      notes = Some("abcdefgh"),
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(transaction)
      _ <- store.create(transaction.copy(id = UUID.randomUUID(), externalId = "test-id-2", category = "test-category-2"))
      _ <- store.create(transaction.copy(id = UUID.randomUUID(), externalId = "test-id-3", category = "test-category-2"))
      _ <- store.create(transaction.copy(id = UUID.randomUUID(), externalId = "test-id-4", category = "test-category-3"))
      categories <- store.categories()
      _ <- store.drop()
    } yield {
      categories.sorted should be(Seq("test-category-1", "test-category-2", "test-category-3"))
    }
  }

  it should "support retrieving transactions between dates for an account" in {
    val store = new DefaultTransactionStore(
      tableName = "DefaultTransactionStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val transaction1 = Transaction(
      id = UUID.randomUUID(),
      externalId = "test-id-1",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-1",
      notes = Some("abcdefgh"),
      created = now,
      updated = now,
      removed = None
    )

    val transaction2 =
      transaction1.copy(id = UUID.randomUUID(), date = LocalDate.now().plusDays(1), from = 2, externalId = "test-id-2")

    val transaction3 =
      transaction1.copy(id = UUID.randomUUID(), date = LocalDate.now().plusDays(2), from = 3, externalId = "test-id-3")

    val transaction4 =
      transaction1.copy(id = UUID.randomUUID(), date = LocalDate.now().minusDays(1), from = 3, externalId = "test-id-4")

    val transaction5 =
      transaction1.copy(id = UUID.randomUUID(), date = LocalDate.now().minusDays(2), externalId = "test-id-5")

    val transaction6 =
      transaction1.copy(id = UUID.randomUUID(), date = LocalDate.now(), from = 3, externalId = "test-id-6")

    val transaction7 =
      transaction1.copy(id = UUID.randomUUID(), date = LocalDate.now().minusDays(2), from = 3, externalId = "test-id-7")

    for {
      _ <- store.init()
      _ <- store.create(transaction1)
      _ <- store.create(transaction2)
      _ <- store.create(transaction3)
      _ <- store.create(transaction4)
      _ <- store.create(transaction5)
      _ <- store.create(transaction6)
      _ <- store.create(transaction7)
      allForAccount1 <- store.between(start = LocalDate.now().minusDays(2), end = LocalDate.now().plusDays(2), account = 1)
      allForAccount2 <- store.between(start = LocalDate.now().minusDays(2), end = LocalDate.now().plusDays(2), account = 2)
      allForAccount3 <- store.between(start = LocalDate.now().minusDays(2), end = LocalDate.now().plusDays(2), account = 3)
      yesterdayForAccount3 <- store.between(start = LocalDate.now().minusDays(1), end = LocalDate.now().minusDays(1), account = 3)
      _ <- store.drop()
    } yield {
      allForAccount1.map(_.externalId) should be(Seq("test-id-1", "test-id-5"))
      allForAccount2.map(_.externalId) should be(Seq("test-id-2"))
      allForAccount3.map(_.externalId) should be(Seq("test-id-3", "test-id-4", "test-id-6", "test-id-7"))
      yesterdayForAccount3.map(_.externalId) should be(Seq("test-id-4"))
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultTransactionStoreSpec"
  )

  private val h2db = H2Profile.api.Database.forURL(url = "jdbc:h2:mem:DefaultTransactionStoreSpec", keepAliveConnection = true)

  override protected def afterAll(): Unit = {
    h2db.close()
    typedSystem.terminate()
  }
}
