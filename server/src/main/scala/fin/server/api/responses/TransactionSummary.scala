package fin.server.api.responses

import fin.server.model.Transaction
import fin.server.model.Transaction.Type

final case class TransactionSummary(
  currencies: Map[String, TransactionSummary.ForCurrency]
) {
  def withTransaction(transaction: Transaction): TransactionSummary = {
    val updated = transaction.`type` match {
      case Type.Debit =>
        currencies.get(transaction.currency) match {
          case Some(existing) => existing.copy(expenses = existing.expenses + transaction.amount)
          case None           => TransactionSummary.ForCurrency(income = 0, expenses = transaction.amount)
        }

      case Type.Credit =>
        currencies.get(transaction.currency) match {
          case Some(existing) => existing.copy(income = existing.income + transaction.amount)
          case None           => TransactionSummary.ForCurrency(income = transaction.amount, expenses = 0)
        }
    }

    copy(currencies = currencies + (transaction.currency -> updated))
  }
}

object TransactionSummary {
  final case class ForCurrency(income: BigDecimal, expenses: BigDecimal)

  def empty: TransactionSummary =
    TransactionSummary(currencies = Map.empty)
}
