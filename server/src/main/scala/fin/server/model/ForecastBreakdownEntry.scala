package fin.server.model

import java.time.{Instant, LocalDate}

final case class ForecastBreakdownEntry(
  id: ForecastBreakdownEntry.Id,
  `type`: Transaction.Type,
  account: Account.Id,
  amount: BigDecimal,
  currency: String,
  date: LocalDate,
  category: String,
  notes: Option[String],
  created: Instant,
  updated: Instant,
  removed: Option[Instant]
)

object ForecastBreakdownEntry {
  type Id = Int
}
