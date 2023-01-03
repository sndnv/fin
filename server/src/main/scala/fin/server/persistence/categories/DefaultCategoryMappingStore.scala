package fin.server.persistence.categories

import akka.Done
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import fin.server.model.CategoryMapping
import slick.ast.BaseTypedType
import slick.jdbc.JdbcProfile
import slick.lifted.ProvenShape

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class DefaultCategoryMappingStore(
  tableName: String,
  protected val profile: JdbcProfile,
  protected val database: JdbcProfile#Backend#DatabaseDef
)(implicit val system: ActorSystem[Nothing])
    extends CategoryMappingStore {
  import profile.api._

  // this EC is only used for mapping ops after results are retrieved from the underlying DB;
  // Slick uses its own dispatcher(s) to handle blocking IO
  // - https://scala-slick.org/doc/3.3.1/database.html#database-thread-pool
  private implicit val ec: ExecutionContext = system.dispatchers.lookup(DispatcherSelector.default())

  private class SlickCategoryMappingStore(tag: Tag) extends Table[CategoryMapping](tag, tableName) {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def condition: Rep[CategoryMapping.Condition] = column[CategoryMapping.Condition]("CONDITION")
    def matcher: Rep[String] = column[String]("MATCHER")
    def category: Rep[String] = column[String]("CATEGORY")
    def created: Rep[Instant] = column[Instant]("CREATED")
    def updated: Rep[Instant] = column[Instant]("UPDATED")
    def removed: Rep[Option[Instant]] = column[Option[Instant]]("REMOVED")

    private def cols = (id, condition, matcher, category, created, updated, removed)

    def * : ProvenShape[CategoryMapping] =
      cols <> ((CategoryMapping.apply _).tupled, CategoryMapping.unapply)
  }

  private implicit val conditionColumnType: BaseTypedType[CategoryMapping.Condition] =
    MappedColumnType.base[CategoryMapping.Condition, String](
      tmap = _.toString,
      tcomap = CategoryMapping.Condition.apply
    )

  private val store = TableQuery[SlickCategoryMappingStore]

  override def init(): Future[Done] =
    database.run(store.schema.create).map(_ => Done)

  override def drop(): Future[Done] =
    database.run(store.schema.drop).map(_ => Done)

  override def create(categoryMapping: CategoryMapping): Future[Done] =
    database.run(store.insertOrUpdate(categoryMapping).map(_ => Done))

  override def update(categoryMapping: CategoryMapping): Future[Done] =
    database.run(store.insertOrUpdate(categoryMapping).map(_ => Done))

  override def delete(categoryMapping: CategoryMapping.Id): Future[Boolean] =
    database.run(store.filter(_.id === categoryMapping).map(_.removed).update(value = Some(Instant.now())).map(_ == 1))

  override def get(categoryMapping: CategoryMapping.Id): Future[Option[CategoryMapping]] =
    database.run(store.filter(_.id === categoryMapping).map(_.value).result.headOption)

  override def available(): Future[Seq[CategoryMapping]] =
    database.run(store.filter(_.removed.isEmpty).result)

  override def all(): Future[Seq[CategoryMapping]] =
    database.run(store.result)
}
