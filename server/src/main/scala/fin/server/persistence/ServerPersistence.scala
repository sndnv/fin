package fin.server.persistence

import fin.server.persistence.accounts.AccountStore
import fin.server.persistence.categories.CategoryMappingStore
import fin.server.persistence.forecasts.ForecastStore
import fin.server.persistence.transactions.TransactionStore

trait ServerPersistence {
  def accounts: AccountStore
  def transactions: TransactionStore
  def forecasts: ForecastStore
  def categoryMappings: CategoryMappingStore
}
