package fin.server.api.directives

import fin.server.api.Metrics
import fin.server.telemetry.TelemetryContext
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.apache.pekko.http.scaladsl.model.{HttpHeader, Uri}
import org.apache.pekko.http.scaladsl.server.Directives.{extractRequest, mapResponse}
import org.apache.pekko.http.scaladsl.server.{Directive, Directive0}
import org.slf4j.Logger

import java.util.UUID

trait LoggingDirectives {
  protected def log: Logger
  protected def telemetry: TelemetryContext

  private val metrics = telemetry.metrics[Metrics.Endpoint]

  def withLoggedRequestAndResponse: Directive0 = Directive { inner =>
    extractRequest { request =>
      val requestId = UUID.randomUUID().toString
      val requestStart = System.currentTimeMillis()

      metrics.recordRequest(request)

      log.debug(
        "Received [{}] request for [{}] with ID [{}], query parameters [{}] and headers [{}]",
        request.method.value,
        request.uri.withQuery(Uri.Query.Empty).toString(),
        requestId,
        renderQueryParameters(request.uri),
        renderHeaders(request.headers)
      )

      mapResponse { response =>
        metrics.recordResponse(requestStart, request, response)

        log.debugN(
          "Responding to [{}] request for [{}] with ID [{}] and query parameters [{}]: [{}] with headers [{}]",
          request.method.value,
          request.uri.withQuery(Uri.Query.Empty).toString(),
          requestId,
          renderQueryParameters(request.uri),
          response.status.value,
          renderHeaders(response.headers)
        )

        response
      }(inner(()))
    }
  }

  private def renderQueryParameters(uri: Uri): String =
    uri.query().toMap.keys.mkString(", ")

  private def renderHeaders(headers: Seq[HttpHeader]): String =
    if (headers.nonEmpty) {
      headers.map(_.toString()).mkString("\n\t", "\n\t", "\n")
    } else {
      "none"
    }
}
