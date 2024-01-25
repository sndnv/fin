package fin.server.api.responses

import fin.server.UnitSpec
import fin.server.model.Transaction

import java.time.{Instant, LocalDate}

class TransactionBreakdownSpec extends UnitSpec {
  "A TransactionBreakdown" should "support parsing breakdown types" in {
    TransactionBreakdown.BreakdownType(value = "by-year") should be(TransactionBreakdown.BreakdownType.ByYear)
    TransactionBreakdown.BreakdownType(value = "by-month") should be(TransactionBreakdown.BreakdownType.ByMonth)
    TransactionBreakdown.BreakdownType(value = "by-week") should be(TransactionBreakdown.BreakdownType.ByWeek)
    TransactionBreakdown.BreakdownType(value = "by-day") should be(TransactionBreakdown.BreakdownType.ByDay)
    an[IllegalArgumentException] should be thrownBy TransactionBreakdown.BreakdownType(value = "other")
  }

  it should "support creating a breakdown (by year) from a list of transactions" in {
    val actual = TransactionBreakdown(breakdownType = TransactionBreakdown.BreakdownType.ByYear, transactions = transactions)

    val expected = TransactionBreakdown(
      currencies = Map(
        "EUR" -> TransactionBreakdown.ForCurrency(
          periods = Map(
            "2019" -> TransactionBreakdown.ForPeriod(
              income = 50.asIncome(withTransactions = 1),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 50.asIncome(withTransactions = 1),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020" -> TransactionBreakdown.ForPeriod(
              income = 225.asIncome(withTransactions = 2),
              expenses = 400.asExpenses(withTransactions = 4),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 225.asIncome(withTransactions = 2),
                  expenses = 300.asExpenses(withTransactions = 3)
                ),
                "other-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            )
          )
        ),
        "USD" -> TransactionBreakdown.ForCurrency(
          periods = Map(
            "2020" -> TransactionBreakdown.ForPeriod(
              income = 42.asIncome(withTransactions = 1),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 42.asIncome(withTransactions = 1),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            )
          )
        )
      )
    )

    actual should be(expected)
  }

  it should "support creating a breakdown (by month) from a list of transactions" in {
    val actual = TransactionBreakdown(breakdownType = TransactionBreakdown.BreakdownType.ByMonth, transactions = transactions)

    val expected = TransactionBreakdown(
      currencies = Map(
        "EUR" -> TransactionBreakdown.ForCurrency(
          periods = Map(
            "2019-01" -> TransactionBreakdown.ForPeriod(
              income = 50.asIncome(withTransactions = 1),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 50.asIncome(withTransactions = 1),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-10" -> TransactionBreakdown.ForPeriod(
              income = 150.asIncome(withTransactions = 1),
              expenses = 200.asExpenses(withTransactions = 2),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 150.asIncome(withTransactions = 1),
                  expenses = 200.asExpenses(withTransactions = 2)
                )
              )
            ),
            "2020-11" -> TransactionBreakdown.ForPeriod(
              income = 75.asIncome(withTransactions = 1),
              expenses = 200.asExpenses(withTransactions = 2),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 75.asIncome(withTransactions = 1),
                  expenses = 100.asExpenses(withTransactions = 1)
                ),
                "other-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            )
          )
        ),
        "USD" -> TransactionBreakdown.ForCurrency(
          periods = Map(
            "2020-10" -> TransactionBreakdown.ForPeriod(
              income = 0.asIncome(withTransactions = 0),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-12" -> TransactionBreakdown.ForPeriod(
              income = 42.asIncome(withTransactions = 1),
              expenses = 0.asExpenses(withTransactions = 0),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 42.asIncome(withTransactions = 1),
                  expenses = 0.asExpenses(withTransactions = 0)
                )
              )
            )
          )
        )
      )
    )

    actual should be(expected)
  }

  it should "support creating a breakdown (by week) from a list of transactions" in {
    val actual = TransactionBreakdown(breakdownType = TransactionBreakdown.BreakdownType.ByWeek, transactions = transactions)

    val expected = TransactionBreakdown(
      currencies = Map(
        "EUR" -> TransactionBreakdown.ForCurrency(
          periods = Map(
            "2018-12-31" -> TransactionBreakdown.ForPeriod(
              income = 50.asIncome(withTransactions = 1),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 50.asIncome(withTransactions = 1),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-10-19" -> TransactionBreakdown.ForPeriod(
              income = 150.asIncome(withTransactions = 1),
              expenses = 200.asExpenses(withTransactions = 2),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 150.asIncome(withTransactions = 1),
                  expenses = 200.asExpenses(withTransactions = 2)
                )
              )
            ),
            "2020-11-09" -> TransactionBreakdown.ForPeriod(
              income = 75.asIncome(withTransactions = 1),
              expenses = 200.asExpenses(withTransactions = 2),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 75.asIncome(withTransactions = 1),
                  expenses = 100.asExpenses(withTransactions = 1)
                ),
                "other-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            )
          )
        ),
        "USD" -> TransactionBreakdown.ForCurrency(
          periods = Map(
            "2020-10-19" -> TransactionBreakdown.ForPeriod(
              income = 0.asIncome(withTransactions = 0),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-12-21" -> TransactionBreakdown.ForPeriod(
              income = 42.asIncome(withTransactions = 1),
              expenses = 0.asExpenses(withTransactions = 0),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 42.asIncome(withTransactions = 1),
                  expenses = 0.asExpenses(withTransactions = 0)
                )
              )
            )
          )
        )
      )
    )

    actual should be(expected)
  }

  it should "support creating a breakdown (by day) from a list of transactions" in {
    val actual = TransactionBreakdown(breakdownType = TransactionBreakdown.BreakdownType.ByDay, transactions = transactions)

    val expected = TransactionBreakdown(
      currencies = Map(
        "EUR" -> TransactionBreakdown.ForCurrency(
          periods = Map(
            "2019-01-01" -> TransactionBreakdown.ForPeriod(
              income = 0.asIncome(withTransactions = 0),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2019-01-02" -> TransactionBreakdown.ForPeriod(
              income = 50.asIncome(withTransactions = 1),
              expenses = 0.asExpenses(withTransactions = 0),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 50.asIncome(withTransactions = 1),
                  expenses = 0.asExpenses(withTransactions = 0)
                )
              )
            ),
            "2020-10-20" -> TransactionBreakdown.ForPeriod(
              income = 0.asIncome(withTransactions = 0),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-10-22" -> TransactionBreakdown.ForPeriod(
              income = 0.asIncome(withTransactions = 0),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-10-23" -> TransactionBreakdown.ForPeriod(
              income = 150.asIncome(withTransactions = 1),
              expenses = 0.asExpenses(withTransactions = 0),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 150.asIncome(withTransactions = 1),
                  expenses = 0.asExpenses(withTransactions = 0)
                )
              )
            ),
            "2020-11-12" -> TransactionBreakdown.ForPeriod(
              income = 0.asIncome(withTransactions = 0),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "other-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-11-13" -> TransactionBreakdown.ForPeriod(
              income = 0.asIncome(withTransactions = 0),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-11-14" -> TransactionBreakdown.ForPeriod(
              income = 75.asIncome(withTransactions = 1),
              expenses = 0.asExpenses(withTransactions = 0),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 75.asIncome(withTransactions = 1),
                  expenses = 0.asExpenses(withTransactions = 0)
                )
              )
            )
          )
        ),
        "USD" -> TransactionBreakdown.ForCurrency(
          periods = Map(
            "2020-10-21" -> TransactionBreakdown.ForPeriod(
              income = 0.asIncome(withTransactions = 0),
              expenses = 100.asExpenses(withTransactions = 1),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 0.asIncome(withTransactions = 0),
                  expenses = 100.asExpenses(withTransactions = 1)
                )
              )
            ),
            "2020-12-24" -> TransactionBreakdown.ForPeriod(
              income = 42.asIncome(withTransactions = 1),
              expenses = 0.asExpenses(withTransactions = 0),
              categories = Map(
                "test-category" -> TransactionBreakdown.ForCategory(
                  income = 42.asIncome(withTransactions = 1),
                  expenses = 0.asExpenses(withTransactions = 0)
                )
              )
            )
          )
        )
      )
    )

    actual should be(expected)
  }

  private val debit = Transaction(
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

  private val credit = Transaction(
    id = Transaction.Id.generate(),
    externalId = "test-id",
    `type` = Transaction.Type.Credit,
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

  private val transactions = Seq(
    debit.copy(amount = 100, date = LocalDate.of(2019, 1, 1)),
    credit.copy(amount = 50, date = LocalDate.of(2019, 1, 2)),
    debit.copy(amount = 100, date = LocalDate.of(2020, 10, 20)),
    debit.copy(currency = "USD", amount = 100, date = LocalDate.of(2020, 10, 21)),
    debit.copy(amount = 100, date = LocalDate.of(2020, 10, 22)),
    debit.copy(category = "other-category", amount = 100, date = LocalDate.of(2020, 11, 12)),
    debit.copy(amount = 100, date = LocalDate.of(2020, 11, 13)),
    credit.copy(amount = 150, date = LocalDate.of(2020, 10, 23)),
    credit.copy(amount = 75, date = LocalDate.of(2020, 11, 14)),
    credit.copy(currency = "USD", amount = 42, date = LocalDate.of(2020, 12, 24))
  )

  implicit class ExtendedInt(value: Int) {
    def asIncome(withTransactions: Int): TransactionBreakdown.Income =
      TransactionBreakdown.Income(value = value, transactions = withTransactions)

    def asExpenses(withTransactions: Int): TransactionBreakdown.Expenses =
      TransactionBreakdown.Expenses(value = value, transactions = withTransactions)
  }
}
