package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.{Forecast, Transaction}

import java.time.LocalDate

class CreateForecastSpec extends UnitSpec {
  "A CreateForecast request" should "support creating a forecast" in {
    val request = CreateForecast(
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category",
      notes = Some("test-notes"),
      disregardAfter = 1
    )

    val actual = request.toForecast

    val expected = Forecast(
      id = 0,
      `type` = request.`type`,
      account = request.account,
      amount = request.amount,
      currency = request.currency,
      date = request.date,
      category = request.category,
      notes = request.notes,
      disregardAfter = request.disregardAfter,
      created = actual.created,
      updated = actual.updated,
      removed = None
    )

    actual should be(expected)
  }

  it should "require valid `disregardAfter` values" in {
    val request = CreateForecast(
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = Some(LocalDate.now()),
      category = "test-category",
      notes = Some("test-notes"),
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
