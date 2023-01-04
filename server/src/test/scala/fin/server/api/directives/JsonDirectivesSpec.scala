package fin.server.api.directives

import akka.http.scaladsl.model.{ContentTypes, Multipart, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec
import fin.server.api.Formats
import fin.server.api.requests.CreateAccount

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class JsonDirectivesSpec extends UnitSpec with ScalatestRouteTest {
  "JsonDirectives" should "support parsing JSON file uploads" in {
    val directive = new JsonDirectives {}
    val receivedAccounts: AtomicInteger = new AtomicInteger(0)

    val route = directive.jsonUpload[CreateAccount](Formats.createAccountFormat) { accounts =>
      Directives.post {
        receivedAccounts.set(accounts.length)
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/json/accounts.json")
    )

    Post("/", content) ~> route ~> check {
      status should be(StatusCodes.OK)
      receivedAccounts.get should be(3)
    }
  }

  they should "fail to process invalid file uploads" in {
    val directive = new JsonDirectives {}
    val receivedAccounts: AtomicInteger = new AtomicInteger(0)

    val route = directive.jsonUpload[CreateAccount](Formats.createAccountFormat) { accounts =>
      Directives.post {
        receivedAccounts.set(accounts.length)
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/test-static/test.txt")
    )

    Post("/", content) ~> route ~> check {
      status should be(StatusCodes.BadRequest)
      receivedAccounts.get should be(0)
    }
  }
}
