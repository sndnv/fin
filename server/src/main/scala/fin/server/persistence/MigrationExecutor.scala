package fin.server.persistence

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcProfile

import scala.concurrent.Future

trait MigrationExecutor {
  def execute(forStore: Store): Future[Done]
}

object MigrationExecutor {
  class Default(database: JdbcProfile#Backend#Database)(implicit system: ActorSystem[Nothing]) extends MigrationExecutor {
    import system.executionContext

    private implicit val log: Logger = LoggerFactory.getLogger(MigrationExecutor.getClass.getSimpleName)

    def execute(forStore: Store): Future[Done] = {
      val migrations = forStore.migrations().sortBy(_.version)

      log.debug(
        "Found [{}] migration(s) for [{} / {}]",
        migrations.length,
        forStore.getClass.getSimpleName,
        forStore.tableName()
      )

      migrations.foldLeft(Future.successful(Done)) { case (collected, current) =>
        for {
          _ <- collected
          _ <- current.run(forStore = forStore, withDatabase = database)
        } yield {
          Done
        }
      }
    }
  }

  def apply(database: JdbcProfile#Backend#Database)(implicit system: ActorSystem[Nothing]): MigrationExecutor =
    new Default(database)
}
