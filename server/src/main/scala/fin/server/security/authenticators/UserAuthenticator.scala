package fin.server.security.authenticators

import fin.server.security.CurrentUser
import org.apache.pekko.http.scaladsl.model.headers.HttpCredentials

import scala.concurrent.Future

trait UserAuthenticator {
  def authenticate(credentials: HttpCredentials): Future[CurrentUser]
}
