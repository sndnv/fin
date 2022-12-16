package fin.server.api.directives

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Directive0}

trait EntityDiscardingDirectives {
  def discardEntity: Directive0 =
    Directive { inner =>
      extractRequestEntity { entity =>
        extractActorSystem { implicit system =>
          onSuccess(entity.discardBytes().future()) { _ =>
            inner(())
          }
        }
      }
    }
}
