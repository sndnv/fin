package fin.server.model

import java.time.temporal.TemporalAdjusters
import java.time.{LocalDate, Month}
import scala.util.matching.Regex

final case class Period(year: Int, month: Month) {
  override def toString: String = f"$year-${month.getValue}%02d"

  def atFirstDayOfMonth: LocalDate = LocalDate.of(year, month, 1)
  def atLastDayOfMonth: LocalDate = atFirstDayOfMonth.`with`(TemporalAdjusters.lastDayOfMonth())
}

object Period {
  val PeriodRegex: Regex = """^(\d{4})-(0?[1-9]|1[012])$""".r

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def apply(value: String): Period =
    value match {
      case PeriodRegex(year, month) => Period(year.toInt, Month.of(month.toInt))
      case _                        => throw new IllegalArgumentException(s"Invalid period value provided: [$value]")
    }

  def current: Period = {
    val now = LocalDate.now()
    Period(year = now.getYear, month = now.getMonth)
  }
}
