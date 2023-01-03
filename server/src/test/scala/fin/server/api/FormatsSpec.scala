package fin.server.api

import fin.server.UnitSpec
import fin.server.api.Formats._
import fin.server.model.{CategoryMapping, Period, Transaction}
import play.api.libs.json.Json

class FormatsSpec extends UnitSpec {
  "Formats" should "convert periods to/from JSON" in {
    val periods = Map(
      Period("2020-01") -> """"2020-01"""",
      Period("2020-02") -> """"2020-02"""",
      Period("2020-03") -> """"2020-03"""",
      Period("2020-04") -> """"2020-04"""",
      Period("2020-05") -> """"2020-05"""",
      Period("2020-06") -> """"2020-06"""",
      Period("2020-07") -> """"2020-07"""",
      Period("2020-08") -> """"2020-08"""",
      Period("2020-09") -> """"2020-09"""",
      Period("2020-10") -> """"2020-10"""",
      Period("2020-11") -> """"2020-11"""",
      Period("2020-12") -> """"2020-12""""
    )

    periods.foreach { case (period, json) =>
      periodFormat.writes(period).toString should be(json)
      periodFormat.reads(Json.parse(json)).asOpt should be(Some(period))
    }

    succeed
  }

  they should "convert transaction types to/from JSON" in {
    val transactionTypes = Map(
      Transaction.Type.Debit -> """"debit"""",
      Transaction.Type.Credit -> """"credit""""
    )

    transactionTypes.foreach { case (transactionType, json) =>
      transactionTypeFormat.writes(transactionType).toString should be(json)
      transactionTypeFormat.reads(Json.parse(json)).asOpt should be(Some(transactionType))
    }

    succeed
  }

  they should "convert category mapping conditions to/from JSON" in {
    val mappingConditions = Map(
      CategoryMapping.Condition.StartsWith -> """"starts_with"""",
      CategoryMapping.Condition.EndsWith -> """"ends_with"""",
      CategoryMapping.Condition.Contains -> """"contains"""",
      CategoryMapping.Condition.Equals -> """"equals"""",
      CategoryMapping.Condition.Matches -> """"matches""""
    )

    mappingConditions.foreach { case (condition, json) =>
      categoryMappingConditionFormat.writes(condition).toString should be(json)
      categoryMappingConditionFormat.reads(Json.parse(json)).asOpt should be(Some(condition))
    }

    succeed
  }
}
