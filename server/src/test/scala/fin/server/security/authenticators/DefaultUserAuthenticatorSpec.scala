package fin.server.security.authenticators

import akka.http.scaladsl.model.headers.{BasicHttpCredentials, OAuth2BearerToken}
import fin.server.UnitSpec
import fin.server.security.exceptions.AuthenticationFailure
import fin.server.security.jwt.DefaultJwtAuthenticator
import fin.server.security.keys.KeyProvider
import fin.server.security.mocks.{MockJwksGenerators, MockJwtGenerators}
import org.jose4j.jws.AlgorithmIdentifiers

import java.security.Key
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

class DefaultUserAuthenticatorSpec extends UnitSpec { test =>
  "A DefaultUserAuthenticator" should "authenticate users with valid JWTs" in {
    val expectedSubject = "test-subject"

    val authenticator = new DefaultUserAuthenticator(
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = audience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedSubject)

    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { actualUser =>
        actualUser.subject should be(expectedSubject)
      }
  }

  it should "fail to authenticate users with unexpected credentials" in {
    val authenticator = new DefaultUserAuthenticator(
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = audience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    authenticator
      .authenticate(
        credentials = BasicHttpCredentials(username = "some-username", password = "some-password")
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage should be("Unsupported user credentials provided: [Basic]")
      }
  }

  it should "fail to authenticate users with invalid JWTs" in {
    val expectedSubject = "test-subject"

    val invalidAudience = "invalid-audience"

    val authenticator = new DefaultUserAuthenticator(
      underlying = new DefaultJwtAuthenticator(
        provider = provider,
        audience = invalidAudience,
        identityClaim = "sub",
        expirationTolerance = 10.seconds
      )
    )

    val token = generateToken(subject = expectedSubject)

    authenticator
      .authenticate(
        credentials = OAuth2BearerToken(token = token)
      )
      .map { response =>
        fail(s"Unexpected response received: [$response]")
      }
      .recover { case NonFatal(e) =>
        e shouldBe an[AuthenticationFailure]
        e.getMessage.contains(s"Expected $invalidAudience as an aud value") should be(true)
      }
  }

  private val issuer = "some-issuer"
  private val audience = "some-audience"

  private val jwk = MockJwksGenerators.generateRandomRsaKey(keyId = Some("some-key"))

  private val provider = new KeyProvider {
    override def key(id: Option[String]): Future[Key] = Future.successful(jwk.getKey)
    override def issuer: String = test.issuer
    override def allowedAlgorithms: Seq[String] =
      Seq(
        AlgorithmIdentifiers.RSA_USING_SHA256,
        AlgorithmIdentifiers.RSA_USING_SHA384,
        AlgorithmIdentifiers.RSA_USING_SHA512
      )
  }

  private def generateToken(subject: String): String =
    MockJwtGenerators.generateJwt(
      issuer = issuer,
      audience = audience,
      subject = subject,
      signatureKey = jwk
    )
}
