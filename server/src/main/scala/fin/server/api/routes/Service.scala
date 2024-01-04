package fin.server.api.routes

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route

class Service() {
  def routes: Route =
    concat(
      path("health") {
        complete(StatusCodes.OK)
      }
    )
}

object Service {
  def apply(): Service = new Service()
}
