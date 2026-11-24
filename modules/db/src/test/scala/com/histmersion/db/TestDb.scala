package com.histmersion
package db

/** The database the DB-backed suites run against: `HISTMERSION_TEST_DB_URL`, or `None` (the suites skip).
  *
  * The suites WRITE fixtures (`zz*` places, sources, articles), so the URL must name a disposable database — one whose
  * name ends in `_test`, as `just test-db` creates. Anything else (notably the live corpus, `histmersion`, which
  * `make dev-up` publishes on :5432) is refused with an error instead of silently seeding test polygons into the web
  * UI, which is exactly what happened once.
  */

import com.histmersion.config.DbConfig
import com.histmersion.util.database.Migrate
import com.histmersion.util.database.given
import com.histmersion.lint.{LintRule, tolerated}
object TestDb:
  val Env = "HISTMERSION_TEST_DB_URL"

  private[db] def validate(url: String): Either[String, String] =
    val db = name(url)
    if db.endsWith("_test") then Right(url)
    else
      Left(
        s"$Env points at database '$db', which is not a disposable test database (its name must end in " +
          "'_test'). The DB suites write fixtures — never run them against the live corpus. " +
          "Use `just test-db`, then HISTMERSION_TEST_DB_URL=jdbc:postgresql://127.0.0.1:55432/histmersion_test."
      )

  /** Set by the build for a forked test group (build.sbt `dbTestGroups`): the database the group's own
    * `HISTMERSION_TEST_DB_URL` names is created from a connection to this one on first use, and migrated — on the one
    * shared database some earlier suite had always migrated it, and suites came to rely on that (AliasRuleHttpSuite
    * cleans tables before anything of its own migrates).
    */
  val BaseEnv = "HISTMERSION_TEST_DB_BASE_URL"

  /** Set where a skip would hide a hole (CI's DB-backed jobs, B8): without `HISTMERSION_TEST_DB_URL` the DB suites then
    * fail instead of skipping, so a run that lost its database cannot pass as green.
    */
  val RequiredEnv = "HISTMERSION_TEST_DB_REQUIRED"

  /** The URL `env` names, or `None` to skip; an error when the URL is not a test database, or when none is named while
    * `RequiredEnv` asks for one (any value but empty, `0` or `false`).
    */
  private[db] def configured(env: Map[String, String]): Either[String, Option[String]] =
    env.get(Env).filter(_.nonEmpty) match
      case Some(u) => validate(u).map(Some(_))
      case None if env.get(RequiredEnv).exists(v => !Set("", "0", "false")(v.trim.toLowerCase)) =>
        Left(
          s"$RequiredEnv is set, so the DB suites must run, but $Env is not: start a test database (`just test-db`) " +
            "and set it, or unset the requirement to let these suites skip."
        )
      case None => Right(None)

  lazy val url: Option[String] =
    configured(sys.env).fold(e => throw IllegalStateException(e), identity).map { ok =>
      sys.env
        .get(BaseEnv)
        .filter(b => b.nonEmpty && b != ok)
        .foreach(b => ensureExists(validate(b).fold(e => throw IllegalStateException(e), identity), ok))
      ok
    }

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  lazy val dbUrl: Option[DbConfig.Url] = url.map(DbConfig.Url(_))

  private[db] def name(url: String): String = url.split('?').head.reverse.takeWhile(_ != '/').reverse

  /** `CREATE DATABASE` the group's database unless it exists (a group of an earlier run on the same `just test-db`
    * cluster keeps its database, as the base one is kept), then migrate it.
    */
  private def ensureExists(base: String, url: String): Unit =
    val db = name(url)
    require(db.matches("[a-z0-9_]+"), s"unexpected test database name '$db'")
    val c = java.sql.DriverManager.getConnection(
      base,
      sys.env.getOrElse("HISTMERSION_DB_USER", "histmersion"),
      sys.env.getOrElse("HISTMERSION_DB_PASS", "histmersion")
    )
    try
      val rs = c.createStatement().executeQuery(s"SELECT 1 FROM pg_database WHERE datname = '$db'")
      if !rs.next() then
        try { val _ = c.createStatement().execute(s"CREATE DATABASE $db") }
        catch case e: java.sql.SQLException if e.getSQLState == "42P04" => () // created meanwhile
    finally c.close()
    val ds = org.postgresql.ds.PGSimpleDataSource()
    ds.setURL(url)
    ds.setUser(sys.env.getOrElse("HISTMERSION_DB_USER", "histmersion"))
    ds.setPassword(sys.env.getOrElse("HISTMERSION_DB_PASS", "histmersion"))
    val _ = Migrate.run(ds)
