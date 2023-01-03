package fin.server.api.routes

import fin.server.persistence.ServerPersistence
import org.slf4j.Logger

final case class RoutesContext(
  persistence: ServerPersistence,
  log: Logger
)

object RoutesContext {
  def collect(
    persistence: ServerPersistence
  )(implicit log: Logger): RoutesContext =
    RoutesContext(persistence = persistence, log = log)
}
