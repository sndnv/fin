package fin.server.api.requests

import fin.server.model.{Account, Transaction}

import java.time.{Instant, LocalDate}

final case class UpdateTransaction(
  externalId: String,
  `type`: Transaction.Type,
  from: Account.Id,
  to: Option[Account.Id],
  amount: BigDecimal,
  currency: String,
  date: LocalDate,
  category: String,
  notes: Option[String]
) {
  require(!to.contains(from), "Cannot update a transaction to have the same `from` and `to` accounts")

  def toTransaction(existing: Transaction): Transaction =
    existing.copy(
      externalId = externalId,
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
