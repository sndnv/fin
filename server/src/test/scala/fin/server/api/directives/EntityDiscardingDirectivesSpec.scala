package fin.server.api.directives

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.scaladsl.Source
import akka.util.ByteString
import fin.server.UnitSpec

import java.util.concurrent.atomic.AtomicInteger

class EntityDiscardingDirectivesSpec extends UnitSpec with ScalatestRouteTest {
  "EntityDiscardingDirectives" should "discard entities" in {
    val directive = new EntityDiscardingDirectives {}

    val route = directive.discardEntity {
      Directives.complete(StatusCodes.OK)
    }

    val counter = new AtomicInteger(0)

    val content = Source(ByteString("part-0") :: ByteString("part-1") :: ByteString("part-2") :: Nil).map { bytes =>
      counter.incrementAndGet()
      bytes
    }

    val entity = HttpEntity(ContentTypes.`application/octet-stream`, content)

    Post().withEntity(entity) ~> route ~> check {
      status should be(StatusCodes.OK)

      // by default, discarding an entity consumes the whole entity
      counter.get should be(3)
    }
  }
}
