package fin.server.api.directives

import org.apache.pekko.http.scaladsl.model.MediaTypes._
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.{Directive, Directive1}
import org.apache.pekko.http.scaladsl.unmarshalling._
import org.apache.pekko.util.ByteString
import org.slf4j.Logger
import scalaxb.XMLFormat

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import scala.util.{Failure, Success, Try, Using}
import scala.xml.{NodeSeq, XML}

trait XmlDirectives {
  def xmlEntity[T](implicit format: XMLFormat[T], log: Logger): Directive1[T] = Directive { inner =>
    import XmlDirectives._

    entity(as[NodeSeq]) { xml =>
      fromXml(xml)(format, log) { parsed =>
        inner(Tuple1(parsed))
      }
    }
  }

  def xmlUpload[T](
    uploadType: XmlDirectives.UploadType
  )(implicit format: XMLFormat[T], log: Logger): Directive1[Seq[T]] = Directive { inner =>
    fileUpload(fieldName = "file") { case (_, source) =>
      extractActorSystem { implicit system =>
        onSuccess(source.runFold(ByteString.empty)(_ concat _)) { bytes =>
          uploadType match {
            case XmlDirectives.UploadType.Xml =>
              fromXml(xml = XmlDirectives.parse(bytes))(format, log) { parsed =>
                inner(Tuple1(Seq(parsed)))
              }

            case XmlDirectives.UploadType.Archive =>
              Using(new ZipInputStream(new ByteArrayInputStream(bytes.toArrayUnsafe()))) { input =>
                LazyList
                  .continually(Option(input.getNextEntry))
                  .map(_.map(_ => XmlDirectives.parse(input.readAllBytes())))
                  .takeWhile(_.isDefined)
                  .flatten
                  .map(scalaxb.fromXML[T](_))
                  .toList
              } match {
                case Success(parsed) if parsed.nonEmpty =>
                  log.debug("Received [{}] entries from XML payload", parsed.length)
                  inner(Tuple1(parsed))

                case Success(_) =>
                  log.warn("Failed to process empty XML payload")
                  complete(StatusCodes.UnprocessableEntity)

                case Failure(e) =>
                  log.warn("Failed to parse XML payload: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
                  complete(StatusCodes.UnprocessableEntity)
              }
          }
        }
      }
    }
  }

  private def fromXml[T](
    xml: => NodeSeq
  )(implicit format: XMLFormat[T], log: Logger): Directive1[T] = Directive { inner =>
    Try(scalaxb.fromXML[T](xml)) match {
      case Success(result) =>
        inner(Tuple1(result))

      case Failure(e) =>
        log.warn("Failed to parse XML payload: [{} - {}]", e.getClass.getSimpleName, e.getMessage)
        complete(StatusCodes.UnprocessableEntity)
    }
  }
}

object XmlDirectives {
  final val SupportedMediaTypes: Seq[MediaType.WithOpenCharset] = Seq(`application/xml`, `text/xml`)

  implicit def unmarshaller: FromEntityUnmarshaller[NodeSeq] =
    Unmarshaller.byteStringUnmarshaller
      .forContentTypes(ranges = SupportedMediaTypes.map(ContentTypeRange.apply): _*)
      .map(parse)

  def parse(bytes: ByteString): NodeSeq =
    parse(bytes.toArrayUnsafe())

  def parse(bytes: Array[Byte]): NodeSeq =
    XML.load(new ByteArrayInputStream(bytes))

  implicit val stringToUploadType: Unmarshaller[String, UploadType] = Unmarshaller.strict(UploadType.apply)

  sealed trait UploadType
  object UploadType {
    case object Xml extends UploadType
    case object Archive extends UploadType

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    def apply(value: String): UploadType =
      value.trim.toLowerCase match {
        case "xml"     => Xml
        case "archive" => Archive
        case _         => throw new IllegalArgumentException(s"Unsupported upload type provided: [$value]")
      }
  }
}
