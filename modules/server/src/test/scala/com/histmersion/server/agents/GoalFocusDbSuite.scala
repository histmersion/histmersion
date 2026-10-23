package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.core.Autopilot.{AutopilotSet, FocusSet}
import com.histmersion.core.GoalPlans.*
import com.histmersion.core.Managers.*
import com.histmersion.db.{AgentRepo, FocusRepo, GoalLinkRepo, ManagerRepo, Migrate}
import com.histmersion.server.{Deps, Handlers}
import io.circe.syntax.*

import java.time.Instant

/** The autopilot's focus against a `*_test` database (docs/research-process-managers.md "As built: the
  * focus"): 20 goals and a cap of 3 — only the 3 chosen goals' model-using items are dispatched (the
  * closest to done first), the others' are held with a `focus` hold, while the currency checks and the
  * ingest backlog run for every goal; a goal reaching `reviewed` frees its slot and the next takes it in the
  * same tick, its items released and dispatched; a person's pin, the cap set by a person, and the cap at 0
  * (every hold released); a goal plan decided by code only for an active goal within the open sub-goals.
  * Models are scripted and never called (the runs are dispatched, not executed); the acquisition upstream
  * is a fixture. Skipped without HISTMERSION_TEST_DB_URL. */
class GoalFocusDbSuite extends com.histmersion.db.DbSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(180, "s")

  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val goals = (1 to 20).toList.map(i => f"zzfocus-g$i%02d")
  private def g(i: Int) = goals(i - 1)
  private val Art = "zzfocus_art"

  /** A cap of 3, room for every run, the crons off (nothing fires by itself), the backlog drained whole. */
  private lazy val cfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.copy(db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(enabled = true, prices = "scripted=1/2", maxConcurrentRuns = 100,
        roles = b.agents.roles.copy(extractor = m, reviewer = m, coldReader = m, skeptic = m, fixer = m, planner = m, writer = m),
        scheduler = b.agents.scheduler.copy(enabled = false),
        seed = b.agents.seed.copy(minSources = 2),
        managers = b.agents.managers.copy(watch = b.agents.managers.watch.copy(drainPerTick = 50)),
        autopilot = b.agents.autopilot.copy(backupHours = 0, lintHours = 0, ingestPerDay = 100, maxActiveGoals = 3)))

  private lazy val ds = testPool(cfg.db)

  private def handlers(c: AppConfig = cfg): (AgentsHandlers, Handlers, StubUpstream) =
    val deps = Deps(c, ds)
    val h    = Handlers(deps)
    val a    = AgentsHandlers(deps, h, (_, _) => Right(ScriptedClient(Model)))
    val up   = StubUpstream()
    a.attach(up)
    (a, h, up)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      // the focus looks at EVERY goal, and the dispatch counts EVERY queued run against max-concurrent-runs:
      // what other suites left (their goals, their runs, open goal plans) must not take the 3 slots or the room
      sql"DELETE FROM agent_managers".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent'".update.run()
      sql"DELETE FROM approvals WHERE subject LIKE 'zzfocus%' OR kind = 'goal-plan'".update.run()
      sql"DELETE FROM goals".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzfocus%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzfocus%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzfocus%' OR text LIKE 'autopilot%'".update.run()
      sql"""UPDATE agent_autopilot SET enabled = false, paused = '{}', changed_by = '', last_ensure_at = NULL,
                                       max_active_goals = NULL""".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  private def doi(slug: String) = s"10.5555/$slug"
  private def ck(d: String) = "zzfocus" + d.replaceAll("[^a-z0-9]", "")

  /** 20 goals with a query each (no plan needed): g05 has an article to review, g10 enough sources. */
  private def seed(a: AgentsHandlers, h: Handlers, up: StubUpstream): Unit =
    goals.foreach { s =>
      assert(h.putGoal(s, GoalPut(s"Zz $s")).isRight)
      assert(a.addGoalLink(s, LinkAdd(LinkKind.Query, s"zzfocus query $s")).isRight)
    }
    List("zzfocus_s1", "zzfocus_s2").foreach { c =>
      assert(h.putSource(c, SourcePut(title = s"Zz $c", year = Some(2020), topics = List("zzfocus_t"), body = Some("Tin."))).isRight)
      assert(GoalLinkRepo.link(ds, g(10), LinkKind.Source, c, None))
    }
    assert(h.putArticle(Art, ArticlePut("Zz art", "concept", "Tin came by sea [[zzfocus_s1]].",
      filter = Some(ArticleFilter(topics = List("zzfocus_t"))))).isRight)
    assert(GoalLinkRepo.link(ds, g(5), LinkKind.Article, Art, None))
    up.onIngest = d =>
      assert(h.putSource(ck(d), SourcePut(title = s"Ingested $d", year = Some(2023), doi = Some(d))).isRight)
      Right(IngestReport(ck(d), Some(d), Some(s"Ingested $d"), created = true,
        Some(PdfInfo("unpaywall", Some(s"https://example.invalid/$d.pdf"), 2048, None)), 0, None, Nil))

  private def on(a: AgentsHandlers): Unit =
    a.managers.ensureDefaults()
    // the review manager's own selection narrowed to these goals' article (other suites' articles stay out)
    a.managing.update(ReviewId, ManagerUpdate(Some(ManagerConfig(includeGoal = Some(g(5)))))).fold(e => fail(e.message), identity)
    a.managing.setAutopilot(AutopilotSet(on = true, Some("test"))).fold(e => fail(e.message), identity)

  private def item(mid: String, kind: String, target: String): ManagerItem =
    ManagerRepo.itemFor(ds, mid, kind, target).getOrElse(fail(s"no $kind item for $target in $mid"))

  /** The runs the managers dispatched on these goals (and the article), by pipeline. */
  private def runs(): Vector[RunInfo] =
    AgentRepo.list(ds, None, 1000).filter(r => r.managerId.isDefined && r.target.startsWith("zzfocus"))
  private def modelRuns(): Vector[RunInfo] =
    runs().filter(r => Pipeline.parse(r.pipeline).exists(_.roles.nonEmpty))

  private def activeNow: Set[String] = FocusRepo.goals(ds).filter(_.active).map(_.slug).toSet

  private final class Clock(var now: Instant = Instant.now()): // a var: the test's own moving time
    def tick(a: AgentsHandlers, minutes: Int = 6): List[String] =
      now = now.plusSeconds(minutes.toLong * 60L)
      a.managers.tick(now)

  test("20 goals, cap 3: only the 3 chosen goals' model items run; currency checks and the ingest backlog run for all; reviewed frees the slot"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    // every watcher has one legal open-access work waiting in its ingest backlog
    goals.foreach(s => a.oaBacklog.add(watchId(s), s, OaWork(doi(s), s"Work of $s")))
    val clock = Clock()
    clock.tick(a)

    // the active set: the article to review first, then enough sources, then the oldest still gathering
    assertEquals(activeNow, Set(g(5), g(10), g(1)))
    val f = a.focus.info(clock.now)
    assertEquals(f.active.map(_.slug), List(g(5), g(10), g(1)))
    assertEquals(f.active.map(_.stage), List("reviewing", "ready", "gathering"))
    assert(f.active.head.why.contains("1 of 1 article still to pass review"), f.active.head.why)
    assert(f.active.forall(_.doing.isDefined), f.active)
    assertEquals(f.queued, 17)
    assertEquals(f.queue.size, 10)
    assertEquals(f.queue.head.slug, g(2))

    // only their model-using items were dispatched
    val targets = modelRuns().map(_.target).toSet
    assertEquals(targets, Set(g(5), g(10), g(1), Art), modelRuns().map(r => s"${r.pipeline} ${r.target}"))
    assertEquals(modelRuns().count(_.pipeline == Pipeline.ProposeArticles.wire), 3)
    assertEquals(modelRuns().count(_.pipeline == Pipeline.ReviewArticle.wire), 1)
    // the others' are held for the focus, not cancelled
    val held = item(seedId(g(2)), ProposeArticles, g(2))
    assertEquals((held.status, held.hold.flatMap(_.focus)), ("held", Some(g(2))))
    assert(held.hold.exists(_.reason.startsWith(GoalFocus.HoldPrefix)), held.hold)
    assertEquals(item(watchId(g(7)), AcquisitionSweep, g(7)).status, "held")
    assert(ManagerRepo.get(ds, seedId(g(2))).flatMap(_.statusNote).exists(_.startsWith("focus: its goal waits for a slot")))
    assert(!ManagerRepo.get(ds, seedId(g(5))).flatMap(_.statusNote).exists(_.startsWith("focus:")))
    assertEquals(ManagerRepo.focusHeldCount(ds), 17 * 2, "a proposal and a sweep for each of the 17 waiting goals")
    // a person cannot release it by hand while the focus is in force: pin the goal instead
    a.managing.patchItem(seedId(g(2)), g(2), ItemPatch("release")) match
      case Left(e) => assert(e.message.contains(s"focus pin ${g(2)}"), e.message)
      case Right(i) => fail(s"released: $i")
    // the currency check needs no model: it ran for the waiting goals too
    val currency = runs().filter(_.pipeline == Pipeline.CurrencyCheck.wire).map(_.target).toSet
    assert(goals.filterNot(Set(g(1), g(5), g(10))).forall(currency), currency)
    // the ingest backlog drains for every goal, active or not
    assertEquals(goals.count(s => item(watchId(s), OaIngest, doi(s)).status == "done"), 20)
    assert(h.getSource(ck(doi(g(17))), body = false).isRight)
    assert(ManagerRepo.events(ds, seedId(g(5)), None, 50).exists(e => e.kind == Feed.Autopilot && e.text.startsWith("focus: active")))

    // the status says it, on every surface
    val st = a.managing.autopilot
    assert(st.focus.exists(_.on), st.focus)
    val md = AutopilotMarkdown.status(st)
    assert(md.contains("## Focus: at most 3 active goals") && md.contains(s"1. **${g(5)}**"), md)

    // g01 is reviewed: it leaves, the next takes its slot in the same tick, its items released and dispatched
    val g1run = item(seedId(g(1)), ProposeArticles, g(1)).runId.get
    assert(h.patchGoal(g(1), GoalPatch(status = Some(GoalStatus.Reviewed))).isRight)
    clock.tick(a)
    assertEquals(activeNow, Set(g(5), g(10), g(2)))
    assertEquals(FocusRepo.goals(ds).find(_.slug == g(1)).flatMap(_.leftWhy), Some("it is reviewed"))
    val g2 = item(seedId(g(2)), ProposeArticles, g(2))
    assertEquals(g2.status, "active")
    assert(g2.runId.isDefined)
    assertEquals(AgentRepo.get(ds, g1run).map(_.status), Some("queued"), "the reviewed goal's run in flight is not cancelled")
    assert(QueueRepoLog.has(ds, "autopilot focus: +" + g(2)), "a worklog line")

  test("a person's pin is active at once; the cap set by a person; a cap of 0 releases every hold"):
    val (a, h, up) = handlers(); seed(a, h, up)
    on(a)
    val clock = Clock()
    clock.tick(a)
    assertEquals(activeNow, Set(g(5), g(10), g(1)))
    val pinned = a.focus.set(FocusSet("pin", Some(g(20)), by = Some("test"))).fold(e => fail(e.message), identity)
    assert(pinned.changes.exists(_.contains(s"${g(20)} pinned")), pinned.changes)
    assertEquals(pinned.active.head.slug, g(20))
    clock.tick(a)
    assertEquals(activeNow, Set(g(20), g(5), g(10)), "the pin took the slot of the worst active goal")
    assert(FocusRepo.goals(ds).find(_.slug == g(1)).flatMap(_.leftWhy).exists(_.contains("cap of 3")))
    assert(item(seedId(g(20)), ProposeArticles, g(20)).runId.isDefined, "the pinned goal's items run")
    assertEquals(a.focus.set(FocusSet("pin", Some("zzfocus-nope"))).left.map(_.message), Left("no goal 'zzfocus-nope'"))
    // a priority: first among the goals still gathering
    a.focus.set(FocusSet("priority", Some(g(15)), Some(5))).fold(e => fail(e.message), identity)
    assertEquals(a.focus.info().queue.head.slug, g(15))
    // the cap raised by a person
    val more = a.focus.set(FocusSet("max", value = Some(5))).fold(e => fail(e.message), identity)
    assertEquals((more.max, more.maxFrom), (5, "person"))
    clock.tick(a)
    assertEquals(activeNow, Set(g(20), g(5), g(10), g(15), g(1)))
    // 0: no cap — the focus is not in force, every hold is released
    a.focus.set(FocusSet("max", value = Some(0))).fold(e => fail(e.message), identity)
    clock.tick(a)
    assertEquals(ManagerRepo.focusHeldCount(ds), 0)
    assertEquals(activeNow, Set.empty[String])
    assertEquals(item(seedId(g(12)), ProposeArticles, g(12)).status, "active", "released and dispatched")
    // back to the config's cap
    val back = a.focus.set(FocusSet("max")).fold(e => fail(e.message), identity)
    assertEquals((back.max, back.maxFrom), (3, "config"))
    // the autopilot off: not in force either (the managers it ran are paused; holds released)
    clock.tick(a)
    assert(ManagerRepo.focusHeldCount(ds) > 0)
    a.managing.setAutopilot(AutopilotSet(on = false)).fold(e => fail(e.message), identity)
    clock.tick(a)
    assertEquals(ManagerRepo.focusHeldCount(ds), 0)
    assert(a.focus.info().note.exists(_.contains("the autopilot is off")))

  test("goal plans decided by code under the focus: only an active goal's, within the open sub-goals"):
    val auto = cfg.copy(agents = cfg.agents.copy(approvals = cfg.agents.approvals.copy(goalPlan = "auto")))
    val (a, h, up) = handlers(auto); seed(a, h, up)
    on(a)
    Clock().tick(a)
    def plan(goal: String, kids: Int) = AgentRepo.openSubject(ds, None, ApprovalKind.GoalPlan, goal,
      Plan(goal, "r", (1 to kids).toList.map(i => PlannedGoal(s"$goal-k$i", s"Zz k$i")), List("q")).asJson.noSpaces)
    val now = Instant.now()
    def judged(p: ApprovalInfo) = a.autoApprovals.judge(AgentRepo.approval(ds, p.id).get, None, None, now)
    judged(plan(g(7), 0)) match
      case AutopilotPolicy.Verdict.Leave(w) => assert(w.contains("not one of the active goals"), w)
      case v => fail(v.toString)
    assert(judged(plan(g(1), 3)).isInstanceOf[AutopilotPolicy.Verdict.Approve])
    judged(plan(g(1), 6)) match
      case AutopilotPolicy.Verdict.Leave(w) => assert(w.contains("max-open-sub-goals"), w)
      case v => fail(v.toString)

/** The worklog, read for an assertion. */
private object QueueRepoLog:
  def has(ds: javax.sql.DataSource, prefix: String): Boolean =
    com.histmersion.db.QueueRepo.list(ds, Queue.Kind.Log, all = true).exists(_.text.startsWith(prefix))
