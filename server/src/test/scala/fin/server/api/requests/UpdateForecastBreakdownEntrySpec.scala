package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.{ForecastBreakdownEntry, Transaction}

import java.time.{Instant, LocalDate}

class UpdateForecastBreakdownEntrySpec extends UnitSpec {
  "An UpdateForecastBreakdownEntry request" should "support updating existing forecasts" in {
    val now = Instant.now()

    val request = UpdateForecastBreakdownEntry(
      `type` = Transaction.Type.Credit,
      account = 3,
      amount = 45,
      currency = "EUR",
      date = LocalDate.now().minusDays(1),
      category = "other-category",
      notes = None
    )

    val original = ForecastBreakdownEntry(
      id = 0,
      `type` = Transaction.Type.Debit,
      account = 1,
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = Some("test-notes"),
      created = now,
      updated = now,
      removed = None
    )

    val actual = request.toForecastBreakdownEntry(existing = original)

    val expected = original.copy(
      `type` = request.`type`,
      account = request.account,
      amount = request.amount,
      date = request.date,
      category = request.category,
      notes = request.notes,
      updated = actual.updated
    )

    actual should be(expected)
    actual.created should not be actual.updated
  }
}
