package com.histmersion
package server.agents
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.{AgentsControllers, LlmClient}
import com.histmersion.domain.automation.managers.Zero

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.domain.automation.goals.GoalLinkRepo
import com.histmersion.domain.automation.managers.{ManagerLight, ManagerRepo}
import io.scalaland.chimney.dsl.*
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.time.Instant
import com.histmersion.db.*
import com.histmersion.api.automation.{Goal, Managers, Queue}
import com.histmersion.api.library.{CandidateSweep, CandidateWatch, FollowupRow, WorkRef}
import com.histmersion.api.library.Source
import com.histmersion.api.library.Candidates
import com.histmersion.api.library.{Doi as ApiDoi, Title as ApiTitle}
import com.histmersion.domain.automation.Goal as DomainGoal
import com.histmersion.domain.library.{Citekey, Doi}
import com.histmersion.domain.library.search.Search
import com.histmersion.Year
import com.histmersion.RowCount
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.domain.library.Edge
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.server.views.WorkListView
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.Approval
import com.histmersion.api.Api
import com.histmersion.util.database.TestPermits.given
import com.histmersion.domain.Creator
import com.histmersion.util.database.TestCreators.given
import com.histmersion.lint.{LintRule, tolerated}

/** The goal sweeps and the goal schedules, against a `*_test` database: a currency check (assess and followups over a
  * goal's sources; a disagreeing paper in the corpus becomes ONE edge proposal, one outside it ONE acquisition item,
  * however often it runs), an acquisition sweep (never queues what the corpus or the queue has; the ranking model may
  * only pick listed candidates), and — since phase 3 — the schedules as the goal's watcher (set through the goal: a
  * paused watcher; the `goal_schedules` rows migrated into it once, logged; a scheduled run that pauses still leaves a
  * backlog item). The watcher's own runs and crons: [[ManagerPhase3Suite]]. Skipped without HISTMERSION_TEST_DB_URL.
  */
@tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
@tolerated(LintRule.SilentCompare, "pending G53 (no comparison of unrelated types)")
class SchedulerRunSuite extends DbSuite with db.Bounded with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Goal = DomainGoal.Slug("zzagent-sched")
  private val GoalTarget = RunRequest.Target("zzagent-sched")
  private val GoalRef = api.automation.Goal.Ref("zzagent-sched")
  private val Ck = Citekey("zzagent2000_old")
  private val Ck2 = Citekey("zzagent2024_new")
  private val ApiCk2 = api.library.Citekey("zzagent2024_new")
  private val CkRef = GoalLink.Ref("zzagent2000_old")
  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private lazy val baseCfg =
    val b = AppConfig.load()
    b.modify(_.db)
      .setTo(b.db.modify(_.url).setTo(DbConfig.Url(testDb.get)))
      .modify(_.agents)
      .setTo(
        b.agents
          .modify(_.enabled)
          .setTo(AgentsConfig.Enabled(true))
          .modify(_.prices)
          .setTo(AgentsConfig.Prices("scripted=1/2"))
          .modify(_.roles)
          .setTo(b.agents.roles.modify(_.planner).setTo(AgentRolesConfig.Planner(Model.wire.value)))
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsControllers, ServerWiring, StubUpstream) =
    val deps = Deps(cfg, ds)
    val h = ServerWiring(deps)
    val a = AgentsControllers(ServerEngineSite(deps, h), (_, _) => Right(c))
    val up = StubUpstream()
    a.attach(up)
    (a, h, up)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM approvals WHERE citekey LIKE 'zzagent%' OR subject LIKE 'zzagent%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(h: ServerWiring): Unit =
    assert(
      h.library.sources
        .put(
          Ck,
          Source.Put(
            title = api.library.Title("Old tin"),
            year = (Some(2000)).map(api.catalogue.Year.apply),
            doi = (Some("10.5555/zzagent.old")).map(api.library.Doi.apply)
          )
        )
        .isRight
    )
    assert(
      h.library.sources
        .put(
          Ck2,
          Source.Put(
            title = api.library.Title("New tin"),
            year = (Some(2024)).map(api.catalogue.Year.apply),
            doi = (Some("10.5555/zzagent.new")).map(api.library.Doi.apply)
          )
        )
        .isRight
    )
    assert(
      h.goals
        .put(
          Goal,
          api.automation.Goal.Put(api.automation.Goal.Title("Zz scheduled tin"))
        )
        .isRight
    )
    assert(transact(ds)(GoalLinkRepo.link(Goal, LinkKind.Source, CkRef, None)))

  private def start(a: AgentsControllers, req: RunRequest): RunInfo =
    val run = a.startRun(req).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.engine.execute(ApiIds.run(run.id))
    connect(ds)(AgentRepo.get(run.id)).get

  private val citing = List(
    FollowupRow(
      title = Some(api.library.Title("New tin")),
      year = Some(api.catalogue.Year(2024)),
      doi = Some(api.library.Doi("10.5555/zzagent.new")),
      contexts = List(FollowupRow.Contexts("we refute the old dating")),
      proposedKind = FollowupRow.ProposedKind("refutes"),
      citekey = Some(ApiCk2)
    ),
    FollowupRow(
      title = Some(api.library.Title("Zzagent outside critique")),
      year = Some(api.catalogue.Year(2022)),
      doi = Some(api.library.Doi("10.5555/zzagent.out")),
      proposedKind = FollowupRow.ProposedKind("challenges")
    ),
    FollowupRow(
      title = Some(api.library.Title("Zzagent agreeing")),
      year = Some(api.catalogue.Year(2021)),
      doi = Some(api.library.Doi("10.5555/zzagent.ok")),
      proposedKind = FollowupRow.ProposedKind("confirms")
    )
  )

  test(
    "currency-check: assess and followups over the goal's sources; one edge proposal, one acquisition item — never twice"
  ):
    val (a, h, up) = handlers(ScriptedClient(Model)); seed(h)
    up.citing = Map(Ck.value -> citing)
    val run = start(a, RunRequest(Agents.RunRequest.Pipeline("currency-check"), GoalTarget))
    assertEquals(run.status, "done", run.error)
    import scala.jdk.CollectionConverters.*
    assertEquals(up.calls.asScala.toList, List(s"assess $Ck", s"followups $Ck"))
    val open = a.approvalsList(None, None).left.map(Api.Error(_)).toOption.get.filter(_.citekey == ApiCk2)
    assertEquals(
      open.map(x => (x.kind, x.edgeKind, x.target.map(_.value: String))),
      List(("edge", Some("refutes"), Some(Ck)))
    )
    assert(open.head.evidence.value.contains("we refute the old dating"), open.head.evidence)
    assertEquals(
      h.library.citations.neighbors(Ck2, List(Edge.Kind.Wire("refutes"))),
      Nil,
      "nothing applied before approval"
    )
    def queued = h.workListView
      .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(true))
      .toOption
      .get
      .items
      .filter(_.text.value.contains("zzagent.out"))
    assertEquals(queued.size, 1)
    assert(
      connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.QueueItem && l.ref == GoalLink.Ref(queued.head.id.toString)
      )
    )
    assert(run.progress.exists(_.value.contains("1 edge(s) proposed, 1 paper(s) queued")), run.progress)

    // again (as a schedule would): the open proposal and the queued paper are not doubled
    val again = start(a, RunRequest(Agents.RunRequest.Pipeline("currency-check"), GoalTarget))
    assertEquals(again.status, "done", again.error)
    assertEquals(
      a.approvalsList(Some(ApprovalQuery.State("all")), None)
        .left
        .map(Api.Error(_))
        .toOption
        .get
        .count(_.citekey == ApiCk2),
      1
    )
    assertEquals(queued.size, 1)
    // a rejected proposal is not asked again either
    assert(a.decide(ApiIds.approval(open.head.id), Approval.Outcome("reject"), DecisionRequest()).isRight)
    assertEquals(start(a, RunRequest(Agents.RunRequest.Pipeline("currency-check"), GoalTarget)).status, "done")
    assertEquals(
      a.approvalsList(Some(ApprovalQuery.State("all")), None)
        .left
        .map(Api.Error(_))
        .toOption
        .get
        .count(_.citekey == ApiCk2),
      1
    )

  private def work(
      doi: api.library.Doi,
      title: api.library.Title,
      inDb: WorkRef.InDb = WorkRef.InDb(false)
  ) =
    WorkRef(
      doi = Some(doi),
      title = Some(title),
      year = (Some(2023)).map(api.catalogue.Year.apply),
      authors = (List("Zz")).map(api.library.AuthorName.apply),
      inDb = inDb
    )

  test(
    "acquisition-sweep: the goal's queries searched, corpus and queue left out, the ranking model's picks queued once"
  ):
    val c = ScriptedClient(Model)
    c.scripts ++= List(
      Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"},{"doi":"10.9999/invented","why":"?"}]}""")),
      Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"}]}"""))
    )
    val (a, h, up) = handlers(c); seed(h)
    assert(
      a.addGoalLink(
        Goal,
        LinkAdd(LinkKind.Query, api.automation.Goal.Plans.LinkAdd.Ref("zzagent tin trade"))
      ).isRight
    )
    assert(
      h.workLists
        .addOverRest(
          WorkListControllers.ListName("acquire"),
          Queue.QueueAdd(Queue.QueueItem.Text("Zzagent queued. https://doi.org/10.5555/zzagent.q"))
        )
        .isRight
    )
    up.found = Map(
      "zzagent tin trade" -> List(
        work(ApiDoi("10.5555/zzagent.a"), ApiTitle("A")),
        work(ApiDoi("10.5555/zzagent.b"), ApiTitle("B")),
        work(ApiDoi("10.5555/zzagent.in"), ApiTitle("In"), inDb = WorkRef.InDb(true)),
        work(ApiDoi("10.5555/zzagent.q"), ApiTitle("Queued"))
      )
    )
    val run = start(a, RunRequest(Agents.RunRequest.Pipeline("acquisition-sweep"), GoalTarget))
    assertEquals(run.status, "done", run.error)
    // the model saw only the two new candidates, and its invented pick was sent back
    val offered = c.steps.head.head.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.get
    assert(
      offered.contains("10.5555/zzagent.a") && offered.contains("10.5555/zzagent.b") && !offered.contains(
        "zzagent.in"
      ) &&
        !offered.contains("zzagent.q"),
      offered
    )
    assertEquals(c.steps.size, 2, "the invented DOI was refused and the ranking asked again")
    val items = h.workListView
      .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(true))
      .toOption
      .get
      .items
      .filter(_.text.value.contains("zzagent.a"))
    assertEquals(items.size, 1)
    assert(items.head.text.value.contains("for goal zzagent-sched: core study"), items.head.text)
    assert(run.progress.exists(_.value.contains("2 new candidate(s), 1 picked, 1 queued")), run.progress)

    // the next sweep finds nothing new: the picked work is now queued, the other still on offer
    c.scripts += Seq(text("""{"picks":[]}"""))
    val again = start(a, RunRequest(Agents.RunRequest.Pipeline("acquisition-sweep"), GoalTarget))
    assertEquals(again.status, "done", again.error)
    assertEquals(
      h.workListView
        .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(true))
        .toOption
        .get
        .items
        .count(_.text.value.contains("zzagent.a")),
      1
    )

  /** The candidate search (#3761) answered from memory: what the goal's watches stored, and the marks set. */
  final private class CandidateUpstream extends AgentUpstream:
    // `var`: the stored candidates, changed by the marks
    @volatile var stored: List[Source.Candidate] = Nil
    val sweeps = java.util.concurrent.atomic.AtomicInteger(0)
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def meta(doi: Doi) = Left(Problem.Unclassified(Legacy.Text("not stubbed")))
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def refs(citekey: Citekey, apply: AgentUpstream.RefsApply)(using Creator.New) = Left(
      Problem.Unclassified(Legacy.Text("not stubbed"))
    )
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def assess(citekey: Citekey, apply: AgentUpstream.AssessApply)(using Creator.New) = Left(
      Problem.Unclassified(Legacy.Text("not stubbed"))
    )
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def followups(id: AgentUpstream.FollowupsId, since: Option[Year], limit: Option[FollowupsInput.Limit])(using
        Creator.New
    ) = Left(
      Problem.Unclassified(Legacy.Text("not stubbed"))
    )
    @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
    def discover(query: Search.Query, limit: AgentUpstream.Limit) = Left(
      Problem.Unclassified(Legacy.Text("the live search must not run when the candidate search is connected"))
    )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def sweepCandidates(goal: DomainGoal.Slug, dryRun: AgentUpstream.SweepCandidatesDryRun)(using
        Creator.New
    ) =
      sweeps.incrementAndGet()
      Right(
        CandidateSweep(
          CandidateSweep.Goal(goal.value),
          List(
            CandidateWatch(
              CandidateWatch.Goal(goal.value),
              CandidateWatch.Kind("query"),
              CandidateWatch.Subject("zzagent tin trade"),
              CandidateWatch.Index("openalex")
            )
          ),
          CandidateSweep.Found(stored.size),
          CandidateSweep.Added(0),
          CandidateSweep.Again(stored.size),
          Map.empty,
          CandidateSweep.Unseen(stored.count(_.state == Source.Candidate.State("new"))),
          CandidateSweep.DryRun(dryRun.value)
        )
      )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def candidates(goal: DomainGoal.Slug, state: Candidates.State.Wire, limit: AgentUpstream.Limit) = Right(
      stored.filter(_.state == Source.Candidate.State(state.value)).take(limit.value)
    )
    @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
    override def markCandidates(ids: List[Source.Candidate.Id], state: Candidates.State.Wire) =
      stored =
        stored.map(c => if ids.contains(c.id) then c.modify(_.state).setTo(Source.Candidate.State(state.value)) else c)
      Right(RowCount(ids.size))

  test(
    "#3761 acquisition-sweep over the stored candidates: the unseen ranked, the picks queued and the rest seen; none new, no model"
  ):
    val c = ScriptedClient(Model)
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"}]}"""))
    val (a, h, _) = handlers(c); seed(h)
    val up = CandidateUpstream()
    a.attach(up)
    up.stored = List(
      Source.Candidate(
        Source.Candidate.Id(1),
        Source.Candidate.Goal(Goal.value),
        api.library.Doi("10.5555/zzagent.a"),
        api.library.Title("A"),
        (Some(2026)).map(api.catalogue.Year.apply),
        (List("Zz")).map(api.library.AuthorName.apply),
        foundBy = List(Source.Candidate.FoundBy("openalex query \"zzagent tin trade\""))
      ),
      Source.Candidate(
        Source.Candidate.Id(2),
        Source.Candidate.Goal(Goal.value),
        api.library.Doi("10.5555/zzagent.b"),
        api.library.Title("B"),
        (Some(2026)).map(api.catalogue.Year.apply),
        (List("Zz")).map(api.library.AuthorName.apply),
        foundBy = List(Source.Candidate.FoundBy("crossref query \"zzagent tin trade\""))
      )
    )
    val run = start(a, RunRequest(Agents.RunRequest.Pipeline("acquisition-sweep"), GoalTarget))
    assertEquals(run.status, "done", run.error)
    assertEquals(c.steps.size, 1)
    val offered = c.steps.head.head.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.get
    assert(offered.contains("10.5555/zzagent.a") && offered.contains("found by \"crossref query"), offered)
    assertEquals(
      up.stored.map(c => c.doi -> c.state).toMap,
      Map("10.5555/zzagent.a" -> "queued", "10.5555/zzagent.b" -> "seen")
    )
    assert(
      h.workListView
        .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(true))
        .toOption
        .get
        .items
        .exists(_.text.value.contains("zzagent.a"))
    )
    // the next sweep: every candidate decided — no model is asked
    val again = start(a, RunRequest(Agents.RunRequest.Pipeline("acquisition-sweep"), GoalTarget))
    assertEquals(again.status, "done", again.error)
    assertEquals(c.steps.size, 1, "no ranking without a new candidate")
    assert(again.progress.exists(_.value.contains("nothing new")), again.progress)

  // ── goal schedules are watchers (phase 3) ────────────────────────────────────────────────────────

  private val Now = Instant.parse("2026-10-04T12:00:00Z")

  private def cleanManagers(): Unit =
    connect(ds):
      sql"DELETE FROM agent_managers WHERE id LIKE 'watch:zzagent%'".update.run()
      sql"DELETE FROM goal_schedules WHERE goal_slug LIKE 'zzagent%'".update.run()

  test(
    "schedules: a cron is checked, too frequent is refused; setting one makes a PAUSED watcher of the goal with that sweep"
  ):
    cleanManagers()
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(h)
    assert(
      a.setSchedule(
        Goal,
        api.automation.Goal.Plans.ScheduleKind.Wire("currency"),
        ScheduleSet(api.automation.Goal.Plans.ScheduleSet.Cron("not cron")),
        Now
      ).left
        .exists(_.problems.sentences.mkString("; ").contains("five fields"))
    )
    assert(
      a.setSchedule(
        Goal,
        api.automation.Goal.Plans.ScheduleKind.Wire("currency"),
        ScheduleSet(api.automation.Goal.Plans.ScheduleSet.Cron("*/5 * * * *")),
        Now
      ).left
        .exists(_.problems.sentences.mkString("; ").contains("min-interval"))
    )
    assert(
      a.setSchedule(
        Goal,
        api.automation.Goal.Plans.ScheduleKind.Wire("weekly"),
        ScheduleSet(api.automation.Goal.Plans.ScheduleSet.Cron("@daily")),
        Now
      ).isLeft
    )
    val s = a
      .setSchedule(
        Goal,
        api.automation.Goal.Plans.ScheduleKind.Wire("currency"),
        ScheduleSet(
          api.automation.Goal.Plans.ScheduleSet.Cron("@daily"),
          budgetUsd = Some(api.automation.Goal.Plans.ScheduleSet.BudgetUsd(0.5))
        ),
        Now
      )
      .toOption
      .get
    assertEquals(
      (s.kind, s.cron, s.enabled, s.budgetUsd, s.nextRunAt),
      (ScheduleKind.Currency, "@daily", true, Some(0.5), Some("2026-10-05T00:00:00Z"))
    )
    assert(s.lastNote.exists(_.value.contains("paused")), s.lastNote)
    val w = a.managing.get(Managers.watchId(GoalRef).transformInto[ManagerLight.Id]).toOption.get.manager
    assertEquals(
      (w.kind, w.state, w.cron, w.config.sweeps, w.config.budgetUsd),
      ("watch", "paused", Some("@daily"), Some(List("currency")), Some(0.5))
    )
    // the acquisition sweep joins it; the cron is the watcher's, shared
    val acq = a
      .setSchedule(
        Goal,
        api.automation.Goal.Plans.ScheduleKind.Wire("acquisition"),
        ScheduleSet(api.automation.Goal.Plans.ScheduleSet.Cron("@weekly")),
        Now
      )
      .toOption
      .get
    assertEquals((acq.kind, acq.cron), (ScheduleKind.Acquisition, "@weekly"))
    assertEquals(
      a.goalView(Goal).toOption.get.schedules.map(s => (s.kind, s.cron)),
      List((ScheduleKind.Acquisition, "@weekly"), (ScheduleKind.Currency, "@weekly"))
    )
    val off = a
      .setSchedule(
        Goal,
        api.automation.Goal.Plans.ScheduleKind.Wire("currency"),
        ScheduleSet(
          api.automation.Goal.Plans.ScheduleSet.Cron("@weekly"),
          enabled = api.automation.Goal.Plans.ScheduleSet.Enabled(false)
        ),
        Now
      )
      .toOption
      .get
    assertEquals(off.enabled, false)
    assertEquals(a.goalView(Goal).toOption.get.schedules.map(_.kind), List(ScheduleKind.Acquisition))
    assertEquals(
      a.removeSchedule(Goal, api.automation.Goal.Plans.ScheduleKind.Wire("acquisition")).map(_.removed),
      Right(true)
    )
    assertEquals(
      a.removeSchedule(Goal, api.automation.Goal.Plans.ScheduleKind.Wire("acquisition")).map(_.removed),
      Right(false)
    )
    assertEquals(a.goalView(Goal).toOption.get.schedules, Nil)
    assert(a.managing.get(Managers.watchId(GoalRef).transformInto[ManagerLight.Id]).isRight, "the watcher stays")
    cleanManagers()

  test(
    "migration: goal_schedules rows become the goal's paused watcher (cron, sweeps, budget, next firing), logged, once"
  ):
    cleanManagers()
    val (a, h, _) = handlers(ScriptedClient(Model)); seed(h)
    transact(ds)(
      GoalLinkRepo.setSchedule(
        Goal,
        ScheduleKind.Currency,
        api.automation.Goal.Plans.GoalSchedule.Cron("@daily"),
        api.automation.Goal.Plans.GoalSchedule.Enabled(true),
        Some(BigDecimal("0.1")),
        Some(Instant.parse("2026-10-05T00:00:00Z"))
      )
    )
    transact(ds)(
      GoalLinkRepo.setSchedule(
        Goal,
        ScheduleKind.Acquisition,
        api.automation.Goal.Plans.GoalSchedule.Cron("@monthly"),
        api.automation.Goal.Plans.GoalSchedule.Enabled(true),
        Some(BigDecimal("0.25")),
        Some(Instant.parse("2026-11-01T00:00:00Z"))
      )
    )
    // before the move, the goal shows the rows as they are
    assertEquals(a.goalView(Goal).toOption.get.schedules.size, 2)
    val out = a.managers.migrateSchedules(Now)
    assertEquals(out, List(s"${Managers.watchId(GoalRef)}: migrated"))
    assertEquals(connect(ds)(GoalLinkRepo.schedules(Some(Goal))), Nil, "the rows are gone")
    val w = a.managing.get(Managers.watchId(GoalRef).transformInto[ManagerLight.Id]).toOption.get.manager
    assertEquals(
      (w.state, w.cron, w.nextTickAt, w.config.sweeps, w.config.budgetUsd),
      ("paused", Some("@daily"), Some("2026-10-05T00:00:00Z"), Some(List("acquisition", "currency")), Some(0.25))
    )
    val ev = connect(ds)(ManagerRepo.events(w.id, None, PageSize(10)))
      .filter(_.kind == Managers.ManagerEvent.Kind(Managers.Feed.Migrated))
    assertEquals(ev.size, 1)
    assert(
      ev.head.text.value.contains("created paused") && ev.head.text.value.contains("`@daily` (fires most often)"),
      ev.head.text
    )
    val log = h.workListView
      .list(WorkListControllers.KindOfList("log"), all = WorkListView.IncludeAll(true))
      .toOption
      .get
      .items
      .map(_.text.value)
      .filter(_.contains(Goal.value))
    assert(log.exists(_.contains("2 goal schedules moved into this watcher")), log)
    assertEquals(a.managers.migrateSchedules(Now), Nil, "idempotent")
    // a schedule brought in later (a bundle import) joins the watcher, which keeps its own cron
    transact(ds)(
      GoalLinkRepo.setSchedule(
        Goal,
        ScheduleKind.Currency,
        api.automation.Goal.Plans.GoalSchedule.Cron("@monthly"),
        api.automation.Goal.Plans.GoalSchedule.Enabled(false),
        None,
        None
      )
    )
    assertEquals(a.managers.migrateSchedules(Now).size, 1)
    val w2 = a.managing.get(Managers.watchId(GoalRef).transformInto[ManagerLight.Id]).toOption.get.manager
    assertEquals((w2.cron, w2.config.sweeps), (Some("@daily"), Some(List("acquisition", "currency"))))
    assert(
      connect(ds)(ManagerRepo.events(w.id, None, PageSize(10))).exists(e =>
        e.kind == Managers.ManagerEvent.Kind(Managers.Feed.Migrated) && e.text.value.contains("was off")
      )
    )
    // the tick migrates too (with agents on)
    transact(ds)(
      GoalLinkRepo.setSchedule(
        Goal,
        ScheduleKind.Acquisition,
        api.automation.Goal.Plans.GoalSchedule.Cron("@weekly"),
        api.automation.Goal.Plans.GoalSchedule.Enabled(true),
        None,
        None
      )
    )
    assert(a.managers.tick(Now).contains(s"${Managers.watchId(GoalRef)}: migrated"))
    cleanManagers()

  test("a run a schedule started (origin scheduler) that pauses leaves a backlog item; a person's does not"):
    val c = ScriptedClient(Model); c.fail = true
    val (a, h, up) = handlers(c); seed(h)
    assert(
      a.addGoalLink(Goal, LinkAdd(LinkKind.Query, api.automation.Goal.Plans.LinkAdd.Ref("zzagent q"))).isRight
    )
    up.found = Map("zzagent q" -> List(work(ApiDoi("10.5555/zzagent.z"), ApiTitle("Z"))))
    val id =
      a.startRunAs(RunRequest(Agents.RunRequest.Pipeline("acquisition-sweep"), GoalTarget), Origin.Scheduler, None)
        .fold(e => fail(e.problems.sentences.mkString("; ")), _.id)
    a.engine.execute(ApiIds.run(id))
    assertEquals(connect(ds)(AgentRepo.get(id)).get.status, "paused")
    def tasks = h.workListView
      .list(WorkListControllers.KindOfList("tasks"), all = WorkListView.IncludeAll(false))
      .toOption
      .get
      .items
      .map(_.text.value)
      .filter(_.contains(Goal.value))
    assert(tasks.exists(t => t.contains("scheduled acquisition-sweep") && t.contains("paused (provider)")), tasks)
    val mine = start(a, RunRequest(Agents.RunRequest.Pipeline("acquisition-sweep"), GoalTarget))
    assertEquals(mine.status, "paused")
    assertEquals(tasks.size, 1, "a person's run is watched; it adds no backlog item")

  // ── #992: dry runs ───────────────────────────────────────────────────────────────────────────────

  test(
    "currency-check dry run: assess without applying; the edge proposal, the acquisition item and the log are only recorded"
  ):
    val (a, h, up) = handlers(ScriptedClient(Model)); seed(h)
    up.citing = Map(Ck.value -> citing)
    val before = CorpusSnapshot.of(ds)
    val run = start(
      a,
      RunRequest(Agents.RunRequest.Pipeline("currency-check"), GoalTarget, dryRun = Some(RunRequest.DryRun(true)))
    )
    assertEquals(run.status, "done", run.error)
    assertEquals(CorpusSnapshot.of(ds), before)
    val steps = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(100000)))
    def would(n: StepInfo.Name) = steps.find(_.name == n).toList.flatMap(_.wouldWrite.map(_.what))
    assertEquals(would(StepInfo.Name("assess")), List(s"assess [[$Ck]] applied: verdict current"))
    val followed = would(StepInfo.Name("followups"))
    assertEquals(followed.size, 2, followed)
    assert(followed.head.value.startsWith("edge proposal"), followed)
    assertEquals(followed(1), s"acquire item for 10.5555/zzagent.out, linked to goal $Goal")
    assertEquals(would(StepInfo.Name("close")), List("log line"))
    assert(run.progress.exists(_.value.contains("1 edge(s) proposed, 1 paper(s) queued")), run.progress)

  test("acquisition-sweep dry run: the ranking runs; the picks it would queue are recorded, none is queued"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"}]}"""))
    val (a, h, up) = handlers(c); seed(h)
    assert(
      a.addGoalLink(
        Goal,
        LinkAdd(LinkKind.Query, api.automation.Goal.Plans.LinkAdd.Ref("zzagent tin trade"))
      ).isRight
    )
    up.found = Map(
      "zzagent tin trade" -> List(
        work(ApiDoi("10.5555/zzagent.a"), ApiTitle("A")),
        work(ApiDoi("10.5555/zzagent.b"), ApiTitle("B"))
      )
    )
    val before = CorpusSnapshot.of(ds)
    val run = start(
      a,
      RunRequest(Agents.RunRequest.Pipeline("acquisition-sweep"), GoalTarget, dryRun = Some(RunRequest.DryRun(true)))
    )
    assertEquals(run.status, "done", run.error)
    assertEquals(CorpusSnapshot.of(ds), before)
    val queue = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(100000)))
      .find(_.name == StepInfo.Name("queue"))
      .get
      .wouldWrite
    assertEquals(queue.map(_.what), List(s"acquire item for 10.5555/zzagent.a, linked to goal $Goal"))
    assert(queue.head.payload.value.contains("core study"), queue.head.payload)
