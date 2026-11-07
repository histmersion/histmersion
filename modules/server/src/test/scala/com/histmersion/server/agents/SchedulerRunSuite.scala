package com.histmersion.server.agents

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
import com.histmersion.domain.automation.managers.ManagerRepo
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*

import java.time.Instant
import com.histmersion.db.*
import com.histmersion.api.automation.{Goal, Managers, Queue}
import com.histmersion.api.library.{CandidateSweep, CandidateWatch, FollowupRow, WorkRef}
import com.histmersion.api.library.Source

/** The goal sweeps and the goal schedules, against a `*_test` database: a currency check (assess and followups over a
  * goal's sources; a disagreeing paper in the corpus becomes ONE edge proposal, one outside it ONE acquisition item,
  * however often it runs), an acquisition sweep (never queues what the corpus or the queue has; the ranking model may
  * only pick listed candidates), and — since phase 3 — the schedules as the goal's watcher (set through the goal: a
  * paused watcher; the `goal_schedules` rows migrated into it once, logged; a scheduled run that pauses still leaves a
  * backlog item). The watcher's own runs and crons: [[ManagerPhase3Suite]]. Skipped without HISTMERSION_TEST_DB_URL.
  */
class SchedulerRunSuite extends com.histmersion.db.DbSuite with com.histmersion.db.Bounded with RunTranscripts:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Goal = "zzagent-sched"
  private val Ck = "zzagent2000_old"
  private val Ck2 = "zzagent2024_new"
  private val Model = ModelRef(ProviderKind.Local, com.histmersion.api.automation.Agents.ModelRef.Model("scripted"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.modify(
      _.db).setTo(b.db.modify(_.url).setTo(testDb.get)).modify(_.agents).setTo(b.agents.modify(_.enabled).setTo(true).modify(_.prices).setTo("scripted=1/2").modify(_.roles).setTo(b.agents.roles.modify(_.planner).setTo(Model.wire))
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
        .put(Ck, Source.Put(title = com.histmersion.api.library.Title("Old tin"), year = (Some(2000)).map(com.histmersion.api.catalogue.Year.apply), doi = (Some("10.5555/zzagent.old")).map(com.histmersion.api.library.Doi.apply)))
        .isRight
    )
    assert(
      h.library.sources
        .put(Ck2, Source.Put(title = com.histmersion.api.library.Title("New tin"), year = (Some(2024)).map(com.histmersion.api.catalogue.Year.apply), doi = (Some("10.5555/zzagent.new")).map(com.histmersion.api.library.Doi.apply)))
        .isRight
    )
    assert(h.goals.put(Goal, com.histmersion.api.automation.Goal.Put(com.histmersion.api.automation.Goal.Title("Zz scheduled tin"))).isRight)
    assert(transact(ds)(GoalLinkRepo.link(Goal, LinkKind.Source, Ck, None)))

  private def start(a: AgentsControllers, req: RunRequest): RunInfo =
    val run = a.startRun(req).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    connect(ds)(AgentRepo.get(run.id)).get

  private val citing = List(
    FollowupRow(
      title = Some(com.histmersion.api.library.Title("New tin")),
      year = Some(com.histmersion.api.catalogue.Year(2024)),
      doi = Some(com.histmersion.api.library.Doi("10.5555/zzagent.new")),
      contexts = List(com.histmersion.api.library.FollowupRow.Contexts("we refute the old dating")),
      proposedKind = com.histmersion.api.library.FollowupRow.ProposedKind("refutes"),
      citekey = Some(Ck2)
    ),
    FollowupRow(
      title = Some(com.histmersion.api.library.Title("Zzagent outside critique")),
      year = Some(com.histmersion.api.catalogue.Year(2022)),
      doi = Some(com.histmersion.api.library.Doi("10.5555/zzagent.out")),
      proposedKind = com.histmersion.api.library.FollowupRow.ProposedKind("challenges")
    ),
    FollowupRow(
      title = Some(com.histmersion.api.library.Title("Zzagent agreeing")),
      year = Some(com.histmersion.api.catalogue.Year(2021)),
      doi = Some(com.histmersion.api.library.Doi("10.5555/zzagent.ok")),
      proposedKind = com.histmersion.api.library.FollowupRow.ProposedKind("confirms")
    )
  )

  test(
    "currency-check: assess and followups over the goal's sources; one edge proposal, one acquisition item — never twice"
  ):
    val (a, h, up) = handlers(ScriptedClient(Model)); seed(h)
    up.citing = Map(Ck -> citing)
    val run = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("currency-check"), Goal))
    assertEquals(run.status, "done", run.error)
    import scala.jdk.CollectionConverters.*
    assertEquals(up.calls.asScala.toList, List(s"assess $Ck", s"followups $Ck"))
    val open = a.approvalsList(None, None).toOption.get.filter(_.citekey == Ck2)
    assertEquals(open.map(x => (x.kind, x.edgeKind, x.target)), List(("edge", Some("refutes"), Some(Ck))))
    assert(open.head.evidence.contains("we refute the old dating"), open.head.evidence)
    assertEquals(h.library.citations.neighbors(Ck2, List(com.histmersion.domain.library.Edge.Kind.Wire("refutes"))), Nil, "nothing applied before approval")
    def queued = h.workListView.list(com.histmersion.domain.automation.worklists.WorkListControllers.KindOfList("acquire"), all = com.histmersion.server.views.WorkListView.IncludeAll(true)).toOption.get.items.filter(_.text.contains("zzagent.out"))
    assertEquals(queued.size, 1)
    assert(
      connect(ds)(GoalLinkRepo.links(Goal)).exists(l =>
        l.kind == LinkKind.QueueItem && l.ref == queued.head.id.toString
      )
    )
    assert(run.progress.exists(_.contains("1 edge(s) proposed, 1 paper(s) queued")), run.progress)

    // again (as a schedule would): the open proposal and the queued paper are not doubled
    val again = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("currency-check"), Goal))
    assertEquals(again.status, "done", again.error)
    assertEquals(a.approvalsList(Some(com.histmersion.domain.automation.approvals.ApprovalQuery.State("all")), None).toOption.get.count(_.citekey == Ck2), 1)
    assertEquals(queued.size, 1)
    // a rejected proposal is not asked again either
    assert(a.decide(open.head.id, com.histmersion.domain.automation.Approval.Outcome("reject"), DecisionRequest()).isRight)
    assertEquals(start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("currency-check"), Goal)).status, "done")
    assertEquals(a.approvalsList(Some(com.histmersion.domain.automation.approvals.ApprovalQuery.State("all")), None).toOption.get.count(_.citekey == Ck2), 1)

  private def work(doi: String, title: String, inDb: Boolean = false) =
    WorkRef(doi = (Some(doi)).map(com.histmersion.api.library.Doi.apply), title = (Some(title)).map(com.histmersion.api.library.Title.apply), year = (Some(2023)).map(com.histmersion.api.catalogue.Year.apply), authors = (List("Zz")).map(com.histmersion.api.library.AuthorName.apply), inDb = inDb)

  test(
    "acquisition-sweep: the goal's queries searched, corpus and queue left out, the ranking model's picks queued once"
  ):
    val c = ScriptedClient(Model)
    c.scripts ++= List(
      Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"},{"doi":"10.9999/invented","why":"?"}]}""")),
      Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"}]}"""))
    )
    val (a, h, up) = handlers(c); seed(h)
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("zzagent tin trade"))).isRight)
    assert(h.workLists.add(com.histmersion.domain.automation.worklists.WorkListControllers.ListName("acquire"), Queue.QueueAdd(com.histmersion.api.automation.Queue.QueueItem.Text("Zzagent queued. https://doi.org/10.5555/zzagent.q"))).isRight)
    up.found = Map(
      "zzagent tin trade" -> List(
        work("10.5555/zzagent.a", "A"),
        work("10.5555/zzagent.b", "B"),
        work("10.5555/zzagent.in", "In", inDb = true),
        work("10.5555/zzagent.q", "Queued")
      )
    )
    val run = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("acquisition-sweep"), Goal))
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
    val items = h.workListView.list(com.histmersion.domain.automation.worklists.WorkListControllers.KindOfList("acquire"), all = com.histmersion.server.views.WorkListView.IncludeAll(true)).toOption.get.items.filter(_.text.contains("zzagent.a"))
    assertEquals(items.size, 1)
    assert(items.head.text.contains("for goal zzagent-sched: core study"), items.head.text)
    assert(run.progress.exists(_.contains("2 new candidate(s), 1 picked, 1 queued")), run.progress)

    // the next sweep finds nothing new: the picked work is now queued, the other still on offer
    c.scripts += Seq(text("""{"picks":[]}"""))
    val again = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("acquisition-sweep"), Goal))
    assertEquals(again.status, "done", again.error)
    assertEquals(h.workListView.list(com.histmersion.domain.automation.worklists.WorkListControllers.KindOfList("acquire"), all = com.histmersion.server.views.WorkListView.IncludeAll(true)).toOption.get.items.count(_.text.contains("zzagent.a")), 1)

  /** The candidate search (#3761) answered from memory: what the goal's watches stored, and the marks set. */
  final private class CandidateUpstream extends AgentUpstream:
    // `var`: the stored candidates, changed by the marks
    @volatile var stored: List[Source.Candidate] = Nil
    val sweeps = java.util.concurrent.atomic.AtomicInteger(0)
    def meta(doi: String) = Left("not stubbed")
    def refs(citekey: String, apply: Boolean) = Left("not stubbed")
    def assess(citekey: String, apply: Boolean) = Left("not stubbed")
    def followups(id: String, since: Option[Int], limit: Option[Int]) = Left("not stubbed")
    def discover(query: String, limit: Int) = Left(
      "the live search must not run when the candidate search is connected"
    )
    override def sweepCandidates(goal: String, dryRun: Boolean) =
      sweeps.incrementAndGet()
      Right(
        CandidateSweep(
          goal,
          List(CandidateWatch(goal, com.histmersion.api.library.CandidateWatch.Kind("query"), com.histmersion.api.library.CandidateWatch.Subject("zzagent tin trade"), com.histmersion.api.library.CandidateWatch.Index("openalex"))),
          stored.size,
          com.histmersion.api.library.CandidateSweep.Added(0),
          stored.size,
          Map.empty,
          stored.count(_.state == "new"),
          dryRun
        )
      )
    override def candidates(goal: String, state: String, limit: Int) = Right(
      stored.filter(_.state == state).take(limit)
    )
    override def markCandidates(ids: List[Long], state: String) =
      stored = stored.map(c => if ids.contains(c.id) then c.modify(_.state).setTo(state) else c)
      Right(ids.size)

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
        com.histmersion.api.library.Source.Candidate.Id(1),
        Goal,
        com.histmersion.api.library.Doi("10.5555/zzagent.a"),
        com.histmersion.api.library.Title("A"),
        (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
        (List("Zz")).map(com.histmersion.api.library.AuthorName.apply),
        foundBy = List("openalex query \"zzagent tin trade\"")
      ),
      Source.Candidate(
        com.histmersion.api.library.Source.Candidate.Id(2),
        Goal,
        com.histmersion.api.library.Doi("10.5555/zzagent.b"),
        com.histmersion.api.library.Title("B"),
        (Some(2026)).map(com.histmersion.api.catalogue.Year.apply),
        (List("Zz")).map(com.histmersion.api.library.AuthorName.apply),
        foundBy = List("crossref query \"zzagent tin trade\"")
      )
    )
    val run = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("acquisition-sweep"), Goal))
    assertEquals(run.status, "done", run.error)
    assertEquals(c.steps.size, 1)
    val offered = c.steps.head.head.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.get
    assert(offered.contains("10.5555/zzagent.a") && offered.contains("found by \"crossref query"), offered)
    assertEquals(
      up.stored.map(c => c.doi -> c.state).toMap,
      Map("10.5555/zzagent.a" -> "queued", "10.5555/zzagent.b" -> "seen")
    )
    assert(h.workListView.list(com.histmersion.domain.automation.worklists.WorkListControllers.KindOfList("acquire"), all = com.histmersion.server.views.WorkListView.IncludeAll(true)).toOption.get.items.exists(_.text.contains("zzagent.a")))
    // the next sweep: every candidate decided — no model is asked
    val again = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("acquisition-sweep"), Goal))
    assertEquals(again.status, "done", again.error)
    assertEquals(c.steps.size, 1, "no ranking without a new candidate")
    assert(again.progress.exists(_.contains("nothing new")), again.progress)

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
    assert(a.setSchedule(Goal, com.histmersion.api.automation.Goal.Plans.ScheduleKind.Wire("currency"), ScheduleSet(com.histmersion.api.automation.Goal.Plans.ScheduleSet.Cron("not cron")), Now).left.exists(_.message.contains("five fields")))
    assert(
      a.setSchedule(Goal, com.histmersion.api.automation.Goal.Plans.ScheduleKind.Wire("currency"), ScheduleSet(com.histmersion.api.automation.Goal.Plans.ScheduleSet.Cron("*/5 * * * *")), Now).left.exists(_.message.contains("min-interval"))
    )
    assert(a.setSchedule(Goal, com.histmersion.api.automation.Goal.Plans.ScheduleKind.Wire("weekly"), ScheduleSet(com.histmersion.api.automation.Goal.Plans.ScheduleSet.Cron("@daily")), Now).isLeft)
    val s = a.setSchedule(Goal, com.histmersion.api.automation.Goal.Plans.ScheduleKind.Wire("currency"), ScheduleSet(com.histmersion.api.automation.Goal.Plans.ScheduleSet.Cron("@daily"), budgetUsd = Some(0.5)), Now).toOption.get
    assertEquals(
      (s.kind, s.cron, s.enabled, s.budgetUsd, s.nextRunAt),
      (ScheduleKind.Currency, "@daily", true, Some(0.5), Some("2026-10-05T00:00:00Z"))
    )
    assert(s.lastNote.exists(_.contains("paused")), s.lastNote)
    val w = a.managing.get(Managers.watchId(Goal)).toOption.get.manager
    assertEquals(
      (w.kind, w.state, w.cron, w.config.sweeps, w.config.budgetUsd),
      ("watch", "paused", Some("@daily"), Some(List("currency")), Some(0.5))
    )
    // the acquisition sweep joins it; the cron is the watcher's, shared
    val acq = a.setSchedule(Goal, com.histmersion.api.automation.Goal.Plans.ScheduleKind.Wire("acquisition"), ScheduleSet(com.histmersion.api.automation.Goal.Plans.ScheduleSet.Cron("@weekly")), Now).toOption.get
    assertEquals((acq.kind, acq.cron), (ScheduleKind.Acquisition, "@weekly"))
    assertEquals(
      a.goalView(Goal).toOption.get.schedules.map(s => (s.kind, s.cron)),
      List((ScheduleKind.Acquisition, "@weekly"), (ScheduleKind.Currency, "@weekly"))
    )
    val off = a.setSchedule(Goal, com.histmersion.api.automation.Goal.Plans.ScheduleKind.Wire("currency"), ScheduleSet(com.histmersion.api.automation.Goal.Plans.ScheduleSet.Cron("@weekly"), enabled = com.histmersion.api.automation.Goal.Plans.ScheduleSet.Enabled(false)), Now).toOption.get
    assertEquals(off.enabled, false)
    assertEquals(a.goalView(Goal).toOption.get.schedules.map(_.kind), List(ScheduleKind.Acquisition))
    assertEquals(a.removeSchedule(Goal, com.histmersion.api.automation.Goal.Plans.ScheduleKind.Wire("acquisition")).map(_.removed), Right(true))
    assertEquals(a.removeSchedule(Goal, com.histmersion.api.automation.Goal.Plans.ScheduleKind.Wire("acquisition")).map(_.removed), Right(false))
    assertEquals(a.goalView(Goal).toOption.get.schedules, Nil)
    assert(a.managing.get(Managers.watchId(Goal)).isRight, "the watcher stays")
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
        com.histmersion.api.automation.Goal.Plans.GoalSchedule.Cron("@daily"),
        com.histmersion.api.automation.Goal.Plans.GoalSchedule.Enabled(true),
        Some(BigDecimal("0.1")),
        Some(Instant.parse("2026-10-05T00:00:00Z"))
      )
    )
    transact(ds)(
      GoalLinkRepo.setSchedule(
        Goal,
        ScheduleKind.Acquisition,
        com.histmersion.api.automation.Goal.Plans.GoalSchedule.Cron("@monthly"),
        com.histmersion.api.automation.Goal.Plans.GoalSchedule.Enabled(true),
        Some(BigDecimal("0.25")),
        Some(Instant.parse("2026-11-01T00:00:00Z"))
      )
    )
    // before the move, the goal shows the rows as they are
    assertEquals(a.goalView(Goal).toOption.get.schedules.size, 2)
    val out = a.managers.migrateSchedules(Now)
    assertEquals(out, List(s"${Managers.watchId(Goal)}: migrated"))
    assertEquals(connect(ds)(GoalLinkRepo.schedules(Some(Goal))), Nil, "the rows are gone")
    val w = a.managing.get(Managers.watchId(Goal)).toOption.get.manager
    assertEquals(
      (w.state, w.cron, w.nextTickAt, w.config.sweeps, w.config.budgetUsd),
      ("paused", Some("@daily"), Some("2026-10-05T00:00:00Z"), Some(List("acquisition", "currency")), Some(0.25))
    )
    val ev = connect(ds)(ManagerRepo.events(w.id, None, com.histmersion.PageSize(10))).filter(_.kind == Managers.Feed.Migrated)
    assertEquals(ev.size, 1)
    assert(
      ev.head.text.contains("created paused") && ev.head.text.contains("`@daily` (fires most often)"),
      ev.head.text
    )
    val log = h.workListView.list(com.histmersion.domain.automation.worklists.WorkListControllers.KindOfList("log"), all = com.histmersion.server.views.WorkListView.IncludeAll(true)).toOption.get.items.map(_.text).filter(_.contains(Goal))
    assert(log.exists(_.contains("2 goal schedules moved into this watcher")), log)
    assertEquals(a.managers.migrateSchedules(Now), Nil, "idempotent")
    // a schedule brought in later (a bundle import) joins the watcher, which keeps its own cron
    transact(ds)(GoalLinkRepo.setSchedule(Goal, ScheduleKind.Currency, com.histmersion.api.automation.Goal.Plans.GoalSchedule.Cron("@monthly"), com.histmersion.api.automation.Goal.Plans.GoalSchedule.Enabled(false), None, None))
    assertEquals(a.managers.migrateSchedules(Now).size, 1)
    val w2 = a.managing.get(Managers.watchId(Goal)).toOption.get.manager
    assertEquals((w2.cron, w2.config.sweeps), (Some("@daily"), Some(List("acquisition", "currency"))))
    assert(
      connect(ds)(ManagerRepo.events(w.id, None, com.histmersion.PageSize(10))).exists(e =>
        e.kind == Managers.Feed.Migrated && e.text.contains("was off")
      )
    )
    // the tick migrates too (with agents on)
    transact(ds)(GoalLinkRepo.setSchedule(Goal, ScheduleKind.Acquisition, com.histmersion.api.automation.Goal.Plans.GoalSchedule.Cron("@weekly"), com.histmersion.api.automation.Goal.Plans.GoalSchedule.Enabled(true), None, None))
    assert(a.managers.tick(Now).contains(s"${Managers.watchId(Goal)}: migrated"))
    cleanManagers()

  test("a run a schedule started (origin scheduler) that pauses leaves a backlog item; a person's does not"):
    val c = ScriptedClient(Model); c.fail = true
    val (a, h, up) = handlers(c); seed(h)
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("zzagent q"))).isRight)
    up.found = Map("zzagent q" -> List(work("10.5555/zzagent.z", "Z")))
    val id =
      a.startRunAs(RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("acquisition-sweep"), Goal), Origin.Scheduler, None).fold(e => fail(e.message), _.id)
    a.engine.execute(id)
    assertEquals(connect(ds)(AgentRepo.get(id)).get.status, "paused")
    def tasks = h.workListView.list(com.histmersion.domain.automation.worklists.WorkListControllers.KindOfList("tasks"), all = com.histmersion.server.views.WorkListView.IncludeAll(false)).toOption.get.items.map(_.text).filter(_.contains(Goal))
    assert(tasks.exists(t => t.contains("scheduled acquisition-sweep") && t.contains("paused (provider)")), tasks)
    val mine = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("acquisition-sweep"), Goal))
    assertEquals(mine.status, "paused")
    assertEquals(tasks.size, 1, "a person's run is watched; it adds no backlog item")

  // ── #992: dry runs ───────────────────────────────────────────────────────────────────────────────

  test(
    "currency-check dry run: assess without applying; the edge proposal, the acquisition item and the log are only recorded"
  ):
    val (a, h, up) = handlers(ScriptedClient(Model)); seed(h)
    up.citing = Map(Ck -> citing)
    val before = CorpusSnapshot.of(ds)
    val run = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("currency-check"), Goal, dryRun = Some(true)))
    assertEquals(run.status, "done", run.error)
    assertEquals(CorpusSnapshot.of(ds), before)
    val steps = connect(ds)(AgentRepo.steps(run.id, com.histmersion.domain.automation.agents.AgentRepo.OutputChars(100000)))
    def would(n: String) = steps.find(_.name == n).toList.flatMap(_.wouldWrite.map(_.what))
    assertEquals(would("assess"), List(s"assess [[$Ck]] applied: verdict current"))
    val followed = would("followups")
    assertEquals(followed.size, 2, followed)
    assert(followed.head.startsWith("edge proposal"), followed)
    assertEquals(followed(1), s"acquire item for 10.5555/zzagent.out, linked to goal $Goal")
    assertEquals(would("close"), List("log line"))
    assert(run.progress.exists(_.contains("1 edge(s) proposed, 1 paper(s) queued")), run.progress)

  test("acquisition-sweep dry run: the ranking runs; the picks it would queue are recorded, none is queued"):
    val c = ScriptedClient(Model)
    c.scripts += Seq(text("""{"picks":[{"doi":"10.5555/zzagent.a","why":"core study"}]}"""))
    val (a, h, up) = handlers(c); seed(h)
    assert(a.addGoalLink(Goal, LinkAdd(LinkKind.Query, com.histmersion.api.automation.Goal.Plans.LinkAdd.Ref("zzagent tin trade"))).isRight)
    up.found = Map("zzagent tin trade" -> List(work("10.5555/zzagent.a", "A"), work("10.5555/zzagent.b", "B")))
    val before = CorpusSnapshot.of(ds)
    val run = start(a, RunRequest(com.histmersion.api.automation.Agents.RunRequest.Pipeline("acquisition-sweep"), Goal, dryRun = Some(true)))
    assertEquals(run.status, "done", run.error)
    assertEquals(CorpusSnapshot.of(ds), before)
    val queue = connect(ds)(AgentRepo.steps(run.id, com.histmersion.domain.automation.agents.AgentRepo.OutputChars(100000))).find(_.name == "queue").get.wouldWrite
    assertEquals(queue.map(_.what), List(s"acquire item for 10.5555/zzagent.a, linked to goal $Goal"))
    assert(queue.head.payload.contains("core study"), queue.head.payload)
