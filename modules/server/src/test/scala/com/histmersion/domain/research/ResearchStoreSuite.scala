package com.histmersion
package domain.research

import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.domain.library.LibraryStoreSuite.Ticking
import com.histmersion.domain.research.articles.ArticleService
import com.histmersion.domain.research.workflows.ReviewLedgerService
import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.util.database.TestCreators
import com.histmersion.lint.{tolerated, LintRule}

/** A contract of research's services, run once on the in-memory stores and once on the database (a scratch database per
  * test, gated on HISTMERSION_TEST_DB_URL), each holding research's rows alone: no source is stored, so no link is a
  * source link and no critique reason names a citekey. [[fresh]] gives each test empty stores, or none on a run with no
  * such store.
  */
abstract class ResearchStoreSuite extends DbSuite:
  protected def fresh(test: String): Option[ResearchStores]

  protected def stest(title: String)(body: ResearchStores => Unit): Unit =
    test(title)(fresh(title).foreach(body))

object ResearchStoreSuite:

  /** The stores in memory, sharing one state. */
  trait InMemory extends ResearchStoreSuite:
    protected def fresh(test: String): Option[ResearchStores] =
      val state = AtomicReference(ResearchState.empty)
      Some(new ResearchStores:
        val articles = ArticleService.InMemory(state, Ticking())
        val ledgers = ReviewLedgerService.InMemory(state))

  /** The stores on a scratch database of their own per test. */
  trait OnDatabase extends ResearchStoreSuite:
    private val testDb = TestDb.url
    override def munitIgnore: Boolean = testDb.isEmpty
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private lazy val scratch =
      ScratchDatabases(
        s"histmersion_s11c_${getClass.getSimpleName.toLowerCase.take(24)}",
        AppConfig.load().db.copy(url = DbConfig.Url(testDb.get))
      )
    private val made = AtomicLong(0L)
    override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
    protected def fresh(test: String): Option[ResearchStores] =
      testDb.map { _ =>
        val db = Transactor(scratch(made.incrementAndGet().toString))
        new ResearchStores:
          val articles = ArticleService(db, TestCreators.clock)
          val ledgers = ReviewLedgerService(db)
      }
