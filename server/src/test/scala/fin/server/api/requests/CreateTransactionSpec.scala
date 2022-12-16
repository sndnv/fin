package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.Transaction

import java.time.LocalDate

class CreateTransactionSpec extends UnitSpec {
  "A CreateTransaction request" should "be support creating a transaction" in {
    val request = CreateTransaction(
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = Some("test-notes")
    )

    val actual = request.toTransaction

    val expected = Transaction(
      id = actual.id,
      externalId = actual.externalId,
      `type` = request.`type`,
      from = request.from,
      to = request.to,
      amount = request.amount,
      currency = request.currency,
      notes = request.notes,
      category = request.category,
      date = request.date,
      created = actual.created,
      updated = actual.updated,
      removed = None
    )

    actual should be(expected)
  }
}
