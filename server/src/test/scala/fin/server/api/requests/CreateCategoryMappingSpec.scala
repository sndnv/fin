package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.CategoryMapping

class CreateCategoryMappingSpec extends UnitSpec {
  "A CreateCategoryMapping request" should "support creating a category mapping" in {
    val request = CreateCategoryMapping(
      condition = CategoryMapping.Condition.Matches,
      matcher = "test-matcher",
      category = "test-category"
    )

    val actual = request.toCategoryMapping

    val expected = CategoryMapping(
      id = 0,
      condition = request.condition,
      matcher = request.matcher,
      category = request.category,
      created = actual.created,
      updated = actual.updated,
      removed = None
    )

    actual should be(expected)
  }
}
