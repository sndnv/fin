package fin.server.service

import com.typesafe.config.{Config, ConfigFactory}
import fin.server.UnitSpec
import fin.server.model.Account
import fin.server.security.mocks.{MockIdentityEndpoint, MockJwtGenerators}
import fin.server.security.tls.EndpointContext
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.marshalling.{Marshal, Marshaller}
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.OAuth2BearerToken
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import org.jose4j.jwk.JsonWebKey
import org.scalatest.concurrent.Eventually

import javax.net.ssl.TrustManagerFactory
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class ServiceSpec extends UnitSpec with ScalatestRouteTest with Eventually {
  "Service" should "handle API and metrics requests" in {
    implicit val trustedContext: EndpointContext = createTrustedContext()

    val jwtPort = 19999
    val jwtEndpoint = new MockIdentityEndpoint(port = jwtPort)

    val defaultJwk = jwtEndpoint.jwks.getJsonWebKeys.asScala.find(_.getKeyType != "oct") match {
      case Some(jwk) => jwk
      case None      => fail("Expected at least one mock JWK but none were found")
    }

    jwtEndpoint.start()

    val service = new Service {}
    val interface = "localhost"

    val servicePort = 29999
    val serviceUrl = s"https://$interface:$servicePort"

    val metricsPort = 39999
    val metricsUrl = s"http://$interface:$metricsPort"

    val serverPersistence = eventually {
      service.state match {
        case Service.State.Started => service.serverPersistence
        case state                 => fail(s"Unexpected service state encountered: [$state]")
      }
    }

    for {
      jwt <- getJwt(signatureKey = defaultJwk)
      accounts <- serverPersistence.accounts.available()
      apiAccounts <- getAccounts(serviceUrl, jwt)
      metrics <- getMetrics(metricsUrl)
      _ <- serverPersistence.drop()
      _ = service.stop()
      _ = jwtEndpoint.stop()
    } yield {
      accounts should be(apiAccounts)

      metrics.filter(_.startsWith(Service.Telemetry.Instrumentation)) should not be empty
      metrics.filter(_.startsWith("jvm")) should not be empty
      metrics.filter(_.startsWith("process")) should not be empty
    }
  }

  private def getJwt(
    signatureKey: JsonWebKey
  ): Future[String] = {
    val authConfig = defaultConfig.getConfig("fin.server.authenticators.users")

    val jwt = MockJwtGenerators.generateJwt(
      issuer = authConfig.getString("issuer"),
      audience = authConfig.getString("audience"),
      subject = "test-subject",
      signatureKey = signatureKey
    )

    Future.successful(jwt)
  }

  private def getAccounts(
    serviceUrl: String,
    jwt: String
  )(implicit trustedContext: EndpointContext): Future[Seq[Account]] = {
    import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
    import fin.server.api.Formats._

    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = s"$serviceUrl/accounts"
        ).addCredentials(OAuth2BearerToken(token = jwt)),
        connectionContext = trustedContext.connection
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[Seq[Account]]
        case response                                   => fail(s"Unexpected response received: [$response]")
      }
  }

  private def getMetrics(
    metricsUrl: String
  ): Future[Seq[String]] =
    Http()
      .singleRequest(
        request = HttpRequest(
          method = HttpMethods.GET,
          uri = metricsUrl
        )
      )
      .flatMap {
        case HttpResponse(StatusCodes.OK, _, entity, _) => Unmarshal(entity).to[String]
        case response                                   => fail(s"Unexpected response received: [$response]")
      }
      .map { result =>
        result.split("\n").toSeq.filterNot(_.startsWith("#"))
      }

  private def createTrustedContext(): EndpointContext = {
    val config = defaultConfig.getConfig("fin.test.server.service.api.context")
    val storeConfig = EndpointContext.StoreConfig(config.getConfig("keystore"))

    val keyStore = EndpointContext.loadStore(storeConfig)

    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    factory.init(keyStore)

    EndpointContext(
      config = EndpointContext.Config(protocol = config.getString("protocol"), storeConfig = Right(storeConfig)),
      keyManagers = None,
      trustManagers = Option(factory.getTrustManagers)
    )
  }

  private val defaultConfig: Config = ConfigFactory.load()

  private implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(
    Behaviors.setup(_ => SpawnProtocol()): Behavior[SpawnProtocol.Command],
    "ServiceSpec"
  )

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(5.seconds, 250.milliseconds)

  import scala.language.implicitConversions

  implicit def requestToEntity[T](request: T)(implicit m: Marshaller[T, RequestEntity]): RequestEntity =
    Marshal(request).to[RequestEntity].await
}
