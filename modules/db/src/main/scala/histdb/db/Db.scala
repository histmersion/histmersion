package histdb.db

import histdb.core.*

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource

/** Postgres DataSources from the HOCON config (see [[AppConfig]] / reference.conf).
  *
  * **Pooled.** `PGSimpleDataSource` opens a fresh TCP connection, TLS handshake and startup packet
  * for every `connect`/`transact` — i.e. for every repo call — which is most of the latency of a
  * cheap query. HikariCP is reflection-free enough for native-image (the tracing agent captures what
  * it needs) and the pool is small on purpose: the server is the only database client and only a
  * handful of netty threads ever ask.
  *
  * Pools are CACHED per (url, user). A pool is a heavyweight resource holding real connections, so
  * handing the same one back is both the correct thing and what keeps a test run — dozens of suites,
  * each building "its own" DataSource in the same JVM — from exhausting `max_connections`.
  */
object Db:

  private val pools = ConcurrentHashMap[(String, String), HikariDataSource]()

  /** The APPLICATION DataSource: owns the schema, runs the migrations, serves every repo. */
  def dataSource(cfg: DbConfig): DataSource =
    pooled(cfg.url, cfg.user, cfg.password, cfg.pool.max, "histdb")

  /** The READ-ONLY DataSource behind `POST /api/query` — the `histdb_query` role of migration V011,
    * which holds nothing but SELECT. Deliberately tiny: the hatch is an interactive escape valve,
    * not a serving path. */
  def queryDataSource(db: DbConfig, q: QueryConfig): DataSource =
    pooled(db.url, q.user, q.password, math.min(4, math.max(1, db.pool.max)), "histdb-query")

  private def pooled(url: String, user: String, password: String, max: Int, name: String): DataSource =
    pools.computeIfAbsent((url, user), _ => {
      val c = HikariConfig()
      c.setJdbcUrl(url)
      c.setUsername(user)
      c.setPassword(password)
      c.setMaximumPoolSize(math.max(1, max))
      // idle connections are not free on the Postgres side, and a test JVM builds many pools;
      // keep one warm and let the rest go.
      c.setMinimumIdle(1)
      c.setPoolName(s"$name-${pools.size + 1}")
      // GraalVM: Hikari's default "auto-detect the JDBC driver by reflection" is what we avoid by
      // giving it the URL only — pgjdbc registers itself through the ServiceLoader metadata the
      // tracing agent already captured.
      HikariDataSource(c)
    })

  def fromConfig(): DataSource = dataSource(AppConfig.load().db)

  /** Throw away every pooled connection, gracefully (in-flight work finishes; the connection is
    * closed instead of returned).
    *
    * `pg_restore --clean` drops and recreates every type in the database, and a session that was
    * open across that keeps PostGIS's per-session type/opclass cache from BEFORE the restore — the
    * next spatial query then fails with "no spatial operator found for st_coveredby: opfamily …".
    * Unpooled code never hit this because every call opened a fresh session; a pool must be told. */
  def evictAll(): Unit =
    pools.values.forEach { p =>
      try p.getHikariPoolMXBean.softEvictConnections()
      catch case e: Throwable => scribe.warn(s"could not evict ${p.getPoolName}: ${e.getMessage}")
    }

  // ── the read-only role ─────────────────────────────────────────────────────────────────────────

  /** Give the `histdb_query` role (created by migration V011) its password and its statement timeout,
    * running as the application role.
    *
    * Neither value can live in the migration: the password must not be committed, and the timeout is
    * config. Neither can be a bind parameter either — `ALTER ROLE` takes no parameters — so Postgres
    * itself renders the statement through `format(%I, %L)`, which is the quoting rule for identifiers
    * and literals and cannot be talked out of it.
    *
    * `Left` when the application role may not `ALTER ROLE` (it is not a superuser and has no
    * CREATEROLE). The caller decides what to do about it; nothing here throws. */
  def provisionQueryRole(admin: DataSource, q: QueryConfig): Either[String, Unit] =
    val c = admin.getConnection
    try
      val alters = List(
        ("ALTER ROLE %I LOGIN PASSWORD %L", q.password),
        (s"ALTER ROLE %I SET statement_timeout = %L", math.max(1, q.timeoutMs).toString),
        ("ALTER ROLE %I SET default_transaction_read_only = %L", "on"))
      alters.foreach { (fmt, arg) =>
        val ps = c.prepareStatement(s"SELECT format('$fmt', ?, ?)")
        val ddl =
          try
            ps.setString(1, q.user)
            ps.setString(2, arg)
            val rs = ps.executeQuery()
            try if rs.next() then rs.getString(1) else "" finally rs.close()
          finally ps.close()
        if ddl.nonEmpty then
          val st = c.createStatement()
          try { val _ = st.execute(ddl) } finally st.close()
      }
      Right(())
    catch case e: SQLException => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
    finally
      try c.close() catch case _: SQLException => ()

  /** Can we actually connect as the read-only role? Checked once at startup, so a misconfigured
    * password is a log line at boot rather than a mystery 500 on the first `query`. */
  def canConnect(ds: DataSource): Either[String, Unit] =
    try
      val c = ds.getConnection
      try Right(()) finally c.close()
    catch case e: Throwable => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
