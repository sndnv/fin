package fin.server.imports

import fin.server.UnitSpec
import fin.server.model.Transaction
import generated.Document
import org.slf4j.{Logger, LoggerFactory}

import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.xml.XML

class FromCamt053Spec extends UnitSpec {
  "FromCamt053" should "support converting CAT.053 data to transactions" in {
    val document = scalaxb.fromXML[Document](XML.loadFile("server/src/test/resources/camt/sample_gs_camt.053.xml"))

    val expected = Seq(
      transaction(
        `type` = Transaction.Type.Debit,
        amount = BigDecimal("2.50"),
        date = LocalDate.parse("2021-08-03"),
        notes = Some("ABCDEF")
      ),
      transaction(
        `type` = Transaction.Type.Credit,
        amount = BigDecimal("15.98"),
        date = LocalDate.parse("2021-08-03")
      ),
      transaction(
        `type` = Transaction.Type.Debit,
        amount = BigDecimal("2.50"),
        date = LocalDate.parse("2021-08-03")
      ),
      transaction(
        `type` = Transaction.Type.Debit,
        amount = BigDecimal("72.50"),
        date = LocalDate.parse("2021-08-03"),
        to = None
      ),
      transaction(
        `type` = Transaction.Type.Credit,
        amount = BigDecimal("0.79"),
        date = LocalDate.parse("2021-08-03"),
        to = None
      ),
      transaction(
        `type` = Transaction.Type.Debit,
        amount = BigDecimal("1.40"),
        date = LocalDate.parse("2021-08-03"),
        to = None
      ),
      transaction(
        `type` = Transaction.Type.Debit,
        amount = BigDecimal("16.01"),
        date = LocalDate.parse("2021-08-03")
      ),
      transaction(
        `type` = Transaction.Type.Debit,
        amount = BigDecimal("1.11"),
        date = LocalDate.parse("2021-08-03")
      ),
      transaction(
        externalId = Some("GI2121500000214"),
        `type` = Transaction.Type.Credit,
        amount = BigDecimal("16.01"),
        date = LocalDate.parse("2021-08-03"),
        notes = Some("XYZ123")
      ),
      transaction(
        `type` = Transaction.Type.Debit,
        amount = BigDecimal("36.80"),
        date = LocalDate.now(),
        to = None
      )
    )

    val actual = FromCamt053
      .transactions(
        forAccount = 0,
        fromStatements = document.BkToCstmrStmt.Stmt,
        withTargetAccountMapping = _ => Some(1)
      )
      .map {
        case t if !t.externalId.contains("-") => t.copy(id = id, created = now, updated = now)
        case t                                => t.copy(id = id, externalId = id.toString, created = now, updated = now)
      }

    actual should be(expected)
  }

  private val id = UUID.randomUUID()
  private val now = Instant.now()

  private def transaction(
    `type`: Transaction.Type,
    amount: BigDecimal,
    date: LocalDate,
    notes: Option[String] = None,
    externalId: Option[String] = None,
    to: Option[Int] = Some(1)
  ): Transaction = Transaction(
    id = id,
    externalId = externalId.getOrElse(id.toString),
    `type` = `type`,
    from = 0,
    to = to,
    amount = amount,
    currency = "USD",
    date = date,
    category = "imported-camt",
    notes = notes,
    created = now,
    updated = now,
    removed = None
  )

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
