package fin.server.imports

import fin.server.model.{Account, Transaction}
import generated._
import org.slf4j.Logger

import java.time.{Instant, LocalDate}
import java.util.UUID

object FromCamt053 {
  def transactions(
    forAccount: Account.Id,
    fromStatements: Seq[AccountStatement2],
    withTargetAccountMapping: String => Option[Account.Id]
  )(implicit log: Logger): Seq[Transaction] = {
    val now = Instant.now()

    fromStatements.flatMap { statement =>
      statement.Ntry.flatMap {
        case entry if entry.Sts == BOOK =>
          Some(
            transaction(
              forAccount = forAccount,
              fromEntry = entry,
              withImportTime = now,
              withTargetAccountMapping = withTargetAccountMapping
            )
          )

        case entry =>
          log.info("Skipping entry [{}]; status was [{}]", entry.AcctSvcrRef.getOrElse("unknown"), entry.Sts)
          None
      }
    }
  }

  def transaction(
    forAccount: Account.Id,
    fromEntry: ReportEntry2,
    withImportTime: Instant,
    withTargetAccountMapping: String => Option[Account.Id]
  )(implicit log: Logger): Transaction = {
    val transactionType: Transaction.Type = fromEntry.CdtDbtInd match {
      case CRDT => Transaction.Type.Credit
      case DBIT => Transaction.Type.Debit
    }

    val date = fromEntry.ValDt match {
      case Some(dtc) =>
        LocalDate.parse(dtc.dateanddatetimechoiceoption.value.toString)

      case None =>
        log.warn("Failed to extract timestamp from entry [{}]", fromEntry.AcctSvcrRef.getOrElse("unknown"))
        LocalDate.now()
    }

    val target = fromEntry.NtryDtls.headOption.flatMap(_.TxDtls.headOption) match {
      case Some(details) =>
        details.RltdPties
          .flatMap {
            case parties if fromEntry.CdtDbtInd == CRDT => parties.DbtrAcct
            case parties                                => parties.CdtrAcct
          }
          .map(_.Id.accountidentification4choiceoption.value.toString)

      case _ =>
        fromEntry.AddtlNtryInf.map(_.trim)
    }

    val id = UUID.randomUUID()

    Transaction(
      id = id,
      externalId = fromEntry.AcctSvcrRef.getOrElse(id.toString),
      `type` = transactionType,
      from = forAccount,
      to = target.flatMap(withTargetAccountMapping.apply),
      amount = fromEntry.Amt.value,
      currency = fromEntry.Amt.Ccy,
      date = date,
      category = "imported-camt",
      notes = fromEntry.AddtlNtryInf.map(_.trim),
      created = withImportTime,
      updated = withImportTime,
      removed = None
    )
  }
}
