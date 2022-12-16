package fin.server.api.routes

import fin.server.api.directives.EntityDiscardingDirectives
import org.slf4j.Logger

trait ApiRoutes extends EntityDiscardingDirectives {
  def log(implicit ctx: RoutesContext): Logger = ctx.log
}
