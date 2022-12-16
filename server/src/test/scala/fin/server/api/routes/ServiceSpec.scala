package fin.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec

class ServiceSpec extends UnitSpec with ScalatestRouteTest {
  "Service routes" should "provide a health-check route" in {
    Get("/health") ~> new Service().routes ~> check {
      status should be(StatusCodes.OK)
    }
  }
}
