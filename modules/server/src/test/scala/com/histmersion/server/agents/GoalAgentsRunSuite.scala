package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.GoalPlans.*
import com.histmersion.db.{AgentRepo, Db, GoalLinkRepo, GoalRepo, Migrate}
import com.histmersion.server.{Deps, Handlers}
import io.circe.Json
import sttp.ai.core.agent.*
import com.histmersion.values.SourceStatus

/** Answers from fixtures: DOI metadata, citing papers, searches; counts what was asked. */
final class StubUpstream extends AgentUpstream:
  @volatile var metas: Map[String, WorkRef]              = Map.empty
  @volatile var citing: Map[String, List[FollowupRow]]   = Map.empty
  @volatile var found: Map[String, List[WorkRef]]        = Map.empty
  val calls = java.util.concurrent.ConcurrentLinkedQueue[String]()
  def meta(doi: String) = { calls.add(s"meta $doi"); metas.get(doi).toRight(s"$doi: not found upstream") }
  def refs(citekey: String, apply: Boolean) = Left("not stubbed")
  def assess(citekey: String, apply: Boolean) =
    calls.add(s"assess $citekey")
    Right(Assessment(citekey, None, Some(citekey), None, None, None, false, Nil, Nil, false, "current", "fine"))
  def followups(id: String, since: Option[Int], limit: Option[Int]) =
    calls.add(s"followups $id")
    val rows = citing.getOrElse(id, Nil)
    Right(FollowupsResult(id, None, "stub", rows.size, 0, rows))
  def discover(query: String, limit: Int) =
    calls.add(s"discover $query")
    val ws = found.getOrElse(query, Nil)
    Right(DiscoverResult(query, ws.size, ws))
  /** A watcher's legal ingest: what `onIngest` answers (nothing connected by default). */
  @volatile var onIngest: String => Either[String, IngestReport] = _ => Left("not stubbed")
  override def ingestLegal(doi: String) = { calls.add(s"ingest $doi"); onIngest(doi) }
  /** The deterministic candidate search: what each goal's sweep finds (not connected unless set). */
  @volatile var sweepFinds: Map[String, List[SourceCandidate]] = Map.empty
  private val marked = java.util.concurrent.ConcurrentHashMap[Long, String]()
  override def sweepCandidates(goal: String, dryRun: Boolean) =
    calls.add(s"sweep $goal")
    sweepFinds.get(goal).toRight("the candidate search is not connected").map(cs =>
      CandidateSweep(goal, Nil, cs.size, cs.size, 0, Map.empty, cs.count(c => !marked.containsKey(c.id)), dryRun))
  override def candidates(goal: String, state: String, limit: Int) =
    Right(sweepFinds.getOrElse(goal, Nil).map(c => c.copy(state = Option(marked.get(c.id)).getOrElse(c.state)))
      .filter(_.state == state).take(limit))
  override def markCandidates(ids: List[Long], state: String) =
    ids.foreach(i => marked.put(i, state))
    Right(ids.size)

/** The P3 pipelines that WAIT for a person, end to end against a `*_test` database with a scripted
  * model: plan-goal (nothing is created before the plan is approved; approval re-queues the run, which
  * applies exactly the approved plan; a rejection changes nothing; a cancel rejects the approval) and
  * compile-article (claim table with verified quotes, evidence, draft, the blocking approval, a new
  * article written only after approval, the chained review run, an extension dropped on 412, and the
  * `auto` mode). Skipped without HISTMERSION_TEST_DB_URL. */
class GoalAgentsRunSuite extends com.histmersion.db.DbSuite with com.histmersion.db.Bounded:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout         = scala.concurrent.duration.Duration(120, "s")

  private val Goal  = "zzagent-goal"
  private val Ck    = "zzagent2019_tin"
  private val Ck2   = "zzagent2024_iso"
  private val Slug  = "zzagent_tin"
  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private lazy val baseCfg =
    val b = AppConfig.load()
    val m = Model.wire
    b.copy(db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(enabled = true, prices = "scripted=1/2",
        roles = b.agents.roles.copy(extractor = m, reviewer = m, coldReader = m, skeptic = m, fixer = m, planner = m, writer = m)))

  private lazy val ds = testPool(baseCfg.db)

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def tool(name: String, args: String) =
    AgentResponse("", Seq(ToolCall("call_1", name, args)), StopReason.ToolUse, Usage1, Some("scripted"))
  private def str(s: String) = Json.fromString(s).noSpaces

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers, StubUpstream) =
    val deps = Deps(cfg, ds)
    val h    = Handlers(deps)
    val a    = AgentsHandlers(deps, h, (_, _) => Right(c))
    val up   = StubUpstream()
    a.attach(up)
    (a, h, up)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM approvals WHERE subject LIKE 'zzagent%' OR citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM article_versions WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  private def seed(h: Handlers): Unit =
    assert(h.putSource(Ck, SourcePut(title = "Bronze Age tin", year = Some(2019), topics = List("zzagent_tin"),
      status = Some(SourceStatus.Current),
      summary = Some("The Uluburun ship carried ten tons of copper and one ton of tin."),
      body = Some("The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."))).isRight)
    assert(h.putSource(Ck2, SourcePut(title = "Tin isotopes", year = Some(2024), topics = List("zzagent_tin"),
      body = Some("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia."))).isRight)
    assert(h.patchSource(Ck2, SourcePatch(metadata = Some(MetadataPatch(status = Some(SourceStatus.Contested))))).isRight)
    assert(h.putGoal(Goal, GoalPut("Zz tin in the Bronze Age", notes = Some("where the tin came from"))).isRight)

  private def start(a: AgentsHandlers, req: RunRequest): RunInfo =
    val run = a.startRun(req).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    AgentRepo.get(ds, run.id).get

  /** Why a run stopped where it did: its error and every failed attempt's note. */
  private def why(r: RunInfo): String =
    (r.error.toList ++ AgentRepo.steps(ds, r.id, 200).filter(_.error.isDefined).map(s => s"${s.name}#${s.attempt}: ${s.error.get}"))
      .mkString("\n")

  private def runAgain(a: AgentsHandlers, id: String): RunInfo =
    a.engine.execute(id)
    AgentRepo.get(ds, id).get

  // ── plan-goal ──────────────────────────────────────────────────────────────────────────────────

  private val planJson =
    s"""{"rationale":"tin sources and their dating","subGoals":[{"slug":"zzagent-tin-mines","title":"Tin mines","notes":"where it was mined","tags":["tin"]}],
       |"queries":["Bronze Age tin isotopes"],
       |"works":[{"doi":"10.5555/zzagent.in","why":"already read"},{"doi":"https://doi.org/10.5555/ZZAGENT.OUT","why":"the isotope study"},
       |{"doi":"10.5555/zzagent.ghost","why":"invented"}]}""".stripMargin

  private def stubPlan(up: StubUpstream): Unit =
    up.metas = Map(
      "10.5555/zzagent.in"  -> WorkRef(doi = Some("10.5555/zzagent.in"), title = Some("Bronze Age tin"), year = Some(2019),
        citekey = Some(Ck), inDb = true),
      "10.5555/zzagent.out" -> WorkRef(doi = Some("10.5555/zzagent.out"), title = Some("Zzagent tin isotopes"), year = Some(2023),
        authors = List("Berger", "Lockhoff")))

  test("plan-goal: the plan waits for a person, nothing is created; approving re-queues the run, which applies it"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(tool("facets", """{"field":"topics"}"""), text(planJson)))
    val (a, h, up) = handlers(c); seed(h); stubPlan(up)
    val run = start(a, RunRequest("plan-goal", Goal))
    assertEquals(run.status, "waiting", why(run))
    assertEquals((run.goal, run.targetKind), (Some(Goal), "goal"))
    // the planner was offered read tools only, and the DOIs were looked up before anyone saw them
    assertEquals(c.steps.head.flatMap(_.toolsOffered).map(_.name).toSet, Set("search", "facets", "get_source"))
    import scala.jdk.CollectionConverters.*
    assertEquals(up.calls.asScala.toList.filter(_.startsWith("meta")).sorted,
      List("meta 10.5555/zzagent.ghost", "meta 10.5555/zzagent.in", "meta 10.5555/zzagent.out"))
    val open = a.approvalsList(None, None).toOption.get.filter(_.subject.contains(Goal))
    assertEquals(open.map(_.kind), List("goal-plan"))
    val plan = open.head.plan.get
    assertEquals(plan.works.map(w => (w.doi, w.inCorpus)), List(("10.5555/zzagent.in", true), ("10.5555/zzagent.out", false)))
    assert(plan.dropped.exists(_.contains("10.5555/zzagent.ghost")), plan.dropped)
    assert(!GoalRepo.exists(ds, "zzagent-tin-mines"), "nothing is created before approval")
    assertEquals(GoalLinkRepo.links(ds, Goal), Nil)

    // approve: the run goes back to the queue and applies exactly the approved plan
    val decided = a.decide(open.head.id, "approve", DecisionRequest(Some("good plan"))).toOption.get
    assertEquals(decided.state, "approved")
    assertEquals(AgentRepo.get(ds, run.id).get.status, "queued")
    val done = runAgain(a, run.id)
    assertEquals(done.status, "done", done.error)
    assertEquals(c.steps.size, 1, "the planner is not asked again on resume")
    assertEquals(GoalRepo.get(ds, "zzagent-tin-mines").flatMap(_.parentSlug), Some(Goal))
    val links = GoalLinkRepo.links(ds, Goal)
    assertEquals(links.map(l => (l.kind, l.ref)).filter(_._1 != LinkKind.QueueItem),
      List((LinkKind.Source, Ck), (LinkKind.Query, "Bronze Age tin isotopes")))
    val items = h.ops.queueList("acquire", all = false).toOption.get.items.filter(_.text.contains("zzagent.out"))
    assertEquals(items.size, 1)
    assert(items.head.text.contains("https://doi.org/10.5555/zzagent.out — for goal zzagent-goal"), items.head.text)
    assert(links.exists(l => l.kind == LinkKind.QueueItem && l.ref == items.head.id.toString))
    assert(a.goalView(Goal).toOption.get.plan.exists(_.queries == List("Bronze Age tin isotopes")), "the approved plan is kept")
    val steps = AgentRepo.steps(ds, run.id, 50).map(_.name)
    assertEquals(steps, List("load", "propose", "verify", "approve", "apply"))
    // deciding twice is refused
    assert(a.decide(open.head.id, "reject", DecisionRequest()).left.exists(_.code.contains(AgentsApi.CodeConflict)))

  test("plan-goal: a rejected plan changes nothing; a cancelled waiting run rejects its approval"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(text(planJson)), Seq(text(planJson)))
    val (a, h, up) = handlers(c); seed(h); stubPlan(up)
    val run = start(a, RunRequest("plan-goal", Goal))
    val id  = a.approvalsList(None, None).toOption.get.find(_.subject.contains(Goal)).get.id
    assert(a.decide(id, "reject", DecisionRequest()).isRight)
    val done = runAgain(a, run.id)
    assertEquals(done.status, "done", done.error)
    assert(done.progress.exists(_.contains("rejected")), done.progress)
    assert(!GoalRepo.exists(ds, "zzagent-tin-mines"))
    assertEquals(GoalLinkRepo.links(ds, Goal), Nil)
    assert(h.ops.queueList("acquire", all = true).toOption.get.items.forall(!_.text.contains("zzagent.out")))

    val second = start(a, RunRequest("plan-goal", Goal))
    assertEquals(second.status, "waiting")
    assertEquals(a.control(second.id, "cancel", None, None).map(_.status), Right("cancelled"))
    val closed = AgentRepo.forStep(ds, second.id, 4).get
    assertEquals(closed.state, "rejected")
    assert(a.control(second.id, "resume", None, None).isLeft)

  // ── compile-article ──────────────────────────────────────────────────────────────────────────────

  private def extract(quote: String, claim: String) =
    s"""{"relevant":true,"reason":"bears on tin","claims":[{"claim":${str(claim)},"quote":${str(quote)},"date":null,"place":null,"confidence":"high"}]}"""

  private val newBody =
    s"""Tin reached the Aegean by sea [[$Ck]].
       |
       |## Disagreements
       |
       |Its origin is disputed: [[$Ck2]] (contested) places it in Central Asia.
       |
       |## Open questions
       |
       |Where the tin was mined is open [[$Ck]].""".stripMargin

  private def draftJson(body: String, section: Option[String] = None, reasons: String = "") =
    s"""{"title":"Zz tin","summary":"Where Aegean tin came from.","body":${str(body)},"section":${section.fold("null")(str)},"reasons":[$reasons]}"""

  private def compileScripts(c: ScriptedClient, draft: String): Unit =
    c.scripts ++= List(
      Seq(text(extract("The Uluburun ship carried ten tons of copper and one ton of tin.", "The ship carried tin."))),
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draft)))

  test("compile-article: claim table, evidence, draft, then the run WAITS; approval writes the new article and chains a review"):
    val c = ScriptedClient(Model)
    // the first draft forgets the contested status; code sends it back; the second passes
    compileScripts(c, draftJson(newBody.replace(" (contested)", "")))
    c.scripts += Seq(text(draftJson(newBody)))
    val (a, h, _) = handlers(c); seed(h)
    val run = start(a, RunRequest("compile-article", Slug, title = Some("Zz tin"), topics = List("zzagent_tin"), goal = Some(Goal)))
    assertEquals(run.status, "waiting", why(run))
    assert(h.getArticle(Slug).isLeft, "nothing is written before approval")
    val steps = AgentRepo.steps(ds, run.id, 4000)
    assertEquals(steps.map(s => (s.name, s.status)), List(("snapshot", "done"), ("filter", "done"), ("extract-1", "done"),
      ("extract-2", "done"), ("evidence", "done"), ("draft", "failed"), ("draft", "done")))
    assert(steps.exists(s => s.name == "draft" && s.error.exists(_.contains("does not say it is contested"))), steps)
    // the extraction saw the record as data and was offered no tool; the writer only the dry run of its draft
    val offered = c.steps.map(_.flatMap(_.toolsOffered).map(_.name).toSet)
    assertEquals(offered.toSet, Set(Set.empty[String], Set("check_edits")))
    assert(offered.dropRight(2).forall(_.isEmpty), offered)
    val appr = a.approvalsList(None, None).toOption.get.find(_.subject.contains(Slug)).get
    assertEquals(appr.kind, "article-publish")
    val d = appr.article.get
    assertEquals((d.mode, d.title, d.kind), (DraftMode.Create, "Zz tin", "concept"))
    assertEquals(d.cited.toSet, Set(Ck, Ck2))
    assertEquals(d.problems, Nil)

    assert(a.decide(appr.id, "approve", DecisionRequest()).isRight)
    val done = runAgain(a, run.id)
    assertEquals(done.status, "done", done.error)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, newBody.trim)
    // the chained review: a review-article run with fix, started by this one, for the same goal
    val child = AgentRepo.list(ds, None, 50).find(r => r.parent.contains(run.id)).get
    assertEquals((child.pipeline, child.target, child.params.fixes, child.origin, child.goal),
      ("review-article", Slug, true, Some("run"), Some(Goal)))
    assert(GoalLinkRepo.links(ds, Goal).exists(l => l.kind == LinkKind.Article && l.ref == Slug))
    val log = h.ops.queueList("log", all = true).toOption.get.items.map(_.text).filter(_.contains(Slug))
    assert(log.exists(_.contains("compiled a new article from 2 source(s)")), log)
    // #958: the article records the run's filter, so its Not used list and Method note are generated
    assertEquals(com.histmersion.db.ArticleRepo.load(ds, Slug).flatMap(_.filter), Some(ArticleFilter(topics = List("zzagent_tin"))))
    val cov = h.articleCoverage(Slug).fold(e => fail(e.message), identity)
    assertEquals((cov.total, cov.cited.map(_.citekey).toSet, cov.notUsed, cov.drift), (2, Set(Ck, Ck2), Nil, Nil))

  test("compile-article (#958): a hand-written Not used section is sent back; the writer's reason is stored, the list generated"):
    val c = ScriptedClient(Model)
    val onlyCk = newBody.replace(s"Its origin is disputed: [[$Ck2]] (contested) places it in Central Asia.", s"Its origin is argued [[$Ck]].")
    val handWritten = onlyCk + s"\n\n## Not used\n\n- [[$Ck2]]: later\n\n_Method: search {topics:[zzagent_tin]} → 2._"
    compileScripts(c, draftJson(handWritten))
    c.scripts += Seq(text(draftJson(onlyCk, reasons = s"""{"citekey":"$Ck2","reason":"Its isotope study dates a later trade."}""")))
    val auto = baseCfg.copy(agents = baseCfg.agents.copy(approvals = baseCfg.agents.approvals.copy(articlePublish = "auto"),
      compile = baseCfg.agents.compile.copy(review = false)))
    val (a, h, _) = handlers(c, auto); seed(h)
    val run = start(a, RunRequest("compile-article", Slug, title = Some("Zz tin"), topics = List("zzagent_tin")))
    assertEquals(run.status, "done", why(run))
    val first = AgentRepo.steps(ds, run.id, 4000).find(s => s.name == "draft" && s.status == "failed").flatMap(_.error).getOrElse("")
    assert(first.contains("a Not used section: code generates it") && first.contains("a _Method_ note: code generates it"), first)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, onlyCk.trim)
    val cov = h.articleCoverage(Slug).fold(e => fail(e.message), identity)
    assertEquals(cov.notUsed.map(e => (e.source.citekey, e.reason)), List((Ck2, Some("Its isotope study dates a later trade."))))
    assertEquals(cov.drift, Nil)
    assert(ArticleMarkdown.article(h.getArticle(Slug).toOption.get).contains(s"- [[$Ck2]] (contested) — Its isotope study dates a later trade."))

  test("compile-article: extending appends under a heading with ifUpdatedAt; a write meanwhile drops it (412), never overwrites"):
    val c = ScriptedClient(Model)
    val current = s"Tin reached the Aegean [[$Ck]].\n\n## Disagreements\n\nNone recorded [[$Ck]].\n\n## Open questions\n\nOpen [[$Ck]]."
    val (a, h, _) = handlers(c); seed(h)
    assert(h.putArticle(Slug, ArticlePut("Zz tin", "concept", current)).isRight)
    c.scripts ++= List(
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draftJson(s"A newer study is contested: [[$Ck2]] (contested) places the tin in Central Asia.", Some("## Disagreements")))))
    val run = start(a, RunRequest("compile-article", Slug, topics = List("zzagent_tin")))
    assertEquals(run.status, "waiting", why(run))
    // only the source the article does not cite yet was extracted
    assertEquals(AgentRepo.steps(ds, run.id, 10).map(_.name).count(_.startsWith("extract")), 1)
    // someone edits the article while the draft waits
    val theirs = current.replace("None recorded", "Nothing recorded")
    assert(h.patchArticle(Slug, ArticlePatch(body = Some(theirs))).isRight)
    val id = a.approvalsList(None, None).toOption.get.find(_.subject.contains(Slug)).get.id
    assert(a.decide(id, "approve", DecisionRequest()).isRight)
    val done = runAgain(a, run.id)
    assertEquals(done.status, "done", done.error)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, theirs.trim, "their text stands")
    assert(done.warnings.exists(_.contains("NOT appended")), done.warnings)
    assert(h.ops.queueList("tasks", all = false).toOption.get.items.exists(_.text.contains(s"[[$Slug]]")))
    assert(AgentRepo.list(ds, None, 50).forall(r => !r.parent.contains(run.id)), "no review of an unwritten article")

  test("compile-article: extending, approved in time, appends under the named heading"):
    val c = ScriptedClient(Model)
    val current = s"Tin reached the Aegean [[$Ck]].\n\n## Disagreements\n\nNone recorded [[$Ck]].\n\n## Open questions\n\nOpen [[$Ck]]."
    val (a, h, _) = handlers(c, baseCfg.copy(agents = baseCfg.agents.copy(compile = baseCfg.agents.compile.copy(review = false))))
    seed(h)
    assert(h.putArticle(Slug, ArticlePut("Zz tin", "concept", current)).isRight)
    val added = s"A newer study is contested: [[$Ck2]] (contested) places the tin in Central Asia."
    c.scripts ++= List(
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draftJson(added, Some("## Disagreements")))))
    val run = start(a, RunRequest("compile-article", Slug, topics = List("zzagent_tin")))
    val id = a.approvalsList(None, None).toOption.get.find(_.subject.contains(Slug)).get.id
    assert(a.decide(id, "approve", DecisionRequest()).isRight)
    assertEquals(runAgain(a, run.id).status, "done")
    val body = h.getArticle(Slug).toOption.get.article.body
    assert(body.indexOf(added) > body.indexOf("## Disagreements") && body.indexOf(added) < body.indexOf("## Open questions"), body)
    assert(h.articleVersions(Slug).toOption.get.nonEmpty, "the previous text is kept")
    // #958: an article that recorded no filter records the run's
    assertEquals(com.histmersion.db.ArticleRepo.load(ds, Slug).flatMap(_.filter), Some(ArticleFilter(topics = List("zzagent_tin"))))

  test("compile-article: with article-publish = auto the run does not wait (still never overwrites a slug)"):
    val c = ScriptedClient(Model)
    compileScripts(c, draftJson(newBody))
    val auto = baseCfg.copy(agents = baseCfg.agents.copy(approvals = baseCfg.agents.approvals.copy(articlePublish = "auto"),
      compile = baseCfg.agents.compile.copy(review = false)))
    val (a, h, _) = handlers(c, auto); seed(h)
    val run = start(a, RunRequest("compile-article", Slug, title = Some("Zz tin"), topics = List("zzagent_tin")))
    assertEquals(run.status, "done", why(run))
    assert(h.getArticle(Slug).isRight)
    val appr = a.approvalsList(Some("all"), None).toOption.get.find(_.subject.contains(Slug)).get
    assertEquals(appr.state, "approved")

  test("refused up front: a new article without a filter or a title; compile options on another pipeline; an unknown goal"):
    val c = ScriptedClient(Model)
    val (a, h, _) = handlers(c); seed(h)
    assert(a.startRun(RunRequest("compile-article", Slug, title = Some("x"))).left.exists(_.message.contains("filter")))
    assert(a.startRun(RunRequest("compile-article", Slug, topics = List("t"))).left.exists(_.message.contains("title")))
    assert(a.startRun(RunRequest("compile-article", "Not A Slug", title = Some("x"), topics = List("t"))).isLeft)
    assert(a.startRun(RunRequest("compile-article", Slug, title = Some("x"), topics = List("t"), kind = Some("BAD KIND"))).isLeft)
    assert(a.startRun(RunRequest("enrich-source", Ck, query = Some("q"))).left.exists(_.message.contains("compile-article")))
    assert(a.startRun(RunRequest("plan-goal", "zzagent-nope")).isLeft)
    assert(a.startRun(RunRequest("enrich-source", Ck, goal = Some("zzagent-nope"))).isLeft)
    val noWriter = baseCfg.copy(agents = baseCfg.agents.copy(roles = baseCfg.agents.roles.copy(writer = "")))
    val (b, _, _) = handlers(c, noWriter)
    assert(b.startRun(RunRequest("compile-article", Slug, title = Some("x"), topics = List("t"))).left.exists(_.message.contains("writer")))

  // ── #992: dry runs ───────────────────────────────────────────────────────────────────────────────

  test("plan-goal dry run: no approval is opened and nothing is created; the plan it would apply is on its step"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(text(planJson)))
    val (a, h, up) = handlers(c); seed(h); stubPlan(up)
    val before = CorpusSnapshot.of(ds)
    val run = start(a, RunRequest("plan-goal", Goal, dryRun = Some(true)))
    assertEquals(run.status, "done", why(run))
    assertEquals(CorpusSnapshot.of(ds), before, "no approval, no sub-goal, no link, no queue item, no log line")
    val steps = AgentRepo.steps(ds, run.id, 100000)
    assertEquals(steps.map(_.name), List("load", "propose", "verify", "approve", "apply"))
    val approve = steps.find(_.name == "approve").get
    assertEquals(approve.wouldWrite.map(_.what), List(s"goal-plan approval for $Goal (treated as approved)"))
    assert(approve.output.exists(_.contains(Engine.DryRunApproval)), approve.output)
    val apply = steps.find(_.name == "apply").get.wouldWrite
    assertEquals(apply.map(_.what), List(s"apply the plan of goal $Goal: 1 sub-goal(s), 1 query(ies), 2 work(s)"))
    assert(apply.head.payload.contains("zzagent-tin-mines"), apply.head.payload)

  test("compile-article dry run: the draft is treated as approved, the article it would create is on its step; no review run"):
    val c = ScriptedClient(Model)
    compileScripts(c, draftJson(newBody))
    val (a, h, _) = handlers(c); seed(h)
    val before = CorpusSnapshot.of(ds)
    val run = start(a, RunRequest("compile-article", Slug, title = Some("Zz tin"), topics = List("zzagent_tin"), goal = Some(Goal),
      dryRun = Some(true)))
    assertEquals(run.status, "done", why(run))
    assertEquals(CorpusSnapshot.of(ds), before, "no article, no approval, no goal link, no log line")
    assert(h.getArticle(Slug).isLeft)
    val steps = AgentRepo.steps(ds, run.id, 100000)
    assertEquals(steps.filter(_.status == "done").map(_.name), List("snapshot", "filter", "extract-1", "extract-2", "evidence",
      "draft", "approve", "write", "review", "close"))
    val write = steps.find(_.name == "write").get.wouldWrite
    assertEquals(write.map(_.what), List(s"create article [[$Slug]]"))
    assert(write.head.payload.contains("Tin reached the Aegean by sea") && write.head.payload.contains("zzagent_tin"), write.head.payload)
    assertEquals(steps.find(_.name == "close").get.wouldWrite.map(_.what), List("log line"))
    assert(AgentRepo.list(ds, None, 50).forall(r => !r.parent.contains(run.id)), "no review of an article that was not written")
