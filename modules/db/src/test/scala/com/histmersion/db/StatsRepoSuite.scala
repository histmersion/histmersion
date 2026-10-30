package com.histmersion.db

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*

import com.augustnagro.magnum.*

/** The About counts against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. Other suites write into the
  * same database at the same time, so each count is compared with its own table read in the same breath, not with a
  * fixed number.
  */
class StatsRepoSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.copy(url = testDb.get))
    Migrate.run(d); d

  private def count(table: String): Int =
    connect(ds)(Sql.raw(s"SELECT count(*)::int FROM $table").frag.query[Int].run().head)

  test("#718: corpus counts read every table they name"):
    val c = StatsRepo.corpus(ds)
    // places and periods are reference data no other suite deletes: they can only have grown
    assert(c.places <= count("places"), c)
    assert(c.periods <= count("periods"), c)
    // #904: the presets are the rows the timeline offers, not every period
    assert(c.presets <= c.periods, c)
    assert(c.presets <= count("periods WHERE preset IS NOT NULL"), c)
    assert(List(c.sources, c.findings, c.articles, c.events).forall(_ >= 0), c)

  test("#718: the job queue by state adds up to the jobs table"):
    val before = count("jobs")
    val j = StatsRepo.jobs(ds)
    val after = count("jobs")
    val total = JobState.values.toList.map(j.of).sum
    assert(total >= before - 1 && total <= after + 1, s"$j vs $before..$after")

  test("storage: the database's size and its largest tables, largest first, at most n"):
    val (name, bytes) = StatsRepo.databaseSize(ds)
    assert(name.endsWith("_test"), name)
    assert(bytes > 0, bytes)
    val top = StatsRepo.largestTables(ds, 5)
    assert(top.nonEmpty && top.size <= 5, top)
    assertEquals(top.map(_.bytes), top.map(_.bytes).sorted.reverse)
    assert(top.forall(t => t.bytes <= bytes), top)
    assert(!top.exists(_.name.startsWith("pg_")), top)
    assertEquals(StatsRepo.largestTables(ds, 0), Nil)
