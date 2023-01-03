package fin.server.api.requests

import fin.server.model.{Account, Forecast, Transaction}

import java.time.{Instant, LocalDate}

final case class UpdateForecast(
  `type`: Transaction.Type,
  account: Account.Id,
  amount: BigDecimal,
  currency: String,
  date: Option[LocalDate],
  category: String,
  notes: Option[String],
  disregardAfter: Int
) {
  require(disregardAfter > 0 && disregardAfter <= 31, "Invalid day-of-month value provided for `disregardAfter`")

  def toForecast(existing: Forecast): Forecast =
    existing.copy(
      `type` = `type`,
      account = account,
      amount = amount,
      currency = currency,
      date = date,
      category = category,
      notes = notes,
      disregardAfter = disregardAfter,
      updated = Instant.now()
    )
}
