package fin.server.api.requests

import fin.server.model.{Account, ForecastBreakdownEntry, Transaction}

import java.time.{Instant, LocalDate}

final case class UpdateForecastBreakdownEntry(
  `type`: Transaction.Type,
  account: Account.Id,
  amount: BigDecimal,
  currency: String,
  date: LocalDate,
  category: String,
  notes: Option[String]
) {
  def toForecastBreakdownEntry(existing: ForecastBreakdownEntry): ForecastBreakdownEntry =
    existing.copy(
      `type` = `type`,
      account = account,
      amount = amount,
      currency = currency,
      date = date,
      category = category,
      notes = notes,
      updated = Instant.now()
    )
}
