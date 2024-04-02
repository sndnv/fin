package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.{ForecastBreakdownEntry, Transaction}

import java.time.LocalDate

class CreateForecastBreakdownEntrySpec extends UnitSpec {
  "A CreateForecastBreakdownEntry request" should "support creating a forecast" in {
    val request = CreateForecastBreakdownEntry(
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = Some("test-notes")
    )

    val actual = request.toForecastBreakdownEntry

    val expected = ForecastBreakdownEntry(
      id = 0,
      `type` = request.`type`,
      account = request.account,
      amount = request.amount,
      currency = request.currency,
      date = request.date,
      category = request.category,
      notes = request.notes,
      created = actual.created,
      updated = actual.updated,
      removed = None
    )

    actual should be(expected)
  }
}
