package fin.server.api

import akka.http.scaladsl.unmarshalling.Unmarshaller
import fin.server.api.requests._
import fin.server.api.responses._
import fin.server.model._

object Formats {
  import play.api.libs.json._

  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val messageResponseFormat: Format[MessageResponse] = Json.format[MessageResponse]

  implicit val periodFormat: Format[Period] = Format(
    fjs = _.validate[String].map(Period.apply),
    tjs = period => Json.toJson(period.toString)
  )

  implicit val accountFormat: Format[Account] = Json.format[Account]
  implicit val createAccountFormat: Format[CreateAccount] = Json.format[CreateAccount]
  implicit val updateAccountFormat: Format[UpdateAccount] = Json.format[UpdateAccount]

  implicit val transactionTypeFormat: Format[Transaction.Type] = Format(
    fjs = _.validate[String].map {
      case "debit"  => Transaction.Type.Debit
      case "credit" => Transaction.Type.Credit
    },
    tjs = {
      case Transaction.Type.Debit  => Json.toJson("debit")
      case Transaction.Type.Credit => Json.toJson("credit")
    }
  )

  implicit val transactionFormat: Format[Transaction] = Json.format[Transaction]
  implicit val createTransactionFormat: Format[CreateTransaction] = Json.format[CreateTransaction]
  implicit val updateTransactionFormat: Format[UpdateTransaction] = Json.format[UpdateTransaction]

  implicit val transactionImportResultProvidedFormat: Format[TransactionImportResult.Provided] =
    Json.format[TransactionImportResult.Provided]
  implicit val transactionImportResultImportedFormat: Format[TransactionImportResult.Imported] =
    Json.format[TransactionImportResult.Imported]
  implicit val transactionImportResultFormat: Format[TransactionImportResult] =
    Json.format[TransactionImportResult]

  implicit val forecastFormat: Format[Forecast] = Json.format[Forecast]
  implicit val createForecastFormat: Format[CreateForecast] = Json.format[CreateForecast]
  implicit val updateForecastFormat: Format[UpdateForecast] = Json.format[UpdateForecast]

  implicit val stringToPeriod: Unmarshaller[String, Period] = Unmarshaller.strict(Period.apply)

  implicit val transactionSummaryForCurrencyFormat: Format[TransactionSummary.ForCurrency] =
    Json.format[TransactionSummary.ForCurrency]
  implicit val transactionSummaryFormat: Format[TransactionSummary] =
    Json.format[TransactionSummary]

  implicit val transactionBreakdownIncomeFormat: Format[TransactionBreakdown.Income] =
    Json.format[TransactionBreakdown.Income]
  implicit val transactionBreakdownExpensesFormat: Format[TransactionBreakdown.Expenses] =
    Json.format[TransactionBreakdown.Expenses]
  implicit val transactionBreakdownForCategoryFormat: Format[TransactionBreakdown.ForCategory] =
    Json.format[TransactionBreakdown.ForCategory]
  implicit val transactionBreakdownForPeriodFormat: Format[TransactionBreakdown.ForPeriod] =
    Json.format[TransactionBreakdown.ForPeriod]
  implicit val transactionBreakdownForCurrencyFormat: Format[TransactionBreakdown.ForCurrency] =
    Json.format[TransactionBreakdown.ForCurrency]
  implicit val transactionBreakdownFormat: Format[TransactionBreakdown] =
    Json.format[TransactionBreakdown]

  implicit val categoryMappingConditionFormat: Format[CategoryMapping.Condition] = Format(
    fjs = _.validate[String].map(CategoryMapping.Condition.apply),
    tjs = condition => Json.toJson(condition.toString)
  )

  implicit val categoryMappingFormat: Format[CategoryMapping] = Json.format[CategoryMapping]
  implicit val createCategoryMappingFormat: Format[CreateCategoryMapping] = Json.format[CreateCategoryMapping]
  implicit val updateCategoryMappingFormat: Format[UpdateCategoryMapping] = Json.format[UpdateCategoryMapping]

  implicit val applyCategoryMappingsFormat: Format[ApplyCategoryMappings] = Json.format[ApplyCategoryMappings]

  implicit val categoryMappingsApplicationResultFormat: Format[CategoryMappingsApplicationResult] =
    Json.format[CategoryMappingsApplicationResult]
}
