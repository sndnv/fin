package fin.server.security

import fin.server.UnitSpec

class CurrentUserSpec extends UnitSpec {
  "A CurrentUser" should "render itself as a string" in {
    CurrentUser(subject = "test-subject").toString should be("test-subject")
  }
}
