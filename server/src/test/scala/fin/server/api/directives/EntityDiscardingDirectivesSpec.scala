package fin.server.api.directives

import fin.server.UnitSpec
import org.apache.pekko.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import org.apache.pekko.http.scaladsl.server.Directives
import org.apache.pekko.http.scaladsl.testkit.ScalatestRouteTest
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString

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
