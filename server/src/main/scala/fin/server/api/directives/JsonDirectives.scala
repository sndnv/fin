package fin.server.api.directives

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{Directive, Directive1}
import org.apache.pekko.util.ByteString
import play.api.libs.json._

import scala.util.{Failure, Success, Try}

trait JsonDirectives {
  def jsonUpload[T](implicit reads: Reads[T]): Directive1[Seq[T]] = Directive { inner =>
    fileUpload(fieldName = "file") { case (_, source) =>
      extractActorSystem { implicit system =>
        onSuccess(source.runFold(ByteString.empty)(_ concat _)) { bytes =>
          Try(Json.parse(bytes.toArrayUnsafe()).as[Seq[T]]) match {
            case Success(result) => inner(Tuple1(result))
            case Failure(_)      => complete(StatusCodes.BadRequest)
          }
        }
      }
    }
  }
}
