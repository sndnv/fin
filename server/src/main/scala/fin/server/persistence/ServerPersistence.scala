package fin.server.persistence

import akka.Done
import akka.actor.typed.ActorSystem
import com.typesafe.{config => typesafe}
import fin.server.persistence.accounts.{AccountStore, DefaultAccountStore}
import fin.server.persistence.transactions.{DefaultTransactionStore, TransactionStore}
import org.slf4j.Logger
import slick.jdbc.JdbcProfile

import scala.concurrent.Future

class ServerPersistence(
  persistenceConfig: typesafe.Config
)(implicit system: ActorSystem[Nothing], log: Logger) {
  import system.executionContext

  val profile: JdbcProfile = SlickProfile(profile = persistenceConfig.getString("database.profile"))

  val databaseUrl: String = persistenceConfig.getString("database.url")
  val databaseDriver: String = persistenceConfig.getString("database.driver")
  val databaseKeepAlive: Boolean = persistenceConfig.getBoolean("database.keep-alive-connection")
  val databaseInit: Boolean = persistenceConfig.getBoolean("database.init")

  private val database: profile.backend.DatabaseDef = profile.api.Database.forURL(
    url = databaseUrl,
    user = persistenceConfig.getString("database.user"),
    password = persistenceConfig.getString("database.password"),
    driver = databaseDriver,
    keepAliveConnection = databaseKeepAlive
  )

  val accounts: AccountStore = new DefaultAccountStore(
    tableName = "ACCOUNTS",
    profile = profile,
    database = database
  )

  val transactions: TransactionStore = new DefaultTransactionStore(
    tableName = "TRANSACTIONS",
    profile = profile,
    database = database
  )

  def init(): Future[Done] =
    if (databaseInit) {
      for {
        _ <- accounts.init()
        _ <- transactions.init()
      } yield {
        log.info("Database initialization complete")
        Done
      }
    } else {
      log.debug("Database initialization not enabled; skipping")
      Future.successful(Done)
    }

  def drop(): Future[Done] =
    for {
      _ <- accounts.drop()
      _ <- transactions.drop()
    } yield {
      Done
    }
}

object ServerPersistence {
  def apply(persistenceConfig: typesafe.Config)(implicit system: ActorSystem[Nothing], log: Logger): ServerPersistence =
    new ServerPersistence(persistenceConfig)
}
