package fin.server.api.requests

import fin.server.model.{Account, Transaction}

import java.time.{Instant, LocalDate}

final case class UpdateTransaction(
  `type`: Transaction.Type,
  from: Account.Id,
  to: Option[Account.Id],
  amount: BigDecimal,
  currency: String,
  date: LocalDate,
  category: String,
  notes: Option[String]
) {
  def toTransaction(existing: Transaction): Transaction =
    existing.copy(
      `type` = `type`,
      from = from,
      to = to,
      amount = amount,
      currency = currency,
      date = date,
      category = category,
      notes = notes,
      updated = Instant.now()
    )
}
