package com.histmersion
package domain.automation

import com.histmersion.testsupport.ComparedByValue.given

import com.histmersion.config.AppConfig
import com.histmersion.domain.automation.agents.{ApprovalService, RunService}
import com.histmersion.domain.automation.autopilot.AutopilotRowService
import com.histmersion.util.jobs.{JobRuns, JobRunState}
import com.histmersion.domain.Title
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.goals.{BGoalLinkRow, FocusService, GoalLinkTargets, GoalService}
import com.histmersion.domain.automation.managers.ManagerService
import com.histmersion.util.database.Transactor
import com.augustnagro.magnum.*

import java.time.{Clock, Instant, ZoneId, ZoneOffset}
import java.util.concurrent.atomic.{AtomicLong, AtomicReference}
import scala.collection.concurrent.TrieMap
import com.histmersion.domain.automation.Goal
import com.histmersion.db.DbSuite
import com.histmersion.domain.library.workflows.SourceApprovalService
import com.histmersion.domain.automation.agents.AgentStateService
import com.histmersion.db.TestDb
import com.histmersion.config.DbConfig
import com.histmersion.util.database.NewtypeColumns.given

/** A contract of the engine's services, run once on the in-memory stores and once on the database (a scratch database
  * per test, gated on HISTMERSION_TEST_DB_URL): [[fresh]] gives each test empty stores, or none on a run with no such
  * store.
  */
abstract class EngineStoreSuite extends DbSuite:
  protected def fresh(test: String): Option[EngineStores]

  protected def stest(title: String)(body: EngineStores => Unit): Unit =
    test(title)(fresh(title).foreach(body))

object EngineStoreSuite:
  /** A clock that moves on a millisecond each time it is read, from noon of a fixed day: as the database's `now()`, no
    * two statements share a moment.
    */
  final class Moving(start: Instant = Instant.parse("2026-11-01T12:00:00Z")) extends Clock:
    private val n = AtomicLong(0L)
    def getZone: ZoneId = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = this
    def instant(): Instant = start.plusMillis(n.incrementAndGet())

  /** A data source whose connections do nothing: the transaction an in-memory store's `…In` operation is given. */
  val NoDatabase: javax.sql.DataSource =
    def inert[T](c: Class[T]): T =
      given CanEqual[Class[?], Class[?]] = CanEqual.derived
      java.lang.reflect.Proxy
        .newProxyInstance(
          c.getClassLoader,
          Array(c),
          (_, m, _) =>
            if m.getReturnType == classOf[Boolean] then java.lang.Boolean.FALSE
            else if m.getReturnType == classOf[Int] then Integer.valueOf(0)
            else null
        )
        .asInstanceOf[T]
    val connection = inert(classOf[java.sql.Connection])
    java.lang.reflect.Proxy
      .newProxyInstance(
        classOf[javax.sql.DataSource].getClassLoader,
        Array(classOf[javax.sql.DataSource]),
        (_, m, _) => if m.getName == "getConnection" then connection else null
      )
      .asInstanceOf[javax.sql.DataSource]

  /** The stores in memory, sharing one state; the other domains' rows a link names are a map. */
  trait InMemory extends EngineStoreSuite:
    protected def fresh(test: String): Option[EngineStores] =
      val state = AtomicReference(AutomationState.empty)
      val clock = Moving()
      val titles = TrieMap.empty[(Goal.LinkKind, BGoalLinkRow.Ref), GoalLinkTargets.LinkTitle]
      val targets = new GoalLinkTargets:
        def title(kind: Goal.LinkKind, ref: BGoalLinkRow.Ref): Option[GoalLinkTargets.LinkTitle] =
          titles.get((kind, ref))
        def queueList(ref: BGoalLinkRow.Ref): Option[GoalLinkTargets.QueueLines] = None
      val jobs = JobRuns.InMemory(AtomicReference(Map.empty[Run.Id, JobRunState]), () => Instant.now(clock))
      Some(new EngineStores:
        val autopilot = AutopilotRowService.InMemory(state, clock)
        val goals = GoalService.InMemory(state, clock, targets)
        val approvals = ApprovalService.InMemory(state, jobs, clock)
        val sourceApprovals = SourceApprovalService.InMemory(state, approvals, clock)
        val runs = jobs
        val agentRuns = RunService.InMemory(state, jobs, clock)
        val managers = ManagerService.InMemory(state, jobs, clock)
        val focus = FocusService.InMemory(state, clock)
        val agentState = AgentStateService.InMemory(state, clock)
        def article(slug: Slug, title: Title): Unit =
          titles.update((Goal.LinkKind.Article, BGoalLinkRow.Ref(slug.value)), GoalLinkTargets.LinkTitle(title.value))
        def source(citekey: Citekey, title: Title): Unit =
          titles.update(
            (Goal.LinkKind.Source, BGoalLinkRow.Ref(citekey.value)),
            GoalLinkTargets.LinkTitle(title.value)
          )
        def within[A](work: DbCon ?=> A): A = connect(NoDatabase)(work))

  /** The stores on a scratch database of their own per test. */
  trait OnDatabase extends EngineStoreSuite:
    private val testDb = TestDb.url
    override def munitIgnore: Boolean = testDb.isEmpty
    private lazy val scratch =
      ScratchDatabases(
        s"histmersion_s7c_${getClass.getSimpleName.toLowerCase.take(24)}",
        AppConfig.load().db.copy(url = DbConfig.Url(testDb.get))
      )
    private val made = AtomicLong(0L)
    override def afterSuite(): Unit = if testDb.isDefined then scratch.dropAll()
    protected def fresh(test: String): Option[EngineStores] =
      testDb.map { _ =>
        val ds = scratch(made.incrementAndGet().toString)
        val db = Transactor(ds)
        new EngineStores:
          val autopilot = AutopilotRowService(db)
          val goals = GoalService(db)
          val approvals = ApprovalService(db, Clock.systemUTC())
          val sourceApprovals = SourceApprovalService(db, approvals, Clock.systemUTC())
          val runs = JobRuns(db)
          val agentRuns = RunService(db)
          val managers = ManagerService(db)
          val focus = FocusService(db)
          val agentState = AgentStateService(db)
          def article(slug: Slug, title: Title): Unit =
            connect(ds)(
              sql"INSERT INTO articles (slug, title, kind, body) VALUES ($slug, $title, 'concept', '')".update
                .run()
            ): Unit
          def source(citekey: Citekey, title: Title): Unit =
            connect(ds)(
              sql"INSERT INTO sources (citekey, title) VALUES ($citekey, $title)".update.run()
            ): Unit
          def within[A](work: DbCon ?=> A): A = transact(ds)(work)
      }
