package fin.server.api.responses

import fin.server.UnitSpec
import fin.server.model.Transaction

import java.time.{Instant, LocalDate}

class TransactionSummarySpec extends UnitSpec {
  "A TransactionSummary" should "support providing an empty summary" in {
    TransactionSummary.empty should be(TransactionSummary(currencies = Map.empty))
  }

  it should "support updating itself with provided transactions" in {
    val transaction = Transaction(
      id = Transaction.Id.generate(),
      externalId = "test-id",
      `type` = Transaction.Type.Debit,
      from = 1,
      to = Some(2),
      amount = 100.0,
      currency = "EUR",
      date = LocalDate.now(),
      category = "test-category",
      notes = Some("test-notes"),
      created = Instant.now(),
      updated = Instant.now(),
      removed = None
    )

    val initial = TransactionSummary.empty
    val afterUpdate1 = initial.withTransaction(transaction)
    val afterUpdate2 = afterUpdate1.withTransaction(transaction.copy(amount = 25))
    val afterUpdate3 = afterUpdate2.withTransaction(transaction.copy(amount = 50, currency = "USD"))
    val afterUpdate4 = afterUpdate3.withTransaction(transaction.copy(`type` = Transaction.Type.Credit, currency = "USD"))
    val afterUpdate5 = afterUpdate4.withTransaction(transaction.copy(`type` = Transaction.Type.Credit, amount = 500))
    val afterUpdate6 = afterUpdate5.withTransaction(transaction.copy(`type` = Transaction.Type.Credit, currency = "CHF"))

    initial should be(TransactionSummary(currencies = Map.empty))

    afterUpdate1 should be(
      TransactionSummary(
        currencies = Map(
          "EUR" -> TransactionSummary.ForCurrency(income = 0.0, expenses = 100.0)
        )
      )
    )

    afterUpdate2 should be(
      TransactionSummary(
        currencies = Map(
          "EUR" -> TransactionSummary.ForCurrency(income = 0.0, expenses = 125.0)
        )
      )
    )

    afterUpdate3 should be(
      TransactionSummary(
        currencies = Map(
          "EUR" -> TransactionSummary.ForCurrency(income = 0.0, expenses = 125.0),
          "USD" -> TransactionSummary.ForCurrency(income = 0.0, expenses = 50.0)
        )
      )
    )

    afterUpdate4 should be(
      TransactionSummary(
        currencies = Map(
          "EUR" -> TransactionSummary.ForCurrency(income = 0.0, expenses = 125.0),
          "USD" -> TransactionSummary.ForCurrency(income = 100.0, expenses = 50.0)
        )
      )
    )

    afterUpdate5 should be(
      TransactionSummary(
        currencies = Map(
          "EUR" -> TransactionSummary.ForCurrency(income = 500.0, expenses = 125.0),
          "USD" -> TransactionSummary.ForCurrency(income = 100.0, expenses = 50.0)
        )
      )
    )

    afterUpdate6 should be(
      TransactionSummary(
        currencies = Map(
          "EUR" -> TransactionSummary.ForCurrency(income = 500.0, expenses = 125.0),
          "USD" -> TransactionSummary.ForCurrency(income = 100.0, expenses = 50.0),
          "CHF" -> TransactionSummary.ForCurrency(income = 100.0, expenses = 0.0)
        )
      )
    )
  }
}
