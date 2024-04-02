package fin.server.persistence.forecasts

import fin.server.UnitSpec
import fin.server.model.{Forecast, Period, Transaction}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import slick.jdbc.H2Profile

import java.time.{Instant, LocalDate}

class DefaultForecastStoreSpec extends UnitSpec with BeforeAndAfterAll {
  "A DefaultForecastStore" should "store and retrieve forecasts" in {
    val store = new DefaultForecastStore(
      tableName = "DefaultForecastStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val forecast = Forecast(
      id = 0,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category",
      notes = None,
      disregardAfter = 1,
      created = now,
      updated = now,
      removed = None
    )

    val expectedId = 1

    for {
      _ <- store.init()
      _ <- store.create(forecast)
      existing <- store.get(forecast = expectedId)
      _ <- store.drop()
    } yield {
      existing should be(Some(forecast.copy(id = expectedId)))
    }
  }

  it should "update existing forecasts" in {
    val store = new DefaultForecastStore(
      tableName = "DefaultForecastStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val forecast = Forecast(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category",
      notes = None,
      disregardAfter = 1,
      created = now,
      updated = now,
      removed = None
    )

    val expectedId = 1

    for {
      _ <- store.init()
      _ <- store.create(forecast)
      existing <- store.get(forecast = expectedId).map {
        case Some(value) => value
        case None        => fail("Expected an existing forecast but none was found")
      }
      _ <- store.update(existing.copy(notes = Some("other-notes")))
      updated <- store.get(forecast = expectedId)
      _ <- store.drop()
    } yield {
      existing should be(forecast.copy(id = expectedId))
      updated should be(Some(forecast.copy(id = expectedId, notes = Some("other-notes"))))
    }
  }

  it should "fail to retrieve missing forecasts" in {
    val store = new DefaultForecastStore(
      tableName = "DefaultForecastStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    for {
      _ <- store.init()
      missing <- store.get(forecast = 1)
      _ <- store.drop()
    } yield {
      missing should be(None)
    }
  }

  it should "retrieve all forecasts" in {
    val store = new DefaultForecastStore(
      tableName = "DefaultForecastStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val forecast = Forecast(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category",
      notes = None,
      disregardAfter = 1,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(forecast)
      _ <- store.create(forecast.copy(notes = Some("test-notes-2")))
      _ <- store.create(forecast.copy(notes = Some("test-notes-3")))
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          forecast.copy(id = 1),
          forecast.copy(id = 2, notes = Some("test-notes-2")),
          forecast.copy(id = 3, notes = Some("test-notes-3"))
        )
      )

      all should be(
        Seq(
          forecast.copy(id = 1),
          forecast.copy(id = 2, notes = Some("test-notes-2")),
          forecast.copy(id = 3, notes = Some("test-notes-3"))
        )
      )
    }
  }

  it should "retrieve forecasts for a specific period" in {
    val store = new DefaultForecastStore(
      tableName = "DefaultForecastStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val oldTimestamp = LocalDate.now().minusDays(60)

    val forecast = Forecast(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category",
      notes = None,
      disregardAfter = 1,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(forecast)
      _ <- store.create(forecast.copy(date = Some(oldTimestamp), notes = Some("test-notes-2")))
      _ <- store.create(forecast.copy(date = None, notes = Some("test-notes-3")))
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          forecast.copy(id = 1),
          forecast.copy(id = 3, date = None, notes = Some("test-notes-3"))
        )
      )

      all should be(
        Seq(
          forecast.copy(id = 1),
          forecast.copy(id = 3, date = None, notes = Some("test-notes-3"))
        )
      )
    }
  }

  it should "mark forecasts as removed" in {
    val store = new DefaultForecastStore(
      tableName = "DefaultForecastStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val forecast = Forecast(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category",
      notes = None,
      disregardAfter = 1,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(forecast)
      _ <- store.create(forecast.copy(notes = Some("test-notes-2")))
      _ <- store.create(forecast.copy(notes = Some("test-notes-3")))
      _ <- store.delete(forecast = 2)
      available <- store.available(forPeriod = Period.current)
      all <- store.all(forPeriod = Period.current)
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          forecast.copy(id = 1),
          forecast.copy(id = 3, notes = Some("test-notes-3"))
        )
      )

      all should be(
        Seq(
          forecast.copy(id = 1),
          forecast.copy(id = 2, notes = Some("test-notes-2"), removed = all.find(_.id == 2).flatMap(_.removed)),
          forecast.copy(id = 3, notes = Some("test-notes-3"))
        )
      )
    }
  }

  it should "support retrieving all unique transaction categories" in {
    val store = new DefaultForecastStore(
      tableName = "DefaultForecastStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val forecast = Forecast(
      id = 0,
      `type` = Transaction.Type.Credit,
      account = 1,
      amount = 3,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category-1",
      notes = None,
      disregardAfter = 1,
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(forecast)
      _ <- store.create(forecast.copy(category = "test-category-2"))
      _ <- store.create(forecast.copy(category = "test-category-2"))
      _ <- store.create(forecast.copy(category = "test-category-3"))
      categories <- store.categories()
      _ <- store.drop()
    } yield {
      categories.sorted should be(Seq("test-category-1", "test-category-2", "test-category-3"))
    }
  }

  it should "provide a list of migrations" in {
    val store = new DefaultForecastStore(
      tableName = "DefaultForecastStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    store.migrations should be(empty)
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultForecastStoreSpec"
  )

  private val h2db = H2Profile.api.Database.forURL(url = "jdbc:h2:mem:DefaultForecastStoreSpec", keepAliveConnection = true)

  override protected def afterAll(): Unit = {
    h2db.close()
    typedSystem.terminate()
  }
}
