package fin.server.api.handlers

import fin.server.api.responses.MessageResponse
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{RejectionHandler, ValidationRejection}
import org.slf4j.Logger

object Rejection {
  def create(log: Logger): RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle { case ValidationRejection(rejectionMessage, _) =>
        import com.github.pjfanning.pekkohttpplayjson.PlayJsonSupport._
        import fin.server.api.Formats.messageResponseFormat

        extractRequest { request =>
          extractActorSystem { implicit system =>
            val message = s"Provided data is invalid or malformed: [$rejectionMessage]"

            log.warnN(
              "[{}] request for [{}] rejected: [{}]",
              request.method.value,
              request.uri.path.toString,
              message
            )

            onSuccess(request.entity.discardBytes().future()) { _ =>
              complete(
                StatusCodes.BadRequest,
                MessageResponse(message)
              )
            }
          }
        }
      }
      .result()
      .seal
}
