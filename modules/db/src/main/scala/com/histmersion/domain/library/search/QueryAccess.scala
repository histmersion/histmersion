package com.histmersion
package domain.library.search

import com.histmersion.config.{DbConfig, QueryConfig}
import com.histmersion.util.database.Db
import com.zaxxer.hikari.HikariDataSource
import java.sql.SQLException
import javax.sql.DataSource
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure

/** The read-only `histmersion_query` role behind `POST /api/query`: its pool and its provisioning. */
object QueryAccess:
  type Problem = Problem.T
  object Problem extends RefinedType[String, Pure]

  /** The READ-ONLY pool behind `POST /api/query` — the `histmersion_query` role of migration V011, which holds nothing
    * but SELECT. Deliberately tiny: the hatch is an interactive escape valve, not a serving path.
    */

  def pool(db: DbConfig, q: QueryConfig): HikariDataSource =
    Db.pooled(Db.JdbcUrl(db.url.value), Db.Login(q.user.value), Db.Password(q.password.value), Db.PoolMax(math.min(4, math.max(1, db.pool.max.value))), Db.PoolName("histmersion-query"))

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
  def provision(admin: DataSource, q: QueryConfig): Either[Problem, Unit] =
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
          create.setString(1, q.user.value); create.setString(2, q.user.value)
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
        ("ALTER ROLE %I LOGIN PASSWORD %L", q.password.value),
        (s"ALTER ROLE %I SET statement_timeout = %L", math.max(1, q.timeoutMs.value).toString),
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
            ps.setString(1, q.user.value)
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
    catch case e: SQLException => Left(Problem(Option(e.getMessage).getOrElse(e.getClass.getSimpleName)))
    finally
      try c.close()
      catch case _: SQLException => ()
