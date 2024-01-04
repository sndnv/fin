package fin.server.security.mocks

import fin.server.security.CurrentUser
import fin.server.security.authenticators.UserAuthenticator
import fin.server.security.exceptions.AuthenticationFailure
import org.apache.pekko.http.scaladsl.model.headers.{BasicHttpCredentials, HttpCredentials}

import scala.concurrent.Future

class MockUserAuthenticator(expectedUser: String, expectedPassword: String) extends UserAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[CurrentUser] =
    credentials match {
      case BasicHttpCredentials(`expectedUser`, `expectedPassword`) =>
        Future.successful(CurrentUser(subject = expectedUser))

      case _ =>
        Future.failed(AuthenticationFailure("Invalid credentials supplied"))
    }
}
