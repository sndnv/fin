package fin.server.security.mocks

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import fin.server.security.tls.EndpointContext
import org.jose4j.jwk.JsonWebKeySet

import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success}

class MockIdentityEndpoint(
  port: Int,
  rsaKeysCount: Int = 3,
  ecKeysCount: Int = 3,
  secretKeysCount: Int = 3,
  context: Option[EndpointContext] = None,
  token: Option[String] = None
)(implicit system: ActorSystem[Nothing]) {
  import system.executionContext

  private val scheme = context match {
    case Some(_) => "https"
    case None    => "http"
  }

  private val bindingRef: AtomicReference[Future[Http.ServerBinding]] = new AtomicReference(
    Future.failed(new IllegalStateException("Server not started"))
  )

  private val paths: ConcurrentHashMap[String, Int] = new ConcurrentHashMap(
    Map(
      "/valid/jwks.json" -> 0,
      "/invalid/jwks.json" -> 0,
      "/oauth/token" -> 0
    ).asJava
  )

  val routes: Route = concat(
    path("valid" / "jwks.json") {
      paths.compute("/valid/jwks.json", (_, current) => current + 1)
      complete(HttpEntity(ContentTypes.`application/json`, jwks.toJson))
    },
    path("invalid" / "jwks.json") {
      paths.compute("/invalid/jwks.json", (_, current) => current + 1)
      complete(StatusCodes.InternalServerError)
    },
    path("oauth" / "token") {
      paths.compute("/oauth/token", (_, current) => current + 1)
      complete(
        HttpEntity(
          ContentTypes.`application/json`,
          s"""
             |{
             |  "access_token": "${token.getOrElse("none")}",
             |  "expires_in": 90
             |}""".stripMargin
        )
      )
    }
  )

  def start(): Unit = {
    val server = {
      val builder = Http().newServerAt(interface = "localhost", port = port)

      context match {
        case Some(httpsContext) => builder.enableHttps(httpsContext.connection)
        case None               => builder
      }
    }

    bindingRef.set(server.bindFlow(handlerFlow = routes))
  }

  def stop(): Unit =
    bindingRef.get().flatMap(_.unbind()).onComplete {
      case Success(_) => () // do nothing
      case Failure(e) => system.log.error("Failed to stop endpoint: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
    }

  def url: String = s"$scheme://localhost:$port"

  def count(path: String): Int =
    paths.getOrDefault(path, 0)

  val jwks: JsonWebKeySet = MockJwksGenerators.generateKeySet(rsaKeysCount, ecKeysCount, secretKeysCount)

  val keys: Map[String, PublicKey] =
    jwks.getJsonWebKeys.asScala
      .map(c => (c.getKeyId, c.getKey))
      .collect { case (keyId: String, key: PublicKey) =>
        (keyId, key)
      }
      .toMap
}
