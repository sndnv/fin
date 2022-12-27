package fin.server.api.requests

import fin.server.UnitSpec
import fin.server.model.Account

class CreateAccountSpec extends UnitSpec {
  "A CreateAccount request" should "support creating an account" in {
    val request = CreateAccount(
      externalId = "test-id",
      name = "test-name",
      description = "test-description"
    )

    val actual = request.toAccount

    val expected = Account(
      id = 0,
      externalId = request.externalId,
      name = request.name,
      description = request.description,
      created = actual.created,
      updated = actual.updated,
      removed = None
    )

    actual should be(expected)
  }
}
