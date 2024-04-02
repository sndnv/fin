package fin.server.persistence.forecasts

import fin.server.UnitSpec
import fin.server.model.{ForecastBreakdownEntry, Period, Transaction}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import slick.jdbc.H2Profile

import java.time.{Instant, LocalDate}

class DefaultForecastBreakdownEntryStoreSpec extends UnitSpec with BeforeAndAfterAll {
  "A DefaultForecastBreakdownEntryStore" should "store and retrieve forecast breakdown entries" in {
    val store = new DefaultForecastBreakdownEntryStore(
      tableName = "DefaultForecastBreakdownEntryStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val entry = ForecastBreakdownEntry(
      id = 0,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    val expectedId = 1

    for {
      _ <- store.init()
      _ <- store.create(entry)
      existing <- store.get(entry = expectedId)
      _ <- store.drop()
    } yield {
      existing should be(Some(entry.copy(id = expectedId)))
    }
  }

  it should "update existing forecast breakdown entries" in {
    val store = new DefaultForecastBreakdownEntryStore(
      tableName = "DefaultForecastBreakdownEntryStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val entry = ForecastBreakdownEntry(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    val expectedId = 1

    for {
      _ <- store.init()
      _ <- store.create(entry)
      existing <- store.get(entry = expectedId).map {
        case Some(value) => value
        case None        => fail("Expected an existing forecast breakdown entry but none was found")
      }
      _ <- store.update(existing.copy(notes = Some("other-notes")))
      updated <- store.get(entry = expectedId)
      _ <- store.drop()
    } yield {
      existing should be(entry.copy(id = expectedId))
      updated should be(Some(entry.copy(id = expectedId, notes = Some("other-notes"))))
    }
  }

  it should "fail to retrieve missing forecast breakdown entries" in {
    val store = new DefaultForecastBreakdownEntryStore(
      tableName = "DefaultForecastBreakdownEntryStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    for {
      _ <- store.init()
      missing <- store.get(entry = 1)
      _ <- store.drop()
    } yield {
      missing should be(None)
    }
  }

  it should "retrieve all forecast breakdown entries" in {
    val store = new DefaultForecastBreakdownEntryStore(
      tableName = "DefaultForecastBreakdownEntryStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val entry = ForecastBreakdownEntry(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
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
      _ <- store.create(entry)
      _ <- store.create(entry.copy(notes = Some("test-notes-2")))
      _ <- store.create(entry.copy(notes = Some("test-notes-3")))
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          entry.copy(id = 1),
          entry.copy(id = 2, notes = Some("test-notes-2")),
          entry.copy(id = 3, notes = Some("test-notes-3"))
        )
      )

      all should be(
        Seq(
          entry.copy(id = 1),
          entry.copy(id = 2, notes = Some("test-notes-2")),
          entry.copy(id = 3, notes = Some("test-notes-3"))
        )
      )
    }
  }

  it should "retrieve forecast breakdown entries for a specific period" in {
    val store = new DefaultForecastBreakdownEntryStore(
      tableName = "DefaultForecastBreakdownEntryStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val oldTimestamp = LocalDate.now().minusDays(60)
    val entry = ForecastBreakdownEntry(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
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
      _ <- store.create(entry)
      _ <- store.create(entry.copy(date = oldTimestamp, notes = Some("test-notes-2")))
      _ <- store.create(entry.copy(notes = Some("test-notes-3")))
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          entry.copy(id = 1),
          entry.copy(id = 3, notes = Some("test-notes-3"))
        )
      )

      all should be(
        Seq(
          entry.copy(id = 1),
          entry.copy(id = 3, notes = Some("test-notes-3"))
        )
      )
    }
  }

  it should "mark forecast breakdown entries as removed" in {
    val store = new DefaultForecastBreakdownEntryStore(
      tableName = "DefaultForecastBreakdownEntryStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()
    val entry = ForecastBreakdownEntry(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
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
      _ <- store.create(entry)
      _ <- store.create(entry.copy(notes = Some("test-notes-2")))
      _ <- store.create(entry.copy(notes = Some("test-notes-3")))
      _ <- store.delete(entry = 2)
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          entry.copy(id = 1),
          entry.copy(id = 3, notes = Some("test-notes-3"))
        )
      )

      all should be(
        Seq(
          entry.copy(id = 1),
          entry.copy(id = 2, notes = Some("test-notes-2"), removed = all.find(_.id == 2).flatMap(_.removed)),
          entry.copy(id = 3, notes = Some("test-notes-3"))
        )
      )
    }
  }

  it should "support retrieving all unique transaction categories" in {
    val store = new DefaultForecastBreakdownEntryStore(
      tableName = "DefaultForecastBreakdownEntryStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()
    val entry = ForecastBreakdownEntry(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category-1",
      notes = None,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(entry)
      _ <- store.create(entry.copy(category = "test-category-2"))
      _ <- store.create(entry.copy(category = "test-category-2"))
      _ <- store.create(entry.copy(category = "test-category-3"))
      categories <- store.categories()
      _ <- store.drop()
    } yield {
      categories.sorted should be(Seq("test-category-1", "test-category-2", "test-category-3"))
    }
  }

  it should "provide a list of migrations" in {
    val store = new DefaultForecastBreakdownEntryStore(
      tableName = "DefaultForecastBreakdownEntryStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    store.migrations.toList match {
      case v1 :: Nil =>
        v1.version should be(1)

        h2db.run(v1.needed).await should be(true)
        h2db.run(v1.action).await
        h2db.run(v1.needed).await should be(false)

      case other =>
        fail(s"Unexpected migrations found: [$other]")
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultForecastBreakdownEntryStoreSpec"
  )

  private val h2db =
    H2Profile.api.Database.forURL(url = "jdbc:h2:mem:DefaultForecastBreakdownEntryStoreSpec", keepAliveConnection = true)

  override protected def afterAll(): Unit = {
    h2db.close()
    typedSystem.terminate()
  }
}
