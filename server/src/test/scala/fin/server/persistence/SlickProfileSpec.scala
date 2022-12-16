package fin.server.persistence

import fin.server.UnitSpec
import slick.jdbc._

class SlickProfileSpec extends UnitSpec {
  "A SlickProfile" should "provide profiles based on profile names" in {
    val profiles = Map(
      "DB2Profile" -> DB2Profile,
      "DerbyProfile" -> DerbyProfile,
      "H2Profile" -> H2Profile,
      "HsqldbProfile" -> HsqldbProfile,
      "MySQLProfile" -> MySQLProfile,
      "OracleProfile" -> OracleProfile,
      "PostgresProfile" -> PostgresProfile,
      "SQLiteProfile" -> SQLiteProfile,
      "SQLServerProfile" -> SQLServerProfile
    )

    profiles.foreach { case (name, profile) =>
      SlickProfile(profile = name) should be(profile)
    }

    succeed
  }
}
