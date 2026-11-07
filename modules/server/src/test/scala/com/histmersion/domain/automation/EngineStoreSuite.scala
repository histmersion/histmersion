package com.histmersion.domain.automation

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

/** A contract of the engine's services, run once on the in-memory stores and once on the database (a scratch database
  * per test, gated on HISTMERSION_TEST_DB_URL): [[fresh]] gives each test empty stores, or none on a run with no such
  * store.
  */
abstract class EngineStoreSuite extends com.histmersion.db.DbSuite:
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
      val titles = TrieMap.empty[(Goal.LinkKind, BGoalLinkRow.Ref), GoalLinkTargets.TitleResult]
      val targets = new GoalLinkTargets:
        def title(kind: Goal.LinkKind, ref: BGoalLinkRow.Ref): Option[GoalLinkTargets.TitleResult] = titles.get((kind, ref))
        def queueList(ref: BGoalLinkRow.Ref): Option[GoalLinkTargets.QueueListResult] = None
      val jobs = JobRuns.InMemory(AtomicReference(Map.empty[Run.Id, JobRunState]), () => Instant.now(clock))
      Some(new EngineStores:
        val autopilot = AutopilotRowService.InMemory(state, clock)
        val goals = GoalService.InMemory(state, clock, targets)
        val approvals = ApprovalService.InMemory(state, jobs, clock)
        val sourceApprovals = com.histmersion.domain.library.workflows.SourceApprovalService.InMemory(state, approvals, clock)
        val runs = jobs
        val agentRuns = RunService.InMemory(state, jobs, clock)
        val managers = ManagerService.InMemory(state, jobs, clock)
        val focus = FocusService.InMemory(state, clock)
        val agentState = com.histmersion.domain.automation.agents.AgentStateService.InMemory(state, clock)
        def article(slug: Slug, title: Title): Unit =
          titles.update((Goal.LinkKind.Article, BGoalLinkRow.Ref(slug.value)), GoalLinkTargets.TitleResult(title.value))
        def source(citekey: Citekey, title: Title): Unit =
          titles.update((Goal.LinkKind.Source, BGoalLinkRow.Ref(citekey.value)), GoalLinkTargets.TitleResult(title.value))
        def within[A](work: DbCon ?=> A): A = connect(NoDatabase)(work))

  /** The stores on a scratch database of their own per test. */
  trait OnDatabase extends EngineStoreSuite:
    private val testDb = com.histmersion.db.TestDb.url
    override def munitIgnore: Boolean = testDb.isEmpty
    private lazy val scratch =
      ScratchDatabases(
        s"histmersion_s7c_${getClass.getSimpleName.toLowerCase.take(24)}",
        AppConfig.load().db.copy(url = com.histmersion.config.DbConfig.Url(testDb.get))
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
          val sourceApprovals = com.histmersion.domain.library.workflows.SourceApprovalService(db, approvals, Clock.systemUTC())
          val runs = JobRuns(db)
          val agentRuns = RunService(db)
          val managers = ManagerService(db)
          val focus = FocusService(db)
          val agentState = com.histmersion.domain.automation.agents.AgentStateService(db)
          def article(slug: Slug, title: Title): Unit =
            connect(ds)(
              sql"INSERT INTO articles (slug, title, kind, body) VALUES (${slug.value}, ${title.value}, 'concept', '')".update.run()
            ): Unit
          def source(citekey: Citekey, title: Title): Unit =
            connect(ds)(sql"INSERT INTO sources (citekey, title) VALUES (${citekey.value}, ${title.value})".update.run()): Unit
          def within[A](work: DbCon ?=> A): A = transact(ds)(work)
      }
