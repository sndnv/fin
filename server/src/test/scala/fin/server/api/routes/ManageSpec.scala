package fin.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec
import fin.server.service.ServiceMode

class ManageSpec extends UnitSpec with ScalatestRouteTest {
  "Manage routes" should "load their config" in {
    val actual = Manage.Config(
      config = com.typesafe.config.ConfigFactory
        .load()
        .getConfig("fin.test.server.service.endpoint.manage")
    )

    actual should be(config)
  }

  they should "provide a home page" in {
    val fixtures = new TestFixtures {}

    Get("/") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val response = responseAs[String]

      response should include("<!doctype html>")
      response should include("<h4 id=\"overview-title\">Overview</h4>")
    }
  }

  they should "provide an accounts page" in {
    val fixtures = new TestFixtures {}

    Get("/accounts") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val response = responseAs[String]

      response should include("<!doctype html>")
      response should include("<h4>Accounts</h4>")
    }
  }

  they should "provide a transactions page" in {
    val fixtures = new TestFixtures {}

    Get("/transactions") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val response = responseAs[String]

      response should include("<!doctype html>")
      response should include("<h4>Transactions</h4>")
    }
  }

  they should "provide a forecasts page" in {
    val fixtures = new TestFixtures {}

    Get("/forecasts") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val response = responseAs[String]

      response should include("<!doctype html>")
      response should include("<h4>Forecasts</h4>")
    }
  }

  they should "provide a reports page" in {
    val fixtures = new TestFixtures {}

    Get("/reports") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val response = responseAs[String]

      response should include("<!doctype html>")
      response should include("<h4>Reports</h4>")
    }
  }

  they should "provide a categories page" in {
    val fixtures = new TestFixtures {}

    Get("/categories") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val response = responseAs[String]

      response should include("<!doctype html>")
      response should include("<h4>Category Mappings</h4>")
    }
  }

  they should "provide a login page" in {
    val fixtures = new TestFixtures {}

    Get("/login") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val response = responseAs[String]

      response should include("<!doctype html>")
      response should include("<script>login();</script>")
    }
  }

  they should "provide a login callback page" in {
    val fixtures = new TestFixtures {}

    Get("/login/callback") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)
      val response = responseAs[String]

      response should include("<!doctype html>")
      response should include("<script src=\"/manage/static/login.js\"></script>")
    }
  }

  they should "provide static resources (production mode)" in {
    val fixtures = new TestFixtures {
      override implicit lazy val serviceMode: ServiceMode = ServiceMode.Production
    }

    Get("/static/test.txt") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }

    Get("/static/logo.svg") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }

    Get("/static/accounts.css") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/accounts.js") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/common.css") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/common.js") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/forecasts.css") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/forecasts.js") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/categories.css") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/categories.js") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/home.css") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/home.js") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/transactions.css") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
    Get("/static/transactions.js") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }
  }

  they should "provide static resources (development mode)" in {
    val fixtures = new TestFixtures {
      override implicit lazy val serviceMode: ServiceMode = ServiceMode.Development(
        resourcesPath = "server/src/test/resources/test-static"
      )
    }

    Get("/static/test.txt") ~> fixtures.routes ~> check { status should be(StatusCodes.OK) }

    Get("/static/logo.svg") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }

    Get("/static/accounts.css") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/accounts.js") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/common.css") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/common.js") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/forecasts.css") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/forecasts.js") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/categories.css") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/categories.js") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/home.css") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/home.js") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/transactions.css") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
    Get("/static/transactions.js") ~> fixtures.routes ~> check { status should be(StatusCodes.NotFound) }
  }

  they should "provide UI config" in {
    val fixtures = new TestFixtures {}

    Get("/config/ui.js") ~> fixtures.routes ~> check {
      status should be(StatusCodes.OK)

      responseAs[String] should be(
        """
          |const FIN_SERVER_UI_CONFIG = {
          |    authorization_endpoint: 'a',
          |    token_endpoint: 'b',
          |    authentication: {
          |        state_size: 1,
          |        code_verifier_size: 2,
          |        client_id: 'c',
          |        redirect_uri: 'd',
          |        scope: 'e',
          |    },
          |    cookies: {
          |        authentication_token: 'f',
          |        code_verifier: 'g',
          |        state: 'h',
          |        secure: true,
          |        expiration_tolerance: 3,
          |    },
          |}""".stripMargin
      )
    }
  }

  private trait TestFixtures {
    implicit lazy val serviceMode: ServiceMode = ServiceMode.Production
    lazy val routes: Route = Route.seal(new Manage(config).routes)
  }

  private val config = Manage.Config(
    ui = Manage.Config.Ui(
      authorizationEndpoint = "a",
      tokenEndpoint = "b",
      authentication = Manage.Config.Ui.Authentication(
        clientId = "c",
        redirectUri = "d",
        scope = "e",
        stateSize = 1,
        codeVerifierSize = 2
      ),
      cookies = Manage.Config.Ui.Cookies(
        authenticationToken = "f",
        codeVerifier = "g",
        state = "h",
        secure = true,
        expirationTolerance = 3
      )
    )
  )
}
