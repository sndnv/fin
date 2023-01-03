package fin.server.persistence.categories

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import fin.server.UnitSpec
import fin.server.model.CategoryMapping
import org.scalatest.BeforeAndAfterAll
import slick.jdbc.H2Profile

import java.time.Instant

class DefaultCategoryMappingStoreSpec extends UnitSpec with BeforeAndAfterAll {
  "A DefaultCategoryMappingStore" should "store and retrieve category mappings" in {
    val store = new DefaultCategoryMappingStore(
      tableName = "DefaultCategoryMappingStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val categoryMapping = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher",
      category = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    val expectedId = 1

    for {
      _ <- store.init()
      _ <- store.create(categoryMapping)
      existing <- store.get(categoryMapping = expectedId)
      _ <- store.drop()
    } yield {
      existing should be(Some(categoryMapping.copy(id = expectedId)))
    }
  }

  it should "update existing category mappings" in {
    val store = new DefaultCategoryMappingStore(
      tableName = "DefaultCategoryMappingStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val categoryMapping = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher",
      category = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    val expectedId = 1

    for {
      _ <- store.init()
      _ <- store.create(categoryMapping)
      existing <- store.get(categoryMapping = expectedId).map {
        case Some(value) => value
        case None        => fail("Expected an existing CategoryMapping but none was found")
      }
      _ <- store.update(existing.copy(matcher = "other-matcher"))
      updated <- store.get(categoryMapping = expectedId)
      _ <- store.drop()
    } yield {
      existing should be(categoryMapping.copy(id = expectedId))
      updated should be(Some(categoryMapping.copy(id = expectedId, matcher = "other-matcher")))
    }
  }

  it should "fail to retrieve missing category mappings" in {
    val store = new DefaultCategoryMappingStore(
      tableName = "DefaultCategoryMappingStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    for {
      _ <- store.init()
      missing <- store.get(categoryMapping = 1)
      _ <- store.drop()
    } yield {
      missing should be(None)
    }
  }

  it should "retrieve all category mappings" in {
    val store = new DefaultCategoryMappingStore(
      tableName = "DefaultCategoryMappingStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val categoryMapping = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher",
      category = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(categoryMapping)
      _ <- store.create(categoryMapping.copy(matcher = "test-matcher-2"))
      _ <- store.create(categoryMapping.copy(matcher = "test-matcher-3"))
      available <- store.available()
      all <- store.all()
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          categoryMapping.copy(id = 1),
          categoryMapping.copy(id = 2, matcher = "test-matcher-2"),
          categoryMapping.copy(id = 3, matcher = "test-matcher-3")
        )
      )

      all should be(
        Seq(
          categoryMapping.copy(id = 1),
          categoryMapping.copy(id = 2, matcher = "test-matcher-2"),
          categoryMapping.copy(id = 3, matcher = "test-matcher-3")
        )
      )
    }
  }

  it should "mark category mappings as removed" in {
    val store = new DefaultCategoryMappingStore(
      tableName = "DefaultCategoryMappingStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val categoryMapping = CategoryMapping(
      id = 0,
      condition = CategoryMapping.Condition.Equals,
      matcher = "test-matcher",
      category = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(categoryMapping)
      _ <- store.create(categoryMapping.copy(matcher = "test-matcher-2"))
      _ <- store.create(categoryMapping.copy(matcher = "test-matcher-3"))
      _ <- store.delete(categoryMapping = 2)
      available <- store.available()
      all <- store.all()
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          categoryMapping.copy(id = 1),
          categoryMapping.copy(id = 3, matcher = "test-matcher-3")
        )
      )

      all should be(
        Seq(
          categoryMapping.copy(id = 1),
          categoryMapping.copy(id = 2, matcher = "test-matcher-2", removed = all.find(_.id == 2).flatMap(_.removed)),
          categoryMapping.copy(id = 3, matcher = "test-matcher-3")
        )
      )
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultCategoryMappingStoreSpec"
  )

  private val h2db =
    H2Profile.api.Database.forURL(url = "jdbc:h2:mem:DefaultCategoryMappingStoreSpec", keepAliveConnection = true)

  override protected def afterAll(): Unit = {
    h2db.close()
    typedSystem.terminate()
  }
}
