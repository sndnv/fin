package fin.server.model

import fin.server.UnitSpec

import java.time.Instant

class CategoryMappingSpec extends UnitSpec {
  "A CategoryMapping Condition" should "be convertible to/from a string" in {
    CategoryMapping.Condition.StartsWith.toString should be("starts_with")
    CategoryMapping.Condition.EndsWith.toString should be("ends_with")
    CategoryMapping.Condition.Contains.toString should be("contains")
    CategoryMapping.Condition.Equals.toString should be("equals")
    CategoryMapping.Condition.Matches.toString should be("matches")

    CategoryMapping.Condition("starts_with") should be(CategoryMapping.Condition.StartsWith)
    CategoryMapping.Condition("ends_with") should be(CategoryMapping.Condition.EndsWith)
    CategoryMapping.Condition("contains") should be(CategoryMapping.Condition.Contains)
    CategoryMapping.Condition("equals") should be(CategoryMapping.Condition.Equals)
    CategoryMapping.Condition("matches") should be(CategoryMapping.Condition.Matches)

    an[IllegalArgumentException] should be thrownBy CategoryMapping.Condition("other")
  }

  "A CategoryMapping" should "support matching values" in {
    val now = Instant.now()

    val mappingStartsWith = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.StartsWith,
      matcher = "matcher",
      category = "test-category-1",
      created = now,
      updated = now,
      removed = None
    )

    val mappingEndsWith = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.EndsWith,
      matcher = "matcher",
      category = "test-category-2",
      created = now,
      updated = now,
      removed = None
    )

    val mappingContains = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Contains,
      matcher = "matcher",
      category = "test-category-3",
      created = now,
      updated = now,
      removed = None
    )

    val mappingEquals = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher-a",
      category = "test-category-4",
      created = now,
      updated = now,
      removed = None
    )

    val mappingMatches = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Matches,
      matcher = "\\w-matcher-\\d{2}",
      category = "test-category-5",
      created = now,
      updated = now,
      removed = None
    )

    mappingStartsWith.matches(value = None) should be(false)
    mappingStartsWith.matches(value = Some("")) should be(false)
    mappingStartsWith.matches(value = Some("matcher-a")) should be(true)
    mappingStartsWith.matches(value = Some("a-matcher")) should be(false)
    mappingStartsWith.matches(value = Some("a-matcher-b")) should be(false)
    mappingStartsWith.matches(value = Some("test-matcher-a")) should be(false)
    mappingStartsWith.matches(value = Some("b-matcher-12")) should be(false)

    mappingEndsWith.matches(value = None) should be(false)
    mappingEndsWith.matches(value = Some("")) should be(false)
    mappingEndsWith.matches(value = Some("matcher-a")) should be(false)
    mappingEndsWith.matches(value = Some("a-matcher")) should be(true)
    mappingEndsWith.matches(value = Some("a-matcher-b")) should be(false)
    mappingEndsWith.matches(value = Some("test-matcher-a")) should be(false)
    mappingEndsWith.matches(value = Some("b-matcher-12")) should be(false)

    mappingContains.matches(value = None) should be(false)
    mappingContains.matches(value = Some("")) should be(false)
    mappingContains.matches(value = Some("matcher-a")) should be(true)
    mappingContains.matches(value = Some("a-matcher")) should be(true)
    mappingContains.matches(value = Some("a-matcher-b")) should be(true)
    mappingContains.matches(value = Some("test-matcher-a")) should be(true)
    mappingContains.matches(value = Some("b-matcher-12")) should be(true)

    mappingEquals.matches(value = None) should be(false)
    mappingEquals.matches(value = Some("")) should be(false)
    mappingEquals.matches(value = Some("matcher-a")) should be(false)
    mappingEquals.matches(value = Some("a-matcher")) should be(false)
    mappingEquals.matches(value = Some("a-matcher-b")) should be(false)
    mappingEquals.matches(value = Some("test-matcher-a")) should be(true)
    mappingEquals.matches(value = Some("b-matcher-12")) should be(false)

    mappingMatches.matches(value = None) should be(false)
    mappingMatches.matches(value = Some("")) should be(false)
    mappingMatches.matches(value = Some("matcher-a")) should be(false)
    mappingMatches.matches(value = Some("a-matcher")) should be(false)
    mappingMatches.matches(value = Some("a-matcher-b")) should be(false)
    mappingMatches.matches(value = Some("test-matcher-a")) should be(false)
    mappingMatches.matches(value = Some("b-matcher-12")) should be(true)
  }
}
