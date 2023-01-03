package fin.server.api.responses

final case class CategoryMappingsApplicationResult(
  categoryMappingsFound: Int,
  transactionsFound: Int,
  transactionsUpdated: Int
)
