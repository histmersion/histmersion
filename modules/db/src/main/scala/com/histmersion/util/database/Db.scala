package com.histmersion.util.database
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
  type ActiveConnections = ActiveConnections.T
  object ActiveConnections extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DatabaseName = DatabaseName.T
  object DatabaseName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type JdbcUrl = JdbcUrl.T
  object JdbcUrl extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Login = Login.T
  object Login extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Password = Password.T
  object Password extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolMax = PoolMax.T
  object PoolMax extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PoolName = PoolName.T
  object PoolName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SettingName = SettingName.T
  object SettingName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The APPLICATION pool: owns the schema, runs the migrations, serves every repo. */
  type PooledUrl = _root_.com.histmersion.util.database.Db.JdbcUrl
  val PooledUrl: _root_.com.histmersion.util.database.Db.JdbcUrl.type = _root_.com.histmersion.util.database.Db.JdbcUrl
  type DatabaseSettingsPart2 = DatabaseSettingsPart2.T
  object DatabaseSettingsPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  def pool(cfg: DbConfig): HikariDataSource =
    pooled(cfg.url, cfg.user, cfg.password, cfg.pool.max, com.histmersion.util.database.Db.PoolName("histmersion"))

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
  def pooled(url: com.histmersion.util.database.Db.PooledUrl, user: com.histmersion.util.database.Db.Login, password: com.histmersion.util.database.Db.Password, max: com.histmersion.util.database.Db.PoolMax, name: com.histmersion.util.database.Db.PoolName): HikariDataSource =
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
  private[histmersion] def databaseOf(url: com.histmersion.util.database.Db.JdbcUrl): com.histmersion.util.database.Db.DatabaseName =
    url.takeWhile(_ != '?').reverse.takeWhile(_ != '/').reverse

  /** Connections the pool behind `ds` has lent out now (0 for a pool that has not started, or no pool at all). A
    * retired pool is closed only when this is 0.
    */
  def activeConnections(ds: DataSource): com.histmersion.util.database.Db.ActiveConnections =
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
  private[histmersion] val databaseSettings: List[(com.histmersion.util.database.Db.SettingName, com.histmersion.util.database.Db.DatabaseSettingsPart2)] =
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
  private[histmersion] val staleStatsSql: String =
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

  /** Can we actually connect as the read-only role? Checked once at startup, so a misconfigured password is a log line
    * at boot rather than a mystery 500 on the first `query`.
    */
  def canConnect(ds: DataSource): Either[com.histmersion.util.database.Db.Problem, Unit] =
    try
      val c = ds.getConnection
      try Right(())
      finally c.close()
    catch case e: Throwable => Left(Option(e.getMessage).getOrElse(e.getClass.getSimpleName))
