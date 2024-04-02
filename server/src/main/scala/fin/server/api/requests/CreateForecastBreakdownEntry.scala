package fin.server.api.requests

import fin.server.model.{Account, ForecastBreakdownEntry, Transaction}

import java.time.{Instant, LocalDate}

final case class CreateForecastBreakdownEntry(
  `type`: Transaction.Type,
  account: Account.Id,
  amount: BigDecimal,
  currency: String,
  date: LocalDate,
  category: String,
  notes: Option[String]
) {
  def toForecastBreakdownEntry: ForecastBreakdownEntry =
    ForecastBreakdownEntry(
      id = 0,
      `type` = `type`,
      account = account,
      amount = amount,
      currency = currency,
      date = date,
      category = category,
      notes = notes,
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )
}
