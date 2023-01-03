package fin.server.model

import java.time.{Instant, LocalDate}

final case class Forecast(
  id: Forecast.Id,
  `type`: Transaction.Type,
  account: Account.Id,
  amount: BigDecimal,
  currency: String,
  date: Option[LocalDate],
  category: String,
  notes: Option[String],
  disregardAfter: Int,
  created: Instant,
  updated: Instant,
  removed: Option[Instant]
)

object Forecast {
  type Id = Int
}
