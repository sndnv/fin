package fin.server.api.directives

import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import fin.server.UnitSpec
import org.slf4j.{Logger, LoggerFactory}
import scalaxb.XMLStandardTypes

class XmlDirectivesSpec extends UnitSpec with ScalatestRouteTest {
  "XmlDirectives" should "support parsing upload types" in {
    XmlDirectives.UploadType("xml") should be(XmlDirectives.UploadType.Xml)
    XmlDirectives.UploadType("archive") should be(XmlDirectives.UploadType.Archive)
    an[IllegalArgumentException] should be thrownBy XmlDirectives.UploadType("other")
  }

  they should "support parsing XML payloads" in {
    val directive = new XmlDirectives {}

    val route = directive.xmlEntity[String](XMLStandardTypes.__StringXMLFormat, log) { _ =>
      Directives.post {
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><test>test-value</test>"""
    Post("/").withEntity(HttpEntity(ContentTypes.`text/xml(UTF-8)`, content)) ~> route ~> check {
      status should be(StatusCodes.OK)
    }
  }

  they should "fail to parse invalid XML payloads" in {
    val directive = new XmlDirectives {}

    val route = directive.xmlEntity[Int](XMLStandardTypes.__IntXMLFormat, log) { _ =>
      Directives.post {
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><test>test-value</test>"""
    Post("/").withEntity(HttpEntity(ContentTypes.`text/xml(UTF-8)`, content)) ~> route ~> check {
      status should be(StatusCodes.UnprocessableEntity)
    }
  }

  they should "support parsing XML file uploads" in {
    val directive = new XmlDirectives {}
    val receivedDocuments: AtomicInteger = new AtomicInteger(0)

    val route = directive.xmlUpload[generated.Document](XmlDirectives.UploadType.Xml)(implicitly, log) { docs =>
      Directives.post {
        receivedDocuments.set(docs.length)
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/camt/sample_gs_camt.053.xml")
    )

    Post("/", content) ~> route ~> check {
      status should be(StatusCodes.OK)
      receivedDocuments.get should be(1)
    }
  }

  they should "fail to parse invalid XML file uploads" in {
    val directive = new XmlDirectives {}
    val receivedDocuments: AtomicInteger = new AtomicInteger(0)

    val route = directive.xmlUpload[generated.Document](XmlDirectives.UploadType.Xml)(implicitly, log) { docs =>
      Directives.post {
        receivedDocuments.set(docs.length)
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/camt/sample_invalid.xml")
    )

    Post("/", content) ~> route ~> check {
      status should be(StatusCodes.UnprocessableEntity)
      receivedDocuments.get should be(0)
    }
  }

  they should "support parsing XML archive uploads" in {
    val directive = new XmlDirectives {}
    val receivedDocuments: AtomicInteger = new AtomicInteger(0)

    val route = directive.xmlUpload[generated.Document](XmlDirectives.UploadType.Archive)(implicitly, log) { docs =>
      Directives.post {
        receivedDocuments.set(docs.length)
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/camt/sample_gs_camt.053.zip")
    )

    Post("/", content) ~> route ~> check {
      status should be(StatusCodes.OK)
      receivedDocuments.get should be(3)
    }
  }

  they should "fail to parse invalid XML archive uploads" in {
    val directive = new XmlDirectives {}
    val receivedDocuments: AtomicInteger = new AtomicInteger(0)

    val route = directive.xmlUpload[generated.Document](XmlDirectives.UploadType.Archive)(implicitly, log) { docs =>
      Directives.post {
        receivedDocuments.set(docs.length)
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/camt/sample_invalid.zip")
    )

    Post("/", content) ~> route ~> check {
      status should be(StatusCodes.UnprocessableEntity)
      receivedDocuments.get should be(0)
    }
  }

  they should "fail to parse empty XML archive uploads" in {
    val directive = new XmlDirectives {}
    val receivedDocuments: AtomicInteger = new AtomicInteger(0)

    val route = directive.xmlUpload[generated.Document](XmlDirectives.UploadType.Archive)(implicitly, log) { docs =>
      Directives.post {
        receivedDocuments.set(docs.length)
        Directives.complete(StatusCodes.OK)
      }
    }

    val content = Multipart.FormData.fromFile(
      name = "file",
      contentType = ContentTypes.`application/octet-stream`,
      file = new File("server/src/test/resources/camt/sample_empty.zip")
    )

    Post("/", content) ~> route ~> check {
      status should be(StatusCodes.UnprocessableEntity)
      receivedDocuments.get should be(0)
    }
  }

  private implicit val log: Logger = LoggerFactory.getLogger(this.getClass.getName)
}
