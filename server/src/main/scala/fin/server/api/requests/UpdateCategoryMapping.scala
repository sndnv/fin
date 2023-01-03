package fin.server.api.requests

import fin.server.model.CategoryMapping

import java.time.Instant

final case class UpdateCategoryMapping(
  condition: CategoryMapping.Condition,
  matcher: String,
  category: String
) {
  def toCategoryMapping(existing: CategoryMapping): CategoryMapping =
    existing.copy(
      condition = condition,
      matcher = matcher,
      category = category,
      updated = Instant.now()
    )
}
