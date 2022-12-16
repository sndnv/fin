package fin.server.api.routes

import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.transactions.TransactionStore
import org.slf4j.Logger

import scala.concurrent.ExecutionContext

final case class RoutesContext(
  accounts: AccountStore,
  transactions: TransactionStore,
  ec: ExecutionContext,
  log: Logger
)

object RoutesContext {
  def collect(
    accounts: AccountStore,
    transactions: TransactionStore
  )(implicit ec: ExecutionContext, log: Logger): RoutesContext =
    RoutesContext(
      accounts = accounts,
      transactions = transactions,
      ec = ec,
      log = log
    )
}
