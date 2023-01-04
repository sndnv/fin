package fin.server.persistence.mocks

import akka.Done
import fin.server.model.CategoryMapping
import fin.server.persistence.categories.CategoryMappingStore

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class MockCategoryMappingStore extends CategoryMappingStore {
  private val store = new ConcurrentHashMap[Int, CategoryMapping]()

  override def create(categoryMapping: CategoryMapping): Future[Done] = {
    store.put(
      if (categoryMapping.id != 0) { categoryMapping.id }
      else { store.size() + 1 },
      categoryMapping
    )
    Future.successful(Done)
  }

  override def update(categoryMapping: CategoryMapping): Future[Done] =
    if (store.containsKey(categoryMapping.id)) {
      store.put(categoryMapping.id, categoryMapping)
      Future.successful(Done)
    } else {
      Future.failed(new IllegalAccessException(s"CategoryMapping [${categoryMapping.id}] does not exist; update failed"))
    }

  override def delete(categoryMapping: CategoryMapping.Id): Future[Boolean] =
    Option(store.get(categoryMapping)) match {
      case Some(existing) =>
        store.put(existing.id, existing.copy(removed = Some(Instant.now())))
        Future.successful(true)

      case None =>
        Future.successful(false)
    }

  override def get(categoryMapping: CategoryMapping.Id): Future[Option[CategoryMapping]] =
    Future.successful(Option(store.get(categoryMapping)))

  override def available(): Future[Seq[CategoryMapping]] =
    Future.successful(store.values().asScala.toSeq.filter(_.removed.isEmpty))

  override def all(): Future[Seq[CategoryMapping]] =
    Future.successful(store.values().asScala.toSeq)

  override def init(): Future[Done] = Future.successful(Done)

  override def drop(): Future[Done] = {
    store.clear()
    Future.successful(Done)
  }
}

object MockCategoryMappingStore {
  def apply(): MockCategoryMappingStore = new MockCategoryMappingStore()
}
