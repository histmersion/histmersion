package com.histmersion.db

import com.histmersion.domain.library.search.QueryAccess

import com.histmersion.config.{DbConfig, QueryConfig}
import com.zaxxer.hikari.HikariDataSource

import javax.sql.DataSource
import scala.util.Try
import com.histmersion.util.database.{Db, Migrate}

/** A suite that owns the connection pools it opens: one per database and role it asks for ([[testPool]],
  * [[testQueryPool]]), opened on first use and closed when the suite is done — after its own clean-up ([[afterSuite]]),
  * which may still need them.
  *
  * There is no process-wide pool any more ([[Db]] builds a new one per call; docs/research-plugins.md §3.6), so a pool
  * a suite did not close would keep its connections until the JVM ends, and every suite of a run shares the test
  * database's `max_connections`. The suites run one at a time, so a run holds one suite's pools (and those of the
  * servers it starts, which close with their scope).
  */
abstract class DbSuite extends munit.FunSuite:
  private val owned = scala.collection.mutable.LinkedHashMap.empty[(String, String), HikariDataSource]

  /** The application pool on `cfg`'s database, this suite's own. */
  protected def testPool(cfg: DbConfig): DataSource = owned.synchronized {
    owned.getOrElseUpdate((cfg.url, cfg.user), Db.pool(cfg))
  }

  /** The read-only role's pool on `db`'s database, this suite's own. */
  protected def testQueryPool(db: DbConfig, q: QueryConfig): DataSource = owned.synchronized {
    owned.getOrElseUpdate((db.url, q.user), QueryAccess.pool(db, q))
  }

  /** Fresh, migrated, EMPTY databases for this suite, named `<prefix>_<n>_test` beside `cfg`'s database: each a copy
    * (`CREATE DATABASE … TEMPLATE`) of one database migrated once per suite (#3765). The suites that compare whole
    * databases (bundles, merges) made dozens, each running every migration — ~1.5 s apiece, a third of the db module's
    * test time. The template is `<prefix>_template_test`, the suite's own (groups share the cluster); [[dropAll]]
    * removes it and every copy.
    */
  final protected class ScratchDatabases(prefix: String, cfg: DbConfig):
    private lazy val admin = testPool(cfg)
    private val made = scala.collection.mutable.ListBuffer.empty[String]

    private def exec(stmt: String): Unit =
      val c = admin.getConnection
      try {
        val st = c.createStatement();
        try { val _ = st.execute(stmt) }
        finally st.close()
      } finally c.close()

    /** The URL of database `name` on `cfg`'s server. */
    def urlOf(name: String): String = cfg.url.replaceFirst("/[^/?]+(\\?|$)", s"/$name$$1")

    private lazy val template: String =
      val t = s"${prefix}_template_test"
      exec(s"DROP DATABASE IF EXISTS $t WITH (FORCE)")
      exec(s"CREATE DATABASE $t")
      made += t
      // its own pool, closed before it is copied: a template must have no session
      val p = Db.pool(cfg.copy(url = urlOf(t)))
      try Migrate.run(p)
      finally p.close()
      t

    /** A new copy of the migrated template, named `<prefix>_<n>_test`, and the suite's pool on it. */
    def apply(n: String): DataSource =
      val name = s"${prefix}_${n}_test"
      exec(s"DROP DATABASE IF EXISTS $name WITH (FORCE)")
      copyOf(template, name, attempts = 50)
      made += name
      val ds = testPool(cfg.copy(url = urlOf(name)))
      renew(ds)
      ds

    /** A copy is a new collection, as a database migrated anew is: an instance id of its own, the rows the migrations
      * wrote under it (their `origin`), and new row uids — two copies must never look like one instance to a bundle or
      * a merge.
      */
    private def renew(ds: DataSource): Unit =
      val c = ds.getConnection
      try
        val st = c.createStatement()
        try
          val _ = st.execute("""DO $$
            DECLARE old uuid; fresh uuid := uuidv7(); t record;
            BEGIN
              SELECT id INTO old FROM instance;
              UPDATE instance SET id = fresh, created_at = now();
              FOR t IN SELECT c.table_name, c.column_name FROM information_schema.columns c
                       JOIN information_schema.tables x ON x.table_schema = c.table_schema AND x.table_name = c.table_name
                       WHERE c.table_schema = 'public' AND x.table_type = 'BASE TABLE' AND c.data_type = 'uuid'
                         AND c.column_name IN ('origin', 'uid') AND c.table_name <> 'instance' LOOP
                IF t.column_name = 'origin' THEN
                  EXECUTE format('UPDATE %I SET origin = $1 WHERE origin = $2', t.table_name) USING fresh, old;
                ELSE
                  EXECUTE format('UPDATE %I SET uid = uuidv7()', t.table_name);
                END IF;
              END LOOP;
            END $$""")
        finally st.close()
      finally c.close()

    // a closed pool's sessions may take a moment to leave the template ("being accessed by other users")
    private def copyOf(t: String, name: String, attempts: Int): Unit =
      try exec(s"CREATE DATABASE $name TEMPLATE $t")
      catch
        case e: java.sql.SQLException if attempts > 1 && Option(e.getMessage).exists(_.contains("being accessed")) =>
          Thread.sleep(100)
          copyOf(t, name, attempts - 1)

    /** Drop every database it made (the template too). */
    def dropAll(): Unit = made.reverse.foreach(n => exec(s"DROP DATABASE IF EXISTS $n WITH (FORCE)"))

  /** The suite's own clean-up, run before its pools are closed (in place of `afterAll`). */
  protected def afterSuite(): Unit = ()

  final override def afterAll(): Unit =
    try afterSuite()
    finally closePools()

  /** Close every pool this suite opened (a suite that drops a database closes them first). */
  protected def closePools(): Unit = owned.synchronized {
    owned.values.foreach(p => Try(p.close()))
    owned.clear()
  }
