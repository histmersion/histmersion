package com.histmersion
package domain.library.acquisition

import com.histmersion.config.{AppConfig, DbConfig}
import com.histmersion.domain.automation.Goal
import com.histmersion.domain.automation.worklists.{QueueService, WorkListState}
import com.histmersion.domain.library.{LibraryState, LibraryStoreSuite}
import com.histmersion.domain.library.sources.SourceService
import com.histmersion.domain.library.sources.referrers.SourceReferrers
import com.histmersion.util.database.Transactor

import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.util.database.TestPermits.given
import com.histmersion.util.database.TestCreators.given
import com.histmersion.util.database.TestCreators
import com.histmersion.lint.{tolerated, LintRule}

/** A contract of the acquisition's services, run once in memory and once on the database (a scratch database per test,
  * gated on HISTMERSION_TEST_DB_URL), each holding the library's rows alone.
  */
abstract class AcquisitionStoreSuite extends DbSuite:
  protected def fresh(test: String): Option[AcquisitionStores]

  protected def stest(title: String)(body: AcquisitionStores => Unit): Unit =
    test(title)(fresh(title).foreach(body))

object AcquisitionStoreSuite:

  /** The services in memory, the acquisition's sharing one state, the library's another. */
  trait InMemory extends AcquisitionStoreSuite:
    protected def fresh(test: String): Option[AcquisitionStores] =
      val state = AtomicReference(AcquisitionState.empty)
      val clock = LibraryStoreSuite.Ticking()
      val src = SourceService.InMemory(AtomicReference(LibraryState.empty), clock, () => LibraryStoreSuite.Settings)
      val q = QueueService.InMemory(AtomicReference(WorkListState.empty), clock)
      Some(new AcquisitionStores:
        val assessments = AssessmentService.InMemory(state, clock)
        val candidates = CandidateService.InMemory(state, clock)
        val quotas = UpstreamQuotaService.InMemory(state)
        val attempts = AcquireCheckService.InMemory(state, q, src)
        val sources = src
        val queues = q
        def goal(slug: String) = ())

  /** The services on a scratch database of their own per test. */
  trait OnDatabase extends AcquisitionStoreSuite:
    private val testDb = TestDb.url
    override def munitIgnore: Boolean = testDb.isEmpty
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    private lazy val scratch =
      ScratchDatabases(
        s"histmersion_s10q_${getClass.getSimpleName.toLowerCase.take(24)}",
        AppConfig.load().db.copy(url = DbConfig.Url(testDb.get))
      )
    private val made = AtomicLong(0L)
    override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
    protected def fresh(test: String): Option[AcquisitionStores] =
      testDb.map { _ =>
        val db = Transactor(scratch(made.incrementAndGet().toString))
        new AcquisitionStores:
          val assessments = AssessmentService(db)
          val candidates = CandidateService(db)
          val quotas = UpstreamQuotaService(db)
          val attempts = AcquireCheckService(db)
          val sources = SourceService(db, SourceReferrers(Nil), () => LibraryStoreSuite.Settings, TestCreators.clock)
          val queues = QueueService(db, TestCreators.clock)
          @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
          def goal(slug: String) =
            val _ = GoalService(db, TestCreators.clock)
              .put(
                Goal.Slug.applyUnsafe(slug),
                api.automation.Goal.Put(api.automation.Goal.Title(s"Goal $slug"))
              )
      }
