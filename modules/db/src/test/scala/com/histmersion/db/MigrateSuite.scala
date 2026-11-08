package com.histmersion
package db

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.util.database.Migrate
import com.histmersion.util.database.given
class MigrateSuite extends munit.FunSuite:
  test("migration index lists every V*.sql, in order, and each is loadable"):
    val names = Migrate.migrationNames
    assertEquals(names, List(s"V${Migrate.SquashedAt}__schema.sql"), "one migration since the squash")
    assertEquals(names, names.sortBy(_.value))
    assert(names.forall(_.value.matches("""V\d{3}__[a-z0-9_]+\.sql""")), names)
    names.foreach(n => assert(getClass.getClassLoader.getResource(s"db/migration/$n") != null, n))

  test("a collision with a concurrent migration is retried; anything else is not (#828)"):
    import java.sql.SQLException
    val wrapped = org.flywaydb.core.api.FlywayException(
      "Migration V011 failed",
      SQLException("duplicate key value violates unique constraint \"pg_authid_rolname_index\"", "23505")
    )
    assert(Migrate.transient(wrapped))
    assert(Migrate.transient(SQLException("role \"histmersion_query\" already exists", "42710")))
    assert(Migrate.transient(SQLException("tuple concurrently updated", "XX000")))
    assert(!Migrate.transient(SQLException("internal error", "XX000")))
    assert(!Migrate.transient(SQLException("syntax error", "42601")))
    assert(!Migrate.transient(RuntimeException("boom")))

  test("retrying: a transient failure is run again, a real one is thrown at once"):
    var calls = 0 // counting the attempts of a closure is what a var is for
    val ok = Migrate.retrying(Migrate.RetryAttempts(5)) {
      calls += 1
      if calls < 3 then throw java.sql.SQLException("tuple concurrently updated", "XX000") else "done"
    }
    assertEquals((ok, calls), ("done", 3))
    calls = 0
    intercept[java.sql.SQLException](Migrate.retrying(Migrate.RetryAttempts(5)) { calls += 1; throw java.sql.SQLException("no", "42601") })
    assertEquals(calls, 1)
    calls = 0
    intercept[java.sql.SQLException](Migrate.retrying(Migrate.RetryAttempts(2)) { calls += 1; throw java.sql.SQLException("x", "40P01") })
    assertEquals(calls, 2)
