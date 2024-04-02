package fin.server.persistence

import org.apache.pekko.Done
import org.slf4j.Logger
import slick.dbio.{DBIOAction, NoStream}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

final case class Migration(
  version: Int,
  needed: DBIOAction[Boolean, NoStream, Nothing],
  action: DBIOAction[_, NoStream, Nothing]
) {
  def run(
    forStore: Store,
    withDatabase: JdbcProfile#Backend#Database
  )(implicit ec: ExecutionContext, log: Logger): Future[Done] =
    withDatabase
      .run(needed)
      .flatMap {
        case true =>
          log.info(
            "Migration to version [{}] needed for [{} / {}]; running migration...",
            version,
            forStore.getClass.getSimpleName,
            forStore.tableName()
          )

          withDatabase
            .run(action)
            .map { _ =>
              log.info(
                "Migration to version [{}] for [{} / {}] completed successfully",
                version,
                forStore.getClass.getSimpleName,
                forStore.tableName()
              )
              Done
            }

        case false =>
          log.debug(
            "Skipping migration to version [{}] for [{} / {}]; migration not needed",
            version,
            forStore.getClass.getSimpleName,
            forStore.tableName()
          )
          Future.successful(Done)
      }
      .recoverWith { case NonFatal(e) =>
        log.error(
          "Migration to version [{}] for [{} / {}] failed with [{} - {}]",
          version,
          forStore.getClass.getSimpleName,
          forStore.tableName(),
          e.getClass.getSimpleName,
          e.getMessage
        )
        Future.failed(e)
      }
}
