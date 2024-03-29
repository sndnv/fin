package fin.server.api.handlers

import fin.server.UnitSpec
import fin.server.api.responses.MessageResponse
import fin.server.security.exceptions.AuthorizationFailure
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{ExceptionHandler, Route}
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.slf4j.LoggerFactory

class SanitizingSpec extends UnitSpec with ScalatestRouteTest {
  "Sanitizing handlers" should "handle authorization failures reported by routes" in {
    implicit val handler: ExceptionHandler = Sanitizing.create(log)

    val route = Route.seal(
      get {
        throw AuthorizationFailure("test failure")
      }
    )

    Get() ~> route ~> check {
      status should be(StatusCodes.Forbidden)
    }
  }

  it should "handle generic failures reported by routes" in {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    implicit val handler: ExceptionHandler = Sanitizing.create(log)

    val route = Route.seal(
      get {
        throw new RuntimeException("test failure")
      }
    )

    Get() ~> route ~> check {
      status should be(StatusCodes.InternalServerError)
      entityAs[MessageResponse].message should startWith("Failed to process request; failure reference is")
    }
  }

  private val log = LoggerFactory.getLogger(this.getClass.getName)
}
