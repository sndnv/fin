package fin.server.api.requests

import fin.server.model.Account

import java.time.Instant

final case class UpdateAccount(
  externalId: String,
  name: String,
  description: String
) {
  def toAccount(existing: Account): Account =
    existing.copy(
      externalId = externalId,
      name = name,
      description = description,
      updated = Instant.now()
    )
}
