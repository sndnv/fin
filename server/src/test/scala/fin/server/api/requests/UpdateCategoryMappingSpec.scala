package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.CategoryMapping

import java.time.Instant

class UpdateCategoryMappingSpec extends UnitSpec {
  "An UpdateCategoryMapping request" should "support updating existing category mappings" in {
    val request = UpdateCategoryMapping(
      condition = CategoryMapping.Condition.Equals,
      matcher = "other-matcher",
      category = "other-category"
    )

    val now = Instant.now()

    val original = CategoryMapping(
      id = 123,
      condition = CategoryMapping.Condition.Matches,
      matcher = "test-matcher",
      category = "test-category",
      created = now,
      updated = now,
      removed = None
    )

    val actual = request.toCategoryMapping(existing = original)

    val expected = original.copy(
      condition = request.condition,
      matcher = request.matcher,
      category = request.category,
      updated = actual.updated
    )

    actual should be(expected)
    actual.created should not be actual.updated
  }
}
