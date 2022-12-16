package fin.server.security.authenticators

import akka.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import fin.server.security.CurrentUser
import fin.server.security.exceptions.AuthenticationFailure
import fin.server.security.jwt.JwtAuthenticator

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class DefaultUserAuthenticator(
  underlying: JwtAuthenticator
)(implicit ec: ExecutionContext)
    extends UserAuthenticator {
  override def authenticate(credentials: HttpCredentials): Future[CurrentUser] =
    credentials match {
      case OAuth2BearerToken(token) =>
        for {
          claims <- underlying.authenticate(token)
          subject <- Future.fromTry(Try(claims.getSubject))
        } yield {
          CurrentUser(subject)
        }

      case _ =>
        Future.failed(AuthenticationFailure(s"Unsupported user credentials provided: [${credentials.scheme()}]"))
    }
}

object DefaultUserAuthenticator {
  def apply(
    underlying: JwtAuthenticator
  )(implicit ec: ExecutionContext): DefaultUserAuthenticator =
    new DefaultUserAuthenticator(underlying = underlying)
}
