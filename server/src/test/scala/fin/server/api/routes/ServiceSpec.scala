package fin.server.api.routes

import fin.server.UnitSpec
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest

class ServiceSpec extends UnitSpec with ScalatestRouteTest {
  "Service routes" should "provide a health-check route" in {
    Get("/health") ~> new Service().routes ~> check {
      status should be(StatusCodes.OK)
    }
  }
}
