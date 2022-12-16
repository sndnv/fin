package fin.server.model

import fin.server.UnitSpec

import java.time.{LocalDate, Month}

class PeriodSpec extends UnitSpec {
  "A Period" should "be parsable from string" in {
    Period(value = "1000-01") should be(Period(year = 1000, month = Month.JANUARY))

    Period(value = "2020-01") should be(Period(year = 2020, month = Month.JANUARY))
    Period(value = "2020-02") should be(Period(year = 2020, month = Month.FEBRUARY))
    Period(value = "2020-03") should be(Period(year = 2020, month = Month.MARCH))
    Period(value = "2020-04") should be(Period(year = 2020, month = Month.APRIL))
    Period(value = "2020-05") should be(Period(year = 2020, month = Month.MAY))
    Period(value = "2020-06") should be(Period(year = 2020, month = Month.JUNE))
    Period(value = "2020-07") should be(Period(year = 2020, month = Month.JULY))
    Period(value = "2020-08") should be(Period(year = 2020, month = Month.AUGUST))
    Period(value = "2020-09") should be(Period(year = 2020, month = Month.SEPTEMBER))
    Period(value = "2020-10") should be(Period(year = 2020, month = Month.OCTOBER))
    Period(value = "2020-11") should be(Period(year = 2020, month = Month.NOVEMBER))
    Period(value = "2020-12") should be(Period(year = 2020, month = Month.DECEMBER))

    Period(value = "2020-1") should be(Period(year = 2020, month = Month.JANUARY))
    Period(value = "2020-2") should be(Period(year = 2020, month = Month.FEBRUARY))
    Period(value = "2020-3") should be(Period(year = 2020, month = Month.MARCH))
    Period(value = "2020-4") should be(Period(year = 2020, month = Month.APRIL))
    Period(value = "2020-5") should be(Period(year = 2020, month = Month.MAY))
    Period(value = "2020-6") should be(Period(year = 2020, month = Month.JUNE))
    Period(value = "2020-7") should be(Period(year = 2020, month = Month.JULY))
    Period(value = "2020-8") should be(Period(year = 2020, month = Month.AUGUST))
    Period(value = "2020-9") should be(Period(year = 2020, month = Month.SEPTEMBER))

    Period(value = "9999-12") should be(Period(year = 9999, month = Month.DECEMBER))
  }

  it should "fail to be parsed from an invalid string" in {
    an[IllegalArgumentException] should be thrownBy Period(value = "999-12")
    an[IllegalArgumentException] should be thrownBy Period(value = "2020-0")
    an[IllegalArgumentException] should be thrownBy Period(value = "2020-13")
    an[IllegalArgumentException] should be thrownBy Period(value = "10000-12")
    an[IllegalArgumentException] should be thrownBy Period(value = "2020-")
    an[IllegalArgumentException] should be thrownBy Period(value = "2020")
    an[IllegalArgumentException] should be thrownBy Period(value = "")
  }

  it should "provide the current period" in {
    val now = LocalDate.now()
    val current = Period.current

    current.year should be(now.getYear)
    current.month should be(now.getMonth)
  }

  it should "render itself as a string" in {
    Period(year = 2020, month = Month.JANUARY).toString should be("2020-01")
    Period(year = 2020, month = Month.FEBRUARY).toString should be("2020-02")
    Period(year = 2020, month = Month.MARCH).toString should be("2020-03")
    Period(year = 2020, month = Month.APRIL).toString should be("2020-04")
    Period(year = 2020, month = Month.MAY).toString should be("2020-05")
    Period(year = 2020, month = Month.JUNE).toString should be("2020-06")
    Period(year = 2020, month = Month.JULY).toString should be("2020-07")
    Period(year = 2020, month = Month.AUGUST).toString should be("2020-08")
    Period(year = 2020, month = Month.SEPTEMBER).toString should be("2020-09")
    Period(year = 2020, month = Month.OCTOBER).toString should be("2020-10")
    Period(year = 2020, month = Month.NOVEMBER).toString should be("2020-11")
    Period(year = 2020, month = Month.DECEMBER).toString should be("2020-12")
  }
}
