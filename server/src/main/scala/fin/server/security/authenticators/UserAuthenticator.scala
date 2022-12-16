package fin.server.security.authenticators

import akka.http.scaladsl.model.headers.HttpCredentials
import fin.server.security.CurrentUser

import scala.concurrent.Future

trait UserAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[CurrentUser]
}
