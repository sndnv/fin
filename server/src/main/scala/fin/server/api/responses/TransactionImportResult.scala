package fin.server.api.responses

final case class TransactionImportResult(
  provided: TransactionImportResult.Provided,
  imported: TransactionImportResult.Imported
)

object TransactionImportResult {
  final case class Provided(
    documents: Int,
    statements: Int,
    entries: Int
  )

  final case class Imported(
    successful: Int,
    existing: Int
  )
}
