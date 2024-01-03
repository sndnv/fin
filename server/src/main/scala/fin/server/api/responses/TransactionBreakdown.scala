package fin.server.api.responses

import fin.server.model.Transaction

final case class TransactionBreakdown(
  currencies: Map[String, TransactionBreakdown.ForCurrency]
)

object TransactionBreakdown {
  final case class ForCurrency(
    periods: Map[String, ForPeriod]
  )

  final case class ForPeriod(
    income: Income,
    expenses: Expenses,
    categories: Map[String, ForCategory]
  )

  final case class ForCategory(income: Income, expenses: Expenses)

  final case class Income(value: BigDecimal, transactions: Int) {
    def +(income: BigDecimal): Income =
      copy(value = value + income, transactions = transactions + 1)

    def +(income: Income): Income =
      copy(value = value + income.value, transactions = transactions + income.transactions)
  }

  final case class Expenses(value: BigDecimal, transactions: Int) {
    def +(expense: BigDecimal): Expenses =
      copy(value = value + expense, transactions = transactions + 1)

    def +(expenses: Expenses): Expenses =
      copy(value = value + expenses.value, transactions = transactions + expenses.transactions)
  }

  def apply(breakdownType: BreakdownType, transactions: Seq[Transaction]): TransactionBreakdown = {
    val grouped: Map[BreakdownKey, (Income, Expenses)] = (
      breakdownType match {
        case BreakdownType.ByYear  => transactions.groupBy(BreakdownKey.forYear)
        case BreakdownType.ByMonth => transactions.groupBy(BreakdownKey.forMonth)
        case BreakdownType.ByDay   => transactions.groupBy(BreakdownKey.forDay)
      }
    ).view.mapValues { groupedTransactions =>
      groupedTransactions.foldLeft((ZeroIncome, ZeroExpenses)) {
        case ((income, expenses), credit) if credit.`type` == Transaction.Type.Credit =>
          (income + credit.amount, expenses)
        case ((income, expenses), debit) =>
          (income, expenses + debit.amount)
      }
    }.toMap

    val breakdown = grouped.groupBy(_._1.currency).map { case (currency, values) =>
      val periods = values.groupBy(_._1.period).view.mapValues { forPeriod =>
        val (totalIncome, totalExpenses) = forPeriod.values.foldLeft((ZeroIncome, ZeroExpenses)) {
          case ((existingIncome, existingExpenses), (currentIncome, currentExpenses)) =>
            (existingIncome + currentIncome, existingExpenses + currentExpenses)
        }

        val categories = forPeriod.groupBy(_._1.category).view.mapValues { forCategory =>
          val (categoryIncome, categoryExpenses) = forCategory.values.foldLeft((ZeroIncome, ZeroExpenses)) {
            case ((existingIncome, existingExpenses), (currentIncome, currentExpenses)) =>
              (existingIncome + currentIncome, existingExpenses + currentExpenses)
          }

          ForCategory(income = categoryIncome, expenses = categoryExpenses)
        }

        ForPeriod(income = totalIncome, expenses = totalExpenses, categories = categories.toMap)
      }

      currency -> ForCurrency(
        periods = periods.toMap
      )
    }

    TransactionBreakdown(currencies = breakdown)
  }

  sealed trait BreakdownType
  object BreakdownType {
    case object ByYear extends BreakdownType
    case object ByMonth extends BreakdownType
    case object ByDay extends BreakdownType

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def apply(value: String): BreakdownType =
      value match {
        case "by-year"  => ByYear
        case "by-month" => ByMonth
        case "by-day"   => ByDay
        case _          => throw new IllegalArgumentException(s"Invalid breakdown type provided: [$value]")
      }
  }

  private final case class BreakdownKey(period: String, category: String, currency: String)

  private object BreakdownKey {
    def forYear(transaction: Transaction): BreakdownKey =
      BreakdownKey(
        period = transaction.date.getYear.toString,
        category = transaction.category,
        currency = transaction.currency
      )

    def forMonth(transaction: Transaction): BreakdownKey =
      BreakdownKey(
        period = f"${transaction.date.getYear}-${transaction.date.getMonthValue}%02d",
        category = transaction.category,
        currency = transaction.currency
      )

    def forDay(transaction: Transaction): BreakdownKey =
      BreakdownKey(
        period = f"${transaction.date.getYear}-${transaction.date.getMonthValue}%02d-${transaction.date.getDayOfMonth}%02d",
        category = transaction.category,
        currency = transaction.currency
      )
  }

  private val Zero: BigDecimal = BigDecimal(0)
  private val ZeroIncome: Income = Income(value = Zero, transactions = 0)
  private val ZeroExpenses: Expenses = Expenses(value = Zero, transactions = 0)
}
