package fin.server.api.handlers

import fin.server.api.responses.MessageResponse
import fin.server.security.exceptions.AuthorizationFailure
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.ExceptionHandler
import org.slf4j.Logger

import scala.util.control.NonFatal

object Sanitizing {
  def create(log: Logger): ExceptionHandler =
    ExceptionHandler {
      case e: AuthorizationFailure =>
        extractRequestEntity { entity =>
          extractActorSystem { implicit system =>
            log.errorN("User authorization failed: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
            onSuccess(entity.discardBytes().future()) { _ =>
              complete(StatusCodes.Forbidden)
            }
          }
        }

      case NonFatal(e) =>
        extractRequestEntity { entity =>
          extractActorSystem { implicit system =>
            import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
            import fin.server.api.Formats._

            val failureReference = java.util.UUID.randomUUID()

            log.error(
              "Unhandled exception encountered: [{} - {}]; failure reference is [{}]",
              e.getClass.getSimpleName,
              e.getMessage,
              failureReference,
              e
            )

            onSuccess(entity.discardBytes().future()) { _ =>
              complete(
                StatusCodes.InternalServerError,
                MessageResponse(s"Failed to process request; failure reference is [${failureReference.toString}]")
              )
            }
          }
        }
    }
}
