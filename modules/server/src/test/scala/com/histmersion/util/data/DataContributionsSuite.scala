package com.histmersion.util.data

import com.histmersion.config.AppConfig
import com.histmersion.db.{DbSuite, TestDb}
import com.histmersion.server.Deps
import com.augustnagro.magnum.*

/** The registry: one contribution per owner, one owner per table, and — on a migrated database — every table of the
  * schema owned by exactly one contributor. The schema part is skipped without HISTMERSION_TEST_DB_URL.
  */
class DataContributionsSuite extends DbSuite:
  private val testDb = TestDb.url

  private final class Fixed(val owner: DataOwner, val tables: List[OwnedTable]) extends DataContribution:
    def figures(using DbCon): diagnostics.DataFigures = diagnostics.DataFigures.empty

  test("an owner registered twice and a table claimed twice are refused, each named"):
    val a = Fixed(DataOwner("a"), OwnedTable.all("t1", "t2"))
    val b = Fixed(DataOwner("b"), OwnedTable.all("t2"))
    assertEquals(
      DataContributions.of(List(a, b, a)).left.toOption,
      Some(List("owner a is registered twice", "table t1 is claimed by a, a", "table t2 is claimed by a, b, a"))
    )
    val ok = DataContributions.of(List(a, Fixed(DataOwner("b"), OwnedTable.all("t3")))).fold(e => fail(e.toString), identity)
    assertEquals(ok.ownerOf(TableName("t3")), Some(DataOwner("b")))
    assertEquals(ok.ownerOf(TableName("t9")), None)

  test("today's registry: the owners in their order, every table once"):
    assertEquals(
      Deps.data.owners.map(_.value),
      List(
        "automation", "catalogue", "media", "library", "research", "personal", "util.data", "util.jobs",
        "util.identity", "util.database"
      )
    )

  test("every table of the migrated schema has exactly one owner"):
    assume(testDb.isDefined, "HISTMERSION_TEST_DB_URL is not set")
    val ds = testPool(AppConfig.load().db.copy(url = testDb.get))
    com.histmersion.db.Migrate.run(ds)
    val schema = connect(ds)(
      sql"""SELECT table_name::text FROM information_schema.tables WHERE table_schema = 'public'
            AND table_type = 'BASE TABLE' ORDER BY 1""".query[String].run().toList
    )
    val owned = Deps.data.tables.map(_._2.name.value)
    assertEquals(schema.filterNot(owned.contains), Nil, "tables no owner registered")
    assertEquals(owned.filterNot(schema.contains), Nil, "registered tables the schema does not have")
