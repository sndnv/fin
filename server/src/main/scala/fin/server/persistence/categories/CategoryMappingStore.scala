package fin.server.persistence.categories

import fin.server.model.CategoryMapping
import org.apache.pekko.Done

import scala.concurrent.Future

trait CategoryMappingStore {
  def create(categoryMapping: CategoryMapping): Future[Done]
  def update(categoryMapping: CategoryMapping): Future[Done]
  def delete(categoryMapping: CategoryMapping.Id): Future[Boolean]
  def get(categoryMapping: CategoryMapping.Id): Future[Option[CategoryMapping]]
  def available(): Future[Seq[CategoryMapping]]
  def all(): Future[Seq[CategoryMapping]]

  def init(): Future[Done]
  def drop(): Future[Done]
}
