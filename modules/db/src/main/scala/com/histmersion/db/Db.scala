package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.zaxxer.hikari.HikariDataSource
import org.postgresql.ds.PGSimpleDataSource
import java.sql.SQLException
import javax.sql.DataSource

/** Postgres connection pools from the HOCON config (see [[AppConfig]] / reference.conf).
  *
  * **Pooled.** `PGSimpleDataSource` opens a fresh TCP connection, TLS handshake and startup packet for every
  * `connect`/`transact` — i.e. for every repo call — which is most of the latency of a cheap query. HikariCP is
  * reflection-free enough for native-image (the tracing agent captures what it needs) and the pool is small on purpose:
  * the server is the only database client and only a handful of netty threads ever ask.
  *
  * **Owned, never cached.** Each call builds a NEW pool, and whoever asked for it owns it and closes it: the server's
  * hot component graph (`server/hot`, docs/research-plugins.md §3.6) holds its pools in the scope of the node that
  * built them, so a config reload can swap the database; a test suite holds its own in a fixture. There is no
  * process-wide pool — a static cache keyed by URL is exactly what made a change of database a restart.
  */
object Db:

  /** The APPLICATION pool: owns the schema, runs the migrations, serves every repo. */
  def pool(cfg: DbConfig): HikariDataSource =
    pooled(cfg.url, cfg.user, cfg.password, cfg.pool.max, "histmersion")

  /** The READ-ONLY pool behind `POST /api/query` — the `histmersion_query` role of migration V011, which holds nothing
    * but SELECT. Deliberately tiny: the hatch is an interactive escape valve, not a serving path.
    */
  def queryPool(db: DbConfig, q: QueryConfig): HikariDataSource =
    pooled(db.url, q.user, q.password, math.min(4, math.max(1, db.pool.max)), "histmersion-query")

  /** Build a pool the way a NATIVE IMAGE can.
    *
    * Two things about HikariCP are reflective, and both are avoided here rather than papered over with reachability
    * metadata:
    *
    *   - `HikariDataSource(config)` copies the config with `copyStateTo`, which walks
    *     `HikariConfig.class.getDeclaredFields()`. Under native-image that array comes back EMPTY, so NOTHING is copied
    *     and the pool starts with a null DataSource, a null URL and a null pool name — `PoolBase.newConnection` then
    *     dies on an NPE before the server can bind. The no-arg `HikariDataSource` IS a `HikariConfig`, so configuring
    *     it directly copies nothing.
    *   - given a JDBC URL, Hikari resolves the driver through `DriverManager`/`ServiceLoader`. Handed a constructed
    *     `PGSimpleDataSource` it never looks one up.
    *
    * Neither is a workaround for a missing agent run: both are the shapes that have no reflection in them at all, which
    * is the rule this codebase already follows for everything else.
    */
  private def pooled(url: String, user: String, password: String, max: Int, name: String): HikariDataSource =
    val pg = PGSimpleDataSource()
    pg.setUrl(url)
    pg.setUser(user)
    pg.setPassword(password)
    val ds = HikariDataSource()
    ds.setDataSource(pg)
    ds.setMaximumPoolSize(math.max(1, max))
    // idle connections are not free on the Postgres side, and a test run opens a pool per suite;
    // keep one warm and let the rest go.
    ds.setMinimumIdle(1)
    ds.setPoolName(s"$name@${databaseOf(url)}")
    ds

  /** The database a JDBC URL names (`jdbc:postgresql://h:p/NAME?…`), for a pool's name in the log. */
  private[db] def databaseOf(url: String): String =
    url.takeWhile(_ != '?').reverse.takeWhile(_ != '/').reverse

  /** Connections the pool behind `ds` has lent out now (0 for a pool that has not started, or no pool at all). A
    * retired pool is closed only when this is 0.
    */
  def activeConnections(ds: DataSource): Int =
    hikari(ds).flatMap(p => Option(p.getHikariPoolMXBean)).map(_.getActiveConnections).getOrElse(0)

  private def hikari(ds: DataSource): Option[HikariDataSource] =
    ds match
      case h: HikariDataSource => Some(h)
      case other               =>
        try Option.when(other.isWrapperFor(classOf[HikariDataSource]))(other.unwrap(classOf[HikariDataSource]))
        catch case _: SQLException => None

  /** Database-level settings (V023), re-applied at every startup. `ALTER DATABASE … SET` lives in the cluster catalog,
    * not in the schema, so a `pg_dump`/`pg_restore` without `--create` — i.e. every `histmersion restore`, or moving
    * the data directory — silently drops it while Flyway still records V023 as applied. Idempotent; takes effect for
    * sessions opened afterwards.
    *
    * `plan_cache_mode = force_custom_plan`: pgjdbc server-prepares a statement on its fifth use in a pooled connection,
    * and Postgres then switches it to a GENERIC plan — one that cannot see the bound values. An `ILIKE ?` becomes
    * `like_escape($n)` with no trigram index, a `= ANY(?::text[])` an array of unknown size; a place's hover counts
    * went from 17 ms to 1.5 s on the sixth hover (2026-10-09), and a place-name search from 0.1 s to minutes. Every
    * statement here is small next to its planning, so each is planned for its own values.
    */
  private[db] val databaseSettings: List[(String, String)] =
    List("random_page_cost" -> "1.1", "plan_cache_mode" -> "force_custom_plan")

  def ensureDatabaseSettings(ds: DataSource): Unit =
    val c = ds.getConnection()
    try
      val st = c.createStatement()
      try
        databaseSettings.foreach { (k, v) =>
          st.execute(s"DO $$$$ BEGIN EXECUTE format('ALTER DATABASE %I SET $k = $v', current_database()); END $$$$")
        }
      finally st.close()
    finally c.close()

  /** Tables the statistics collector has never seen analyzed. Postgres's cumulative statistics are reset by a crash
    * (e.g. a full disk), after which autovacuum has no row counts to act on and would leave these tables unanalyzed
    * until enough new writes accumulate.
    */
  private[db] val staleStatsSql: String =
    "SELECT relname FROM pg_stat_user_tables" +
      " WHERE last_analyze IS NULL AND last_autoanalyze IS NULL AND schemaname = 'public'" +
      " ORDER BY relname"

  /** ANALYZE every table listed by [[staleStatsSql]]; returns their names. Meant for a background thread at startup: it
    * is a no-op on a healthy database and self-heals one after a crash.
    */
  def analyzeStale(ds: DataSource): List[String] =
    val c = ds.getConnection()
    try
      val rs = c.createStatement().executeQuery(staleStatsSql)
      val names = Iterator.continually(rs).takeWhile(_.next()).map(_.getString(1)).toList
      names.foreach { t =>
        val st = c.createStatement()
        try st.execute(s"""ANALYZE "${t.replace("\"", "\"\"")}"""")
        finally st.close()
      }
      names
    finally c.close()

  /** Throw away the pooled connections of `ds` (the pool itself, or a proxy that unwraps to one), gracefully (in-flight
    * work finishes; the connection is closed instead of returned).
    *
    * `pg_restore --clean` drops and recreates every type in the database, and a session that was open across that keeps
    * PostGIS's per-session type/opclass cache from BEFORE the restore — the next spatial query then fails with "no
    * spatial operator found for st_coveredby: opfamily …". Unpooled code never hit this because every call opened a
    * fresh session; a pool must be told.
    */
  def evict(ds: DataSource): Unit =
    hikari(ds).foreach { p =>
      try Option(p.getHikariPoolMXBean).foreach(_.softEvictConnections())
      catch case e: Throwable => scribe.warn(s"could not evict ${p.getPoolName}: ${e.getMessage}")
    }

  // ── the read-only role ─────────────────────────────────────────────────────────────────────────

  /** Give the `histmersion_query` role (created by migration V011) its password and its statement timeout, running as
    * the application role.
    *
    * Neither value can live in the migration: the password must not be committed, and the timeout is config. Neither
    * can be a bind parameter either — `ALTER ROLE` takes no parameters — so Postgres itself renders the statement
    * through `format(%I, %L)`, which is the quoting rule for identifiers and literals and cannot be talked out of it.
    *
    * `Left` when the application role may not `ALTER ROLE` (it is not a superuser and has no CREATEROLE). The caller
    * decides what to do about it; nothing here throws.
    */
  def provisionQueryRole(admin: DataSource, q: QueryConfig): Either[String, Unit] =
    val c = admin.getConnection
    try
      // A restore into a FRESH cluster (disaster recovery) finds V011 recorded as applied but no role:
      // roles live in the cluster, not in the dump that `--no-privileges` restores. Create it here,
      // with V011's attributes, so the read-only hatch never silently runs as the application role.
      val create = c.prepareStatement(
        "SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION', ?)" +
          " WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)"
      )
      val createDdl =
        try
          create.setString(1, q.user); create.setString(2, q.user)
          val rs = create.executeQuery()
          try if rs.next() then Some(rs.getString(1)) else None
          finally rs.close()
        finally create.close()
      createDdl.foreach { ddl =>
        val st = c.createStatement()
        try { val _ = st.execute(ddl) }
        finally st.close()
      }
      // The grants repeat V011 on purpose: a restore runs `--no-privileges` (so a dump taken under
      // other role names still loads) and a migration already recorded as applied never runs again.
      // `%s` takes the unused second argument so every statement shares one shape.
      val alters = List(
        ("ALTER ROLE %I LOGIN PASSWORD %L", q.password),
        (s"ALTER ROLE %I SET statement_timeout = %L", math.max(1, q.timeoutMs).toString),
        ("ALTER ROLE %I SET default_transaction_read_only = %L", "on"),
        ("ALTER ROLE %I SET search_path = %L", "public"),
        ("GRANT USAGE ON SCHEMA public TO %I%s", ""),
        ("GRANT SELECT ON ALL TABLES IN SCHEMA public TO %I%s", ""),
        ("ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO %I%s", ""),
        ("REVOKE CREATE ON SCHEMA public FROM %I%s", "")
      )
      alters.foreach { (fmt, arg) =>
        val ps = c.prepareStatement(s"SELECT format('$fmt', ?, ?)")
        val ddl =
          try
            ps.setString(1, q.user)
            ps.setString(2, arg)
            val rs = ps.executeQuery()
            try if rs.next() then rs.getString(1) else ""
            finally rs.close()
          finally ps.close()
        if ddl.nonEmpty then
          val st = c.createStatement()
          try { val _ = st.execute(ddl) }
          finally st.close()
      }
      Right(())
    catch case e: SQLException => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
    finally
      try c.close()
      catch case _: SQLException => ()

  /** Can we actually connect as the read-only role? Checked once at startup, so a misconfigured password is a log line
    * at boot rather than a mystery 500 on the first `query`.
    */
  def canConnect(ds: DataSource): Either[String, Unit] =
    try
      val c = ds.getConnection
      try Right(())
      finally c.close()
    catch case e: Throwable => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
