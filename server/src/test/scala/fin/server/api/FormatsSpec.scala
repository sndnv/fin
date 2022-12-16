package fin.server.api

import fin.server.UnitSpec
import fin.server.api.Formats._
import fin.server.model.Transaction
import play.api.libs.json.Json

class FormatsSpec extends UnitSpec {
  "Formats" should "convert rule operations to/from JSON" in {
    val operations = Map(
      Transaction.Type.Debit -> """"debit"""",
      Transaction.Type.Credit -> """"credit""""
    )

    operations.foreach { case (operation, json) =>
      transactionTypeFormat.writes(operation).toString should be(json)
      transactionTypeFormat.reads(Json.parse(json)).asOpt should be(Some(operation))
    }

    succeed
  }
}
