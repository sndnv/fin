package fin.server.api.requests

import fin.server.model.Account

import java.time.Instant

final case class CreateAccount(
  externalId: String,
  name: String,
  description: String
) {
  def toAccount: Account = {
    val now = Instant.now()

    Account(
      id = 0,
      externalId = externalId,
      name = name,
      description = description,
      created = now,
      updated = now,
      removed = None
    )
  }
}
