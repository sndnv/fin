package fin.server.api

import fin.server.api.requests.{CreateAccount, CreateTransaction, UpdateAccount, UpdateTransaction}
import fin.server.api.responses.TransactionImportResult
import fin.server.model.{Account, Transaction}

object Formats {
  import play.api.libs.json._

  implicit val jsonConfig: JsonConfiguration = JsonConfiguration(JsonNaming.SnakeCase)

  implicit val messageResponseFormat: Format[MessageResponse] = Json.format[MessageResponse]

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
}
