package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.Account

import java.time.Instant

class UpdateAccountSpec extends UnitSpec {
  "An UpdateAccount request" should "support updating existing accounts" in {
    val request = UpdateAccount(
      externalId = "other-id",
      name = "other-name",
      description = "other-description"
    )

    val now = Instant.now()

    val original = Account(
      id = 123,
      externalId = "test-id",
      name = "test-name",
      description = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    val actual = request.toAccount(existing = original)

    val expected = original.copy(
      externalId = request.externalId,
      name = request.name,
      description = request.description,
      updated = actual.updated
    )

    actual should be(expected)
    actual.created should not be actual.updated
  }
}
