package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.Transaction

import java.time.{Instant, LocalDate}

class UpdateTransactionSpec extends UnitSpec {
  "An UpdateAccountUpdateTransaction request" should "support updating existing transactions" in {
    val now = Instant.now()

    val request = UpdateTransaction(
      externalId = "other-id",
      `type` = Transaction.Type.Credit,
      from = 3,
      to = Some(4),
      amount = 45,
      currency = "EUR",
      date = LocalDate.now().minusDays(1),
      category = "other-category",
      notes = None
    )

    val original = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 123.4,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = Some("test-notes"),
      created = now,
      updated = now,
      removed = None
    )

    val actual = request.toTransaction(existing = original)

    val expected = original.copy(
      externalId = request.externalId,
      `type` = request.`type`,
      from = request.from,
      to = request.to,
      amount = request.amount,
      date = request.date,
      category = request.category,
      notes = request.notes,
      updated = actual.updated
    )

    actual should be(expected)
    actual.created should not be actual.updated
  }

  it should "require different `from` and `to` accounts" in {
    val request = UpdateTransaction(
      externalId = "other-id",
      `type` = Transaction.Type.Credit,
      from = 3,
      to = Some(4),
      amount = 45,
      currency = "EUR",
      date = LocalDate.now().minusDays(1),
      category = "other-category",
      notes = None
    )

    noException should be thrownBy request.copy(from = 1, to = None)
    an[IllegalArgumentException] should be thrownBy request.copy(from = 1, to = Some(1))
  }
}
