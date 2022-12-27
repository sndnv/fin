package fin.server.api.requests

import fin.server.model.{Account, Transaction}

import java.time.{Instant, LocalDate}

final case class CreateTransaction(
  `type`: Transaction.Type,
  from: Account.Id,
  to: Option[Account.Id],
  amount: BigDecimal,
  currency: String,
  date: LocalDate,
  category: String,
  notes: Option[String]
) {
  require(!to.contains(from), "Cannot create a transaction with the same `from` and `to` accounts")

  def toTransaction: Transaction = {
    val id = Transaction.Id.generate()

    Transaction(
      id = id,
      externalId = id.toString,
      `type` = `type`,
      from = from,
      to = to,
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
}
