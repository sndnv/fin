package fin.server.api.handlers

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete => compl, entity, get}
import akka.http.scaladsl.server.{RejectionHandler, Route}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec
import fin.server.api.MessageResponse
import org.slf4j.LoggerFactory
import play.api.libs.json.JsArray

class RejectionSpec extends UnitSpec with ScalatestRouteTest {
  "Rejection handler" should "reject requests with invalid entities" in {
    import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats.messageResponseFormat

    implicit val handler: RejectionHandler = Rejection.create(log)

    val route = Route.seal(
      get {
        entity(as[JsArray]) { _ =>
          compl(StatusCodes.OK)
        }
      }
    )

    Get().withEntity(HttpEntity(ContentTypes.`application/json`, "{}")) ~> route ~> check {
      status should be(StatusCodes.BadRequest)
      entityAs[MessageResponse].message should startWith("Provided data is invalid or malformed")
    }
  }

  private val log = LoggerFactory.getLogger(this.getClass.getName)
}
