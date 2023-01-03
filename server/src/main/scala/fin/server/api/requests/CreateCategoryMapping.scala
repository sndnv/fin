package fin.server.api.requests

import fin.server.model.CategoryMapping

import java.time.Instant

final case class CreateCategoryMapping(
  condition: CategoryMapping.Condition,
  matcher: String,
  category: String
) {
  def toCategoryMapping: CategoryMapping = {
    val now = Instant.now()

    CategoryMapping(
      id = 0,
      condition = condition,
      matcher = matcher,
      category = category,
      created = now,
      updated = now,
      removed = None
    )
  }
}
