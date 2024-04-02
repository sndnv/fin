package fin.server.persistence

import org.apache.pekko.Done

import scala.concurrent.Future

trait Store {
  def tableName(): String
  def migrations(): Seq[Migration]
  def init(): Future[Done]
  def drop(): Future[Done]
}
