package com.histmersion.db

import com.histmersion.domain.library.search.QueryAccess

import com.histmersion.domain.library.search.SearchFixtures
import com.histmersion.domain.library.sources.SourceFixtures

import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.Source

/** The read-only query path against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise.
  *
  * The point of these tests is the SAFETY PROPERTY: a write must fail at the Postgres level, not at a keyword blacklist
  * we wrote — so every refusal below is asserted to carry Postgres's own "read-only transaction" message, and the store
  * is checked afterwards to prove nothing changed.
  */
class QueryRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val cfg = AppConfig.load()
  private lazy val dbCfg = cfg.db.copy(url = testDb.get)

  /** The APPLICATION role — schema owner, superuser in every deployment we ship. Used here only to migrate and to seed
    * the probe row.
    */
  private lazy val ds =
    val d = testPool(dbCfg)
    Migrate.run(d)
    d

  /** The role `query` actually runs as: `histmersion_query` (migration V011), created by the migration and given its
    * password here exactly as `Main` does at startup.
    */
  private lazy val queryDs =
    val _ = ds // migrate first: V011 creates the role
    QueryAccess.provision(ds, cfg.query) match
      case Right(_) => ()
      case Left(m)  => fail(s"could not provision the read-only role: $m")
    val q = testQueryPool(dbCfg, cfg.query)
    Db.canConnect(q).left.foreach(m => fail(s"the read-only role cannot connect: $m"))
    q

  private def run(sql: String, maxRows: Int = 100, timeoutMs: Int = 5000, maxCell: Int = 2000) =
    SearchFixtures.run(queryDs, sql, timeoutMs, maxRows, maxCell)

  private val ck = "zzquery_probe"

  override def beforeEach(context: BeforeEach): Unit =
    connect(ds)(sql"DELETE FROM sources WHERE citekey = $ck".update.run())
    SourceFixtures.upsert(
      ds,
      Source(
        citekey = ck,
        title = "Probe",
        authors = List("A"),
        doi = None,
        year = Some(2020),
        venue = None,
        status = com.histmersion.domain.library.Source.Status.Current,
        topics = Nil,
        regions = Nil,
        tags = Nil,
        period = Some(Period(-2000, -1500)),
        summary = Some("s"),
        citedByCount = None,
        influentialCitations = None,
        provenance = None,
        calibreId = None
      ),
      cfg.limits.chunkChars
    )

  private def probeExists: Boolean =
    connect(ds)(sql"SELECT count(*) FROM sources WHERE citekey = $ck".query[Int].run().head) == 1

  // ── reads work ───────────────────────────────────────────────────────────────────────────────

  test("a SELECT returns columns and rows"):
    val r = run(s"select citekey, year from sources where citekey = '$ck'").toOption.get
    assertEquals(r.columns, List("citekey", "year"))
    assertEquals(r.rows, List(List(Some(ck), Some("2020"))))
    assertEquals(r.rowCount, 1)
    assertEquals(r.truncated, false)

  test("count(*) over the whole corpus works — the acceptance query"):
    val r = run("select count(*) from sources").toOption.get
    assertEquals(r.columns, List("count"))
    assert(r.rows.head.head.exists(_.toInt >= 1), r.rows.toString)

  test("NULL comes back as an absent cell, not as the string 'null'"):
    assertEquals(run("select null::text as x").toOption.get.rows, List(List(None)))

  test("deep time reads back as signed years with the inclusive upper bound"):
    val r = run(s"select lower(period), upper(period) - 1 from sources where citekey = '$ck'").toOption.get
    assertEquals(r.rows.head, List(Some("-2000"), Some("-1500")))

  test("PostGIS is available inside the read-only transaction"):
    assertEquals(run("select ST_AsText(ST_MakePoint(32, 39))").toOption.get.rows, List(List(Some("POINT(32 39)"))))

  test("queue_items is visible to `query` (the tool description says it is)"):
    assert(run("select count(*) from queue_items").isRight)

  // ── writes are refused BY POSTGRES ───────────────────────────────────────────────────────────

  private def refused(sql: String): String =
    val e = run(sql).left.getOrElse(fail(s"'$sql' was NOT refused"))
    assert(e.toLowerCase.contains("read-only transaction"), s"'$sql' failed for the wrong reason: $e")
    e

  test("delete is refused, and the row is still there"):
    val _ = refused("delete from sources")
    assert(probeExists)

  test("update is refused"):
    val _ = refused("update sources set title = 'hacked'")
    assertEquals(run(s"select title from sources where citekey = '$ck'").toOption.get.rows, List(List(Some("Probe"))))

  test("insert is refused"):
    val _ = refused("insert into sources (citekey, title) values ('zzquery_evil', 'x')")
    assertEquals(
      run("select count(*) from sources where citekey = 'zzquery_evil'").toOption.get.rows,
      List(List(Some("0")))
    )

  test("create / drop / truncate are refused"):
    val _ = refused("create table zzquery_tmp (id int)")
    val _ = refused("drop table if exists sources")
    val _ = refused("truncate sources")

  test("a data-modifying CTE — the classic blacklist escape — is refused too"):
    val _ = refused(s"with gone as (delete from sources where citekey = '$ck' returning citekey) select * from gone")
    assert(probeExists)

  test("a syntax error is a value carrying Postgres's message, not an exception"):
    val e = run("select * fom sources").left.getOrElse(fail("expected a failure"))
    assert(e.toLowerCase.contains("syntax"), e)

  test("an empty statement is refused before it reaches the database"):
    assertEquals(run("   "), Left("empty statement"))

  // ── the limits ───────────────────────────────────────────────────────────────────────────────

  test("the row cap truncates and says so"):
    val r = run("select generate_series(1, 50) as n", maxRows = 5).toOption.get
    assertEquals(r.rowCount, 5)
    assertEquals(r.truncated, true)

  test("under the cap, nothing is marked truncated"):
    val r = run("select generate_series(1, 3) as n", maxRows = 5).toOption.get
    assertEquals((r.rowCount, r.truncated), (3, false))

  test("a huge cell is cut with a visible marker rather than shipped whole"):
    val r = run("select repeat('x', 5000) as big", maxCell = 100).toOption.get
    val cell = r.rows.head.head.get
    assert(cell.length < 200, cell.length.toString)
    assert(cell.contains("(+4900 chars)"), cell.takeRight(30))

  test("statement_timeout aborts a long statement"):
    val e = run("select pg_sleep(5)", timeoutMs = 200).left.getOrElse(fail("expected a timeout"))
    assert(e.toLowerCase.contains("statement timeout") || e.toLowerCase.contains("canceling"), e)

  test("a refused statement leaves the connection usable for the next one"):
    val _ = refused("delete from sources")
    assert(run("select 1").isRight)

  // ── the live schema ──────────────────────────────────────────────────────────────────────────

  test("information_schema lists the real tables with their columns"):
    val ts = SearchFixtures.tables(ds)
    val names = ts.map(_.name)
    List("sources", "findings", "edges", "source_chunks", "queue_items")
      .foreach(t => assert(names.contains(t), s"$t missing from $names"))
    assert(!names.contains("spatial_ref_sys"), "PostGIS plumbing stays out")
    assert(!names.contains("flyway_schema_history"), "migration bookkeeping stays out")
    val sources = ts.find(_.name == "sources").get
    assertEquals(sources.kind, "table")
    assert(sources.columns.map(_.name).contains("citekey"))
    assert(sources.columns.find(_.name == "citekey").exists(!_.nullable))
    // the flat findings VIEW is reported as a view
    assert(ts.find(_.name == "findings_flat").forall(_.kind == "view"))

  test("the generated tool description names the tables that exist"):
    // `Query` alone is ambiguous here — magnum exports one too
    val d = com.histmersion.api.library.Query
      .toolDescription(com.histmersion.api.library.Query.SchemaTables(SearchFixtures.tables(ds)))
    assert(d.contains("sources("), d)
    assert(d.contains("queue_items("), d)

  // ── the batch / session-control escape (the critical fix) ────────────────────────────────────
  // pgjdbc runs a `;`-batch statement by statement, so `SET TRANSACTION READ WRITE; …` used to turn
  // the read-only guard OFF before the payload ran. Refused before the connection is even taken.

  test("`SET TRANSACTION READ WRITE; SELECT …` is refused, and nothing was executed"):
    val e = run(s"SET TRANSACTION READ WRITE; select * from sources").left
      .getOrElse(fail("the disarming batch was NOT refused"))
    assert(e.contains("statements"), e)
    assert(probeExists)

  test("`SET LOCAL statement_timeout = 0; SELECT pg_sleep(30)` is refused"):
    assert(run("SET LOCAL statement_timeout = 0; select pg_sleep(30)").isLeft)

  test("`SELECT 1; SELECT 2` is refused — the hatch runs exactly ONE statement"):
    assert(run("select 1; select 2").isLeft)

  test("a lone SET is refused by the keyword check"):
    val e = run("set statement_timeout = 0").left.getOrElse(fail("a lone SET was NOT refused"))
    assert(e.contains("'SET'"), e)

  // ── the ROLE is what stops a superuser-only function ─────────────────────────────────────────

  test("`query` does not run as a superuser"):
    val r = run("select current_user, (select rolsuper from pg_roles where rolname = current_user)").toOption.getOrElse(
      fail("current_user query failed")
    )
    assertEquals(r.rows.head(0), Some(cfg.query.user))
    assertEquals(r.rows.head(1), Some("f")) // pgjdbc renders a bool column as f/t

  test("pg_read_file is permission denied — the read-only TRANSACTION never stopped this"):
    val e = run("select pg_read_file('/etc/hostname')").left
      .getOrElse(fail("pg_read_file was NOT refused"))
    assert(e.toLowerCase.contains("permission denied"), e)

  test("the read-only role cannot even be talked into a writable transaction"):
    // `default_transaction_read_only = on` is a property of the ROLE, so it holds on a connection
    // this code did not open — and the statement check refuses the SET that would flip it.
    assert(run("SET TRANSACTION READ WRITE").isLeft)
    val r = run("show default_transaction_read_only").toOption.get
    assertEquals(r.rows.head.head, Some("on"))

  // #393: a restore into a fresh cluster has the tables but not the role (V011 is recorded as applied)
  test("provisioning creates the read-only role when the cluster has none, and it can read but not write"):
    val role = "zz_query_role_probe"
    def admin(sql: String): Unit =
      val c = ds.getConnection
      try {
        val st = c.createStatement();
        try { val _ = st.execute(sql) }
        finally st.close()
      } finally c.close()
    scala.util.Try(admin(s"DROP OWNED BY $role")); admin(s"DROP ROLE IF EXISTS $role")
    val qc = cfg.query.copy(user = role, password = "probe-pw")
    try
      assertEquals(QueryAccess.provision(ds, qc), Right(()))
      assertEquals(QueryAccess.provision(ds, qc), Right(()), "idempotent")
      val q = testQueryPool(dbCfg, qc)
      assertEquals(Db.canConnect(q), Right(()))
      assert(SearchFixtures.run(q, s"SELECT title FROM sources WHERE citekey = '$ck'", 5000, 10, 100).isRight)
      assert(SearchFixtures.run(q, s"DELETE FROM sources WHERE citekey = '$ck'", 5000, 10, 100).isLeft)
      assert(probeExists)
    finally
      scala.util.Try(admin(s"DROP OWNED BY $role")); scala.util.Try(admin(s"DROP ROLE IF EXISTS $role"))
