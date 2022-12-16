package fin.server.model

import java.time.Instant

final case class Account(
  id: Account.Id,
  externalId: String,
  name: String,
  description: String,
  created: Instant,
  updated: Instant,
  removed: Option[Instant]
)

object Account {
  type Id = Int
}
