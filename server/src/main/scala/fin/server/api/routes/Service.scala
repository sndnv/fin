package fin.server.api.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

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
