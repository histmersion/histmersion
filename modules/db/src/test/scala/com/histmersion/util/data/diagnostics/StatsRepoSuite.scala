package com.histmersion
package util.data.diagnostics

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.api.TableSize
import com.histmersion.config.*
import com.histmersion.domain.*

import com.histmersion.util.database.{Migrate, Sql}

import com.augustnagro.magnum.*
import com.histmersion.db.*
import com.histmersion.api.instance.CorpusCounts
import com.histmersion.api.jobs.{JobCounts, JobState}
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.util.data.TableName
import com.histmersion.util.jobs.JobRepo
import com.histmersion.api.ByteCount

/** The About counts against a real Postgres (HISTMERSION_TEST_DB_URL); skipped otherwise. Other suites write into the
  * same database at the same time, so each count is compared with its own table read in the same breath, not with a
  * fixed number.
  */
class StatsRepoSuite extends DbSuite:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty

  private lazy val ds =
    val base = AppConfig.load().db
    val d = testPool(base.modify(_.url).setTo(DbConfig.Url(testDb.get)))
    Migrate.run(d); d

  private def count(table: String): Int =
    connect(ds)(Sql.raw(Sql.Text(s"SELECT count(*)::int FROM $table")).frag.query[Int].run().head)

  test("#718: corpus counts read every table they name"):
    val c = connect(ds)(
      CorpusCounts(
        CorpusCounts.Sources(StatsRepo.rows(TableName("sources")).value.toInt),
        CorpusCounts.Findings(StatsRepo.rows(TableName("findings")).value.toInt),
        CorpusCounts.Articles(StatsRepo.rows(TableName("articles")).value.toInt),
        CorpusCounts.Events(StatsRepo.rows(TableName("events")).value.toInt),
        CorpusCounts.Places(StatsRepo.rows(TableName("places")).value.toInt),
        CorpusCounts.Periods(StatsRepo.rows(TableName("periods")).value.toInt),
        CorpusCounts.Presets(StatsRepo.rowsWith(TableName("periods"), StatsRepo.ColumnName("preset")).value.toInt)
      )
    )
    // places and periods are reference data no other suite deletes: they can only have grown
    assert(c.places.value <= count("places"), c)
    assert(c.periods.value <= count("periods"), c)
    // #904: the presets are the rows the timeline offers, not every period
    assert(c.presets.value <= c.periods.value, c)
    assert(c.presets.value <= count("periods WHERE preset IS NOT NULL"), c)
    assert(List(c.sources.value, c.findings.value, c.articles.value, c.events.value).forall(_ >= 0), c)

  test("#718: the job queue by state adds up to the jobs table"):
    val before = count("jobs")
    val j = JobCounts.of(connect(ds)(JobRepo.states).map((state, n) => (JobState.Wire(state.value), JobCounts.Count(n.value))))
    val after = count("jobs")
    val total = JobState.values.toList.map(state => j.of(state).value).sum
    assert(total >= before - 1 && total <= after + 1, s"$j vs $before..$after")

  test("storage: the database's size and its largest tables, largest first, at most n"):
    val (name, bytes) = connect(ds)(StatsRepo.databaseSize)
    assert(name.value.endsWith("_test"), name)
    assert(bytes.value > 0, bytes)
    val top = connect(ds)(StatsRepo.tableBytes).take(5).map((table, size) => TableSize(TableSize.Name(table.value), ByteCount(size.value)))
    assert(top.nonEmpty && top.size <= 5, top)
    assertEquals(top.map(_.bytes), top.map(_.bytes).sortBy(b => b.value: Long).reverse)
    assert(top.forall(t => t.bytes.value <= bytes.value), top)
    assert(!top.exists(_.name.value.startsWith("pg_")), top)
