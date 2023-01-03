package fin.server.api.requests

import fin.server.model.{Account, Forecast, Transaction}

import java.time.{Instant, LocalDate}

final case class CreateForecast(
  `type`: Transaction.Type,
  account: Account.Id,
  amount: BigDecimal,
  currency: String,
  category: String,
  date: Option[LocalDate],
  notes: Option[String],
  disregardAfter: Int
) {
  require(disregardAfter > 0 && disregardAfter <= 31, "Invalid day-of-month value provided for `disregardAfter`")

  def toForecast: Forecast =
    Forecast(
      id = 0,
      `type` = `type`,
      account = account,
      amount = amount,
      currency = currency,
      date = date,
      category = category,
      notes = notes,
      disregardAfter = disregardAfter,
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )
}
