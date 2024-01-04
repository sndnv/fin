package fin.server.persistence.accounts

import fin.server.UnitSpec
import fin.server.model.Account
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.scalatest.BeforeAndAfterAll
import slick.jdbc.H2Profile

import java.time.Instant

class DefaultAccountStoreSpec extends UnitSpec with BeforeAndAfterAll {
  "A DefaultAccountStore" should "store and retrieve accounts" in {
    val store = new DefaultAccountStore(
      tableName = "DefaultAccountStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val account = Account(
      id = 0,
      externalId = "test-id",
      name = "test-name",
      description = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    val expectedId = 1

    for {
      _ <- store.init()
      _ <- store.create(account)
      existing <- store.get(account = expectedId)
      _ <- store.drop()
    } yield {
      existing should be(Some(account.copy(id = expectedId)))
    }
  }

  it should "update existing accounts" in {
    val store = new DefaultAccountStore(
      tableName = "DefaultAccountStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val account = Account(
      id = 0,
      externalId = "test-id",
      name = "test-name",
      description = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    val expectedId = 1

    for {
      _ <- store.init()
      _ <- store.create(account)
      existing <- store.get(account = expectedId).map {
        case Some(value) => value
        case None        => fail("Expected an existing account but none was found")
      }
      _ <- store.update(existing.copy(name = "other-name"))
      updated <- store.get(account = expectedId)
      _ <- store.drop()
    } yield {
      existing should be(account.copy(id = expectedId))
      updated should be(Some(account.copy(id = expectedId, name = "other-name")))
    }
  }

  it should "fail to retrieve missing accounts" in {
    val store = new DefaultAccountStore(
      tableName = "DefaultAccountStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    for {
      _ <- store.init()
      missing <- store.get(account = 1)
      _ <- store.drop()
    } yield {
      missing should be(None)
    }
  }

  it should "retrieve all accounts" in {
    val store = new DefaultAccountStore(
      tableName = "DefaultAccountStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val account = Account(
      id = 0,
      externalId = "test-id",
      name = "test-name",
      description = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(account)
      _ <- store.create(account.copy(externalId = "test-id-2", name = "test-name-2"))
      _ <- store.create(account.copy(externalId = "test-id-3", name = "test-name-3"))
      available <- store.available()
      all <- store.all()
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          account.copy(id = 1),
          account.copy(id = 2, externalId = "test-id-2", name = "test-name-2"),
          account.copy(id = 3, externalId = "test-id-3", name = "test-name-3")
        )
      )

      all should be(
        Seq(
          account.copy(id = 1),
          account.copy(id = 2, externalId = "test-id-2", name = "test-name-2"),
          account.copy(id = 3, externalId = "test-id-3", name = "test-name-3")
        )
      )
    }
  }

  it should "mark accounts as removed" in {
    val store = new DefaultAccountStore(
      tableName = "DefaultAccountStoreSpec",
      profile = H2Profile,
      database = h2db
    )

    val now = Instant.now()

    val account = Account(
      id = 0,
      externalId = "test-id",
      name = "test-name",
      description = "test-description",
      created = now,
      updated = now,
      removed = None
    )

    for {
      _ <- store.init()
      _ <- store.create(account)
      _ <- store.create(account.copy(externalId = "test-id-2", name = "test-name-2"))
      _ <- store.create(account.copy(externalId = "test-id-3", name = "test-name-3"))
      _ <- store.delete(account = 2)
      available <- store.available()
      all <- store.all()
      _ <- store.drop()
    } yield {
      available should be(
        Seq(
          account.copy(id = 1),
          account.copy(id = 3, externalId = "test-id-3", name = "test-name-3")
        )
      )

      all should be(
        Seq(
          account.copy(id = 1),
          account.copy(id = 2, externalId = "test-id-2", name = "test-name-2", removed = all.find(_.id == 2).flatMap(_.removed)),
          account.copy(id = 3, externalId = "test-id-3", name = "test-name-3")
        )
      )
    }
  }

  private implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(
    guardianBehavior = Behaviors.ignore,
    name = "DefaultAccountStoreSpec"
  )

  private val h2db = H2Profile.api.Database.forURL(url = "jdbc:h2:mem:DefaultAccountStoreSpec", keepAliveConnection = true)

  override protected def afterAll(): Unit = {
    h2db.close()
    typedSystem.terminate()
  }
}
