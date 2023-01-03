package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.{Forecast, Transaction}

import java.time.{Instant, LocalDate}

class UpdateForecastSpec extends UnitSpec {
  "An UpdateForecast request" should "support updating existing forecasts" in {
    val now = Instant.now()

    val request = UpdateForecast(
      `type` = Transaction.Type.Credit,
      account = 3,
      amount = 45,
      currency = "EUR",
      date = Some(LocalDate.now().minusDays(1)),
      category = "other-category",
      notes = None,
      disregardAfter = 15
    )

    val original = Forecast(
      id = 0,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category",
      notes = Some("test-notes"),
      disregardAfter = 1,
      created = now,
      updated = now,
      removed = None
    )

    val actual = request.toForecast(existing = original)

    val expected = original.copy(
      `type` = request.`type`,
      account = request.account,
      amount = request.amount,
      date = request.date,
      category = request.category,
      notes = request.notes,
      disregardAfter = request.disregardAfter,
      updated = actual.updated
    )

    actual should be(expected)
    actual.created should not be actual.updated
  }

  it should "require valid `disregardAfter` values" in {
    val request = UpdateForecast(
      `type` = Transaction.Type.Credit,
      account = 3,
      amount = 45,
      currency = "EUR",
      date = Some(LocalDate.now().minusDays(1)),
      category = "other-category",
      notes = None,
      disregardAfter = 1
    )

    an[IllegalArgumentException] should be thrownBy request.copy(disregardAfter = 0)
    noException should be thrownBy request.copy(disregardAfter = 1)
    noException should be thrownBy request.copy(disregardAfter = 10)
    noException should be thrownBy request.copy(disregardAfter = 20)
    noException should be thrownBy request.copy(disregardAfter = 30)
    noException should be thrownBy request.copy(disregardAfter = 31)
    an[IllegalArgumentException] should be thrownBy request.copy(disregardAfter = 32)
  }
}
