package com.histmersion
package db

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.AppConfig

import javax.sql.DataSource
import com.histmersion.util.database.Migrate
import com.histmersion.util.database.given
import com.histmersion.config.DbConfig

/** The squash of the migration chain ([[Migrate.squashHistory]]): a fresh database gets the one migration; a database
  * the chain before it built keeps every row and has its history replaced by a baseline at 109; one that chain had not
  * finished is refused, untouched. In databases of their own beside HISTMERSION_TEST_DB_URL's, dropped afterwards;
  * skipped without it.
  */
class MigrateSquashSuite extends DbSuite with Bounded:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  private lazy val cfg = AppConfig.load()
  private lazy val admin = testPool(cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  private lazy val dbs = ScratchDatabases("histmersion_squash", cfg.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
  private val created = scala.collection.mutable.ListBuffer.empty[String]

  private def exec(ds: DataSource, stmt: String): Unit =
    val c = ds.getConnection
    try
      val st = c.createStatement()
      try { val _ = st.execute(stmt) }
      finally st.close()
    finally c.close()

  private def strings(ds: DataSource, query: String): List[String] =
    val c = ds.getConnection
    try
      val st = c.createStatement()
      try
        val rs = st.executeQuery(query)
        Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toList
      finally st.close()
    finally c.close()

  private def emptyDatabase(name: String): DataSource =
    exec(admin, s"DROP DATABASE IF EXISTS $name WITH (FORCE)")
    exec(admin, s"CREATE DATABASE $name")
    created += name
    testPool(cfg.db.modify(_.url).setTo(dbs.urlOf(name)))

  /** The history the chain before the squash left: `001` to `top`, each a successful SQL migration. */
  private def oldChain(ds: DataSource, top: Int): Unit =
    exec(ds, "DELETE FROM flyway_schema_history")
    exec(
      ds,
      s"""INSERT INTO flyway_schema_history
         |  (installed_rank, version, description, type, script, checksum, installed_by, execution_time, success)
         |SELECT v, lpad(v::text, 3, '0'), 'm' || v, 'SQL', 'V' || lpad(v::text, 3, '0') || '__m.sql', v, 'x', 1, true
         |FROM generate_series(1, $top) v""".stripMargin
    )

  private val history = "SELECT version || ' ' || type FROM flyway_schema_history ORDER BY installed_rank"

  override def afterSuite(): Unit =
    if testDb.isDefined then
      created.foreach(n => exec(admin, s"DROP DATABASE IF EXISTS $n WITH (FORCE)"))
      dbs.dropAll()

  test("a fresh database gets the one migration, at the version the chain ended"):
    val ds = emptyDatabase("histmersion_squash_fresh_test")
    assertEquals(Migrate.run(ds), Migrate.SquashedAt)
    assertEquals(strings(ds, history), List(s"${Migrate.SquashedAt} SQL"))
    assertEquals(strings(ds, "SELECT count(*)::text FROM instance"), List("1"))
    assert(Migrate.validate(ds).isRight, Migrate.validate(ds).toString)

  test("a database the old chain built keeps its rows; its history becomes one baseline"):
    val ds = emptyDatabase("histmersion_squash_old_test")
    val _ = Migrate.run(ds)
    exec(ds, "INSERT INTO queue_items (list, text) VALUES ('tasks', 'kept')")
    val before = strings(ds, "SELECT md5(string_agg(t::text, ',' ORDER BY t::text)) FROM queue_items t")
    oldChain(ds, 109)
    assertEquals(Migrate.run(ds), Migrate.SquashedAt)
    assertEquals(strings(ds, history), List(s"${Migrate.SquashedAt} BASELINE"))
    assertEquals(strings(ds, "SELECT md5(string_agg(t::text, ',' ORDER BY t::text)) FROM queue_items t"), before)
    assert(Migrate.validate(ds).isRight, Migrate.validate(ds).toString)
    assertEquals(Migrate.run(ds), Migrate.SquashedAt, "idempotent")
    assertEquals(strings(ds, history), List(s"${Migrate.SquashedAt} BASELINE"))

  test("a database the old chain had not finished is refused, its history untouched"):
    val ds = emptyDatabase("histmersion_squash_behind_test")
    val _ = Migrate.run(ds)
    oldChain(ds, 108)
    val e = intercept[IllegalStateException](Migrate.run(ds))
    assert(e.getMessage.contains("schema 108"), e.getMessage)
    assertEquals(strings(ds, "SELECT count(*)::text FROM flyway_schema_history"), List("108"))
    assert(Migrate.validate(ds).left.exists(_.value.contains("schema 108")), Migrate.validate(ds).toString)
