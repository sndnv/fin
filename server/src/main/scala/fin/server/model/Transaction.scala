package fin.server.model

import java.time.{Instant, LocalDate}
import java.util.UUID

final case class Transaction(
  id: Transaction.Id,
  externalId: String,
  `type`: Transaction.Type,
  from: Account.Id,
  to: Option[Account.Id],
  amount: BigDecimal,
  currency: String,
  date: LocalDate,
  category: String,
  notes: Option[String],
  created: Instant,
  updated: Instant,
  removed: Option[Instant]
)

object Transaction {
  type Id = UUID

  object Id {
    def generate(): Id = UUID.randomUUID()
  }

  sealed trait Type
  object Type {
    case object Debit extends Type
    case object Credit extends Type
  }
}
