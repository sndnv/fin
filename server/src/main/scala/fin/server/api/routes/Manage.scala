package fin.server.api.routes

import akka.http.scaladsl.marshalling.{Marshaller, ToEntityMarshaller}
import akka.http.scaladsl.model.{DateTime, MediaTypes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import fin.server.service.ServiceMode
import play.twirl.api.Appendable

class Manage(config: Manage.Config)(implicit mode: ServiceMode) {
  import Manage._

  private val startTime = DateTime.now

  def routes: Route =
    concat(
      pathEndOrSingleSlash { completeWithHtml(html.home.render()) },
      path("accounts") { completeWithHtml(html.accounts.render()) },
      path("transactions") { completeWithHtml(html.transactions.render()) },
      path("forecasts") { completeWithHtml(html.forecasts.render()) },
      path("categories") { completeWithHtml(html.categories.render()) },
      pathPrefix("login") {
        concat(
          pathEndOrSingleSlash { completeWithHtml(html.login.render(processCallback = false)) },
          path("callback") { completeWithHtml(html.login.render(processCallback = true)) }
        )
      },
      mode match {
        case ServiceMode.Development(resources) => pathPrefix("static") { getFromDirectory(resources) }
        case ServiceMode.Production             => pathPrefix("static") { getFromResourceDirectory("static") }
      },
      path("config" / "ui.js") { conditional(startTime) { completeWithJs(js.config_ui.render(config.ui)) } }
    )
}

object Manage {
  def apply(config: Config)(implicit mode: ServiceMode): Manage =
    new Manage(config = config)

  final case class Config(
    ui: Config.Ui
  )

  object Config {
    def apply(config: com.typesafe.config.Config): Config = Config(
      ui = Ui(config = config.getConfig("ui"))
    )

    final case class Ui(
      authorizationEndpoint: String,
      tokenEndpoint: String,
      authentication: Ui.Authentication,
      cookies: Ui.Cookies
    )

    object Ui {
      def apply(config: com.typesafe.config.Config): Ui = Ui(
        authorizationEndpoint = config.getString("authorization-endpoint"),
        tokenEndpoint = config.getString("token-endpoint"),
        authentication = Ui.Authentication(config.getConfig("authentication")),
        cookies = Ui.Cookies(config.getConfig("cookies"))
      )

      final case class Authentication(
        clientId: String,
        redirectUri: String,
        scope: String,
        stateSize: Int,
        codeVerifierSize: Int
      )

      object Authentication {
        def apply(config: com.typesafe.config.Config): Authentication =
          Authentication(
            clientId = config.getString("client-id"),
            redirectUri = config.getString("redirect-uri"),
            scope = config.getString("scope"),
            stateSize = config.getInt("state-size"),
            codeVerifierSize = config.getInt("code-verifier-size")
          )
      }

      final case class Cookies(
        authenticationToken: String,
        codeVerifier: String,
        state: String,
        secure: Boolean,
        expirationTolerance: Int
      )

      object Cookies {
        def apply(config: com.typesafe.config.Config): Cookies = Cookies(
          authenticationToken = config.getString("authentication-token"),
          codeVerifier = config.getString("code-verifier"),
          state = config.getString("state"),
          secure = config.getBoolean("secure"),
          expirationTolerance = config.getInt("expiration-tolerance")
        )
      }
    }
  }

  private def completeWithHtml[T](v: => Appendable[T]): StandardRoute = {
    implicit def htmlMarshaller: ToEntityMarshaller[Appendable[T]] =
      Marshaller.StringMarshaller.wrap(MediaTypes.`text/html`)(_.toString)

    complete(v)
  }

  private def completeWithJs[T](v: => Appendable[T]): StandardRoute = {
    implicit def htmlMarshaller: ToEntityMarshaller[Appendable[T]] =
      Marshaller.StringMarshaller.wrap(MediaTypes.`application/javascript`)(_.toString)

    complete(v)
  }
}
