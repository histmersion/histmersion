package com.histmersion
package server.agents
import com.histmersion.util.json.RawJson.*
import hearth.kindlings.jsoniterjson.Json
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.automation.agents.ScriptedClient
import com.histmersion.domain.automation.agents.TestRuns

import com.histmersion.util.database.Transactor

import com.histmersion.domain.research.articles.ArticleFixtures

import com.histmersion.domain.library.workflows.*

import com.histmersion.domain.automation.agents.{AgentsControllers, Engine, LlmClient}
import com.histmersion.domain.automation.managers.{ManagerLight, Zero}

import com.augustnagro.magnum.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.Goal.Plans.*
import com.histmersion.domain.automation.agents.AgentRepo
import com.histmersion.util.database.{Db, Migrate}
import com.histmersion.domain.automation.goals.{GoalLinkRepo, GoalRepo}
import com.histmersion.server.{Deps, ServerWiring}
import sttp.ai.core.agent.*
import com.histmersion.db.*
import com.histmersion.api.automation.{AgentsApi, Goal}
import com.histmersion.Year
import com.histmersion.RowCount
import com.histmersion.domain.automation.{Approval, Goal as DomainGoal, Run}
import com.histmersion.domain.library.{Citekey, Doi}
import com.histmersion.domain.library.search.Search
import com.histmersion.util.jobs.Job
import com.histmersion.api.library.{
  Assessment,
  Candidates,
  CandidateSweep,
  DiscoverResult,
  FollowupRow,
  FollowupsResult,
  MetadataPatch,
  WorkRef
}
import com.histmersion.api.research.ArticleMarkdown
import com.histmersion.api.library.Source
import com.histmersion.api.library.Ingest
import com.histmersion.api.research.Article
import com.histmersion.api.automation.Managers
import com.histmersion.db.DbSuite
import com.histmersion.db.TestDb
import com.histmersion.api.automation.Agents
import com.histmersion.config.DbConfig
import com.histmersion.config.AgentsConfig
import com.histmersion.config.AgentRolesConfig
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.server.views.WorkListView
import com.histmersion.domain.automation.agents.ApprovalService
import com.histmersion.api.library.Filter
import com.histmersion.config.AgentApprovalsConfig
import com.histmersion.config.AgentCompileConfig
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.api.Api

/** Answers from fixtures: DOI metadata, citing papers, searches; counts what was asked. */
final class StubUpstream extends AgentUpstream:
  @volatile var metas: Map[String, WorkRef] = Map.empty
  @volatile var citing: Map[String, List[FollowupRow]] = Map.empty
  @volatile var found: Map[String, List[WorkRef]] = Map.empty
  val calls = java.util.concurrent.ConcurrentLinkedQueue[String]()
  def meta(doi: Doi) = {
    calls.add(s"meta $doi");
    metas.get(doi.value).toRight(Problem.Unclassified(Legacy.Text(s"$doi: not found upstream")))
  }
  def refs(citekey: Citekey, apply: AgentUpstream.RefsApply) = Left(Problem.Unclassified(Legacy.Text("not stubbed")))
  def assess(citekey: Citekey, apply: AgentUpstream.AssessApply) =
    calls.add(s"assess $citekey")
    Right(
      Assessment(
        Assessment.Target(citekey.value),
        None,
        Some(api.library.Citekey(citekey.value)),
        None,
        None,
        None,
        Assessment.IsRetracted(false),
        Nil,
        Nil,
        Assessment.Flagged(false),
        Assessment.Verdict("current"),
        Assessment.Recommendation("fine")
      )
    )
  def followups(id: AgentUpstream.FollowupsId, since: Option[Year], limit: Option[FollowupsInput.Limit]) =
    calls.add(s"followups $id")
    val rows = citing.getOrElse(id.value, Nil)
    Right(
      FollowupsResult(
        FollowupsResult.Target(id.value),
        None,
        FollowupsResult.Backend("stub"),
        FollowupsResult.Count(rows.size),
        FollowupsResult.EdgesAdded(0),
        rows
      )
    )
  def discover(query: Search.Query, limit: AgentUpstream.Limit) =
    calls.add(s"discover $query")
    val ws = found.getOrElse(query.value, Nil)
    Right(DiscoverResult(DiscoverResult.Query(query.value), DiscoverResult.Count(ws.size), ws))

  /** A watcher's legal ingest: what `onIngest` answers (nothing connected by default). */
  @volatile var onIngest: String => Either[Problem, Ingest.Report] = _ =>
    Left(Problem.Unclassified(Legacy.Text("not stubbed")))
  override def ingestLegal(doi: Doi) = {
    calls.add(s"ingest $doi"); onIngest(doi.value)
  }

  /** The deterministic candidate search: what each goal's sweep finds (not connected unless set). */
  @volatile var sweepFinds: Map[String, List[Source.Candidate]] = Map.empty
  private val marked = java.util.concurrent.ConcurrentHashMap[Source.Candidate.Id, Candidates.State.Wire]()
  override def sweepCandidates(goal: DomainGoal.Slug, dryRun: AgentUpstream.SweepCandidatesDryRun) =
    calls.add(s"sweep $goal")
    sweepFinds
      .get(goal.value)
      .toRight(Problem.Unclassified(Legacy.Text("the candidate search is not connected")))
      .map(cs =>
        CandidateSweep(
          CandidateSweep.Goal(goal.value),
          Nil,
          CandidateSweep.Found(cs.size),
          CandidateSweep.Added(cs.size),
          CandidateSweep.Again(0),
          Map.empty,
          CandidateSweep.Unseen(cs.count(c => !marked.containsKey(c.id))),
          CandidateSweep.DryRun(dryRun.value)
        )
      )
  override def candidates(goal: DomainGoal.Slug, state: Candidates.State.Wire, limit: AgentUpstream.Limit) =
    Right(
      sweepFinds
        .getOrElse(goal.value, Nil)
        .map(c =>
          c.modify(_.state).setTo(Option(marked.get(c.id)).map(w => Source.Candidate.State(w.value)).getOrElse(c.state))
        )
        .filter(_.state == Source.Candidate.State(state.value))
        .take(limit.value)
    )
  override def markCandidates(ids: List[Source.Candidate.Id], state: Candidates.State.Wire) =
    ids.foreach(i => marked.put(i, state))
    Right(RowCount(ids.size))

/** The domain ids of what the API answered with, for the suites that drive the engine and the repositories by hand. */
object ApiIds:
  def run(id: RunInfo.Id): Run.Id = Run.Id.assume(id.value)
  def job(id: RunInfo.Id): Job.Id = Job.Id.assume(id.value)
  def approval(id: ApprovalInfo.Id): Approval.Id = Approval.Id.assume(id.value)
  def manager(id: Managers.ManagerInfo.Id): ManagerLight.Id = ManagerLight.Id(id.value)

/** The P3 pipelines that WAIT for a person, end to end against a `*_test` database with a scripted model: plan-goal
  * (nothing is created before the plan is approved; approval re-queues the run, which applies exactly the approved
  * plan; a rejection changes nothing; a cancel rejects the approval) and compile-article (claim table with verified
  * quotes, evidence, draft, the blocking approval, a new article written only after approval, the chained review run,
  * an extension dropped on 412, and the `auto` mode). Skipped without HISTMERSION_TEST_DB_URL.
  */
class GoalAgentsRunSuite extends DbSuite with db.Bounded with RunTranscripts:
  private val testDb = TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Goal = DomainGoal.Slug("zzagent-goal")
  private val GoalSlug = api.automation.Goal.Slug("zzagent-goal")
  private val GoalTarget = RunRequest.Target("zzagent-goal")
  private val GoalSubject = ApprovalInfo.Subject("zzagent-goal")
  private val Ck = Citekey("zzagent2019_tin")
  private val ApiCk = api.library.Citekey("zzagent2019_tin")
  private val Ck2 = Citekey("zzagent2024_iso")
  private val CkTarget = RunRequest.Target("zzagent2019_tin")
  private val Slug = domain.research.Slug("zzagent_tin")
  private val SlugTarget = RunRequest.Target("zzagent_tin")
  private val SlugSubject = ApprovalInfo.Subject("zzagent_tin")
  private val SlugRef = GoalLink.Ref("zzagent_tin")
  private val PlanGoal = RunRequest.Pipeline("plan-goal")
  private val CompileArticle = RunRequest.Pipeline("compile-article")
  private val EnrichSource = RunRequest.Pipeline("enrich-source")
  private val ZzTitle = RunRequest.Title("Zz tin")
  private val TinTopics = List(RunRequest.Topics("zzagent_tin"))
  private val Model = ModelRef(ProviderKind.Local, Agents.ModelRef.Model("scripted"))
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private lazy val baseCfg =
    val b = AppConfig.load()
    val m = Model.wire
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
          .setTo(
            b.agents.roles
              .modify(_.extractor)
              .setTo(AgentRolesConfig.Extractor(m.value))
              .modify(_.reviewer)
              .setTo(AgentRolesConfig.Reviewer(m.value))
              .modify(_.coldReader)
              .setTo(AgentRolesConfig.ColdReader(m.value))
              .modify(_.skeptic)
              .setTo(AgentRolesConfig.Skeptic(m.value))
              .modify(_.fixer)
              .setTo(AgentRolesConfig.Fixer(m.value))
              .modify(_.planner)
              .setTo(AgentRolesConfig.Planner(m.value))
              .modify(_.writer)
              .setTo(AgentRolesConfig.Writer(m.value))
          )
      )

  private lazy val ds = testPool(baseCfg.db)
  protected def transcriptDs: javax.sql.DataSource = ds

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def tool(name: String, args: String) =
    AgentResponse("", Seq(ToolCall("call_1", name, args)), StopReason.ToolUse, Usage1, Some("scripted"))
  private def str(s: String) = Json.fromString(s).noSpaces

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
      sql"DELETE FROM approvals WHERE subject LIKE 'zzagent%' OR citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM goals WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM article_versions WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzagent%'".update.run()
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
            title = api.library.Title("Bronze Age tin"),
            year = (Some(2019)).map(api.catalogue.Year.apply),
            topics = List(Source.Topics("zzagent_tin")),
            status = Some(Source.Status.Current),
            summary = Some(Source.Summary("The Uluburun ship carried ten tons of copper and one ton of tin.")),
            body = Some(
              Source.OcrMarkdown(
                "The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."
              )
            )
          )
        )
        .isRight
    )
    assert(
      h.library.sources
        .put(
          Ck2,
          Source.Put(
            title = api.library.Title("Tin isotopes"),
            year = (Some(2024)).map(api.catalogue.Year.apply),
            topics = List(Source.Topics("zzagent_tin")),
            body = Some(
              Source.OcrMarkdown("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia.")
            )
          )
        )
        .isRight
    )
    assert(
      h.library.sources
        .patch(
          Ck2,
          Source
            .Patch(metadata = Some(MetadataPatch(status = Some(Source.Status.Contested))))
        )
        .isRight
    )
    assert(
      h.goals
        .put(
          Goal,
          api.automation.Goal.Put(
            api.automation.Goal.Title("Zz tin in the Bronze Age"),
            notes = Some(api.automation.Goal.Notes("where the tin came from"))
          )
        )
        .isRight
    )

  private def start(a: AgentsControllers, req: RunRequest): RunInfo =
    val run = a.startRun(req).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    a.engine.execute(ApiIds.run(run.id))
    connect(ds)(AgentRepo.get(run.id)).get

  /** Why a run stopped where it did: its error and every failed attempt's note. */
  private def why(r: RunInfo): String =
    (r.error.toList ++ connect(ds)(AgentRepo.steps(ApiIds.job(r.id), AgentRepo.OutputChars(200)))
      .filter(_.error.isDefined)
      .map(s => s"${s.name}#${s.attempt}: ${s.error.get.sentence}"))
      .mkString("\n")

  private def runAgain(a: AgentsControllers, id: RunInfo.Id): RunInfo =
    a.engine.execute(ApiIds.run(id))
    connect(ds)(AgentRepo.get(id)).get

  // ── plan-goal ──────────────────────────────────────────────────────────────────────────────────

  private val planJson =
    s"""{"rationale":"tin sources and their dating","subGoals":[{"slug":"zzagent-tin-mines","title":"Tin mines","notes":"where it was mined","tags":["tin"]}],
       |"queries":["Bronze Age tin isotopes"],
       |"works":[{"doi":"10.5555/zzagent.in","why":"already read"},{"doi":"https://doi.org/10.5555/ZZAGENT.OUT","why":"the isotope study"},
       |{"doi":"10.5555/zzagent.ghost","why":"invented"}]}""".stripMargin

  private def stubPlan(up: StubUpstream): Unit =
    up.metas = Map(
      "10.5555/zzagent.in" -> WorkRef(
        doi = (Some("10.5555/zzagent.in")).map(api.library.Doi.apply),
        title = (Some("Bronze Age tin")).map(api.library.Title.apply),
        year = (Some(2019)).map(api.catalogue.Year.apply),
        citekey = Some(ApiCk),
        inDb = WorkRef.InDb(true)
      ),
      "10.5555/zzagent.out" -> WorkRef(
        doi = (Some("10.5555/zzagent.out")).map(api.library.Doi.apply),
        title = (Some("Zzagent tin isotopes")).map(api.library.Title.apply),
        year = (Some(2023)).map(api.catalogue.Year.apply),
        authors = (List("Berger", "Lockhoff")).map(api.library.AuthorName.apply)
      )
    )

  test("plan-goal: the plan waits for a person, nothing is created; approving re-queues the run, which applies it"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(tool("facets", """{"field":"topics"}"""), text(planJson)))
    val (a, h, up) = handlers(c); seed(h); stubPlan(up)
    val run = start(a, RunRequest(PlanGoal, GoalTarget))
    assertEquals(run.status, "waiting", why(run))
    assertEquals((run.goal.map(_.value: String), run.targetKind), (Some(Goal), "goal"))
    // the planner was offered read tools only, and the DOIs were looked up before anyone saw them
    assertEquals(c.steps.head.flatMap(_.toolsOffered).map(_.name).toSet, Set("search", "facets", "get_source"))
    import scala.jdk.CollectionConverters.*
    assertEquals(
      up.calls.asScala.toList.filter(_.startsWith("meta")).sorted,
      List("meta 10.5555/zzagent.ghost", "meta 10.5555/zzagent.in", "meta 10.5555/zzagent.out")
    )
    val open = a.approvalsList(None, None).left.map(Api.Error(_)).toOption.get.filter(_.subject.contains(GoalSubject))
    assertEquals(open.map(_.kind), List("goal-plan"))
    val plan = open.head.plan.get
    assertEquals(
      plan.works.map(w => (w.doi, w.inCorpus)),
      List(("10.5555/zzagent.in", true), ("10.5555/zzagent.out", false))
    )
    assert(plan.dropped.exists(_.value.contains("10.5555/zzagent.ghost")), plan.dropped)
    assert(
      !Transactor(ds).read(GoalRepo.exists(domain.automation.Goal.Slug("zzagent-tin-mines"))),
      "nothing is created before approval"
    )
    assertEquals(connect(ds)(GoalLinkRepo.links(Goal)), Nil)

    // approve: the run goes back to the queue and applies exactly the approved plan
    val decided = a
      .decide(
        ApiIds.approval(open.head.id),
        Approval.Outcome("approve"),
        DecisionRequest(Some(api.automation.Approval.Note("good plan")))
      )
      .toOption
      .get
    assertEquals(decided.state, "approved")
    assertEquals(connect(ds)(AgentRepo.get(run.id)).get.status, "queued")
    val done = runAgain(a, run.id)
    assertEquals(done.status, "done", done.error)
    assertEquals(c.steps.size, 1, "the planner is not asked again on resume")
    assertEquals(
      connect(ds)(GoalRepo.get(domain.automation.Goal.Slug("zzagent-tin-mines")))
        .flatMap(_.parentSlug)
        .map(_.value: String),
      Some(Goal)
    )
    val links = connect(ds)(GoalLinkRepo.links(Goal))
    assertEquals(
      links.map(l => (l.kind, l.ref.value: String)).filter(_._1 != LinkKind.QueueItem),
      List((LinkKind.Source, Ck.value), (LinkKind.Query, "Bronze Age tin isotopes"))
    )
    val items = h.workListView
      .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(false))
      .toOption
      .get
      .items
      .filter(_.text.value.contains("zzagent.out"))
    assertEquals(items.size, 1)
    assert(
      items.head.text.value.contains("https://doi.org/10.5555/zzagent.out — for goal zzagent-goal"),
      items.head.text
    )
    assert(links.exists(l => l.kind == LinkKind.QueueItem && l.ref == GoalLink.Ref(items.head.id.toString)))
    assert(
      a.goalView(Goal).toOption.get.plan.exists(_.queries == List(Plan.Queries("Bronze Age tin isotopes"))),
      "the approved plan is kept"
    )
    val steps = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(50))).map(_.name)
    assertEquals(steps, List("load", "propose", "verify", "approve", "apply"))
    // deciding twice is refused
    assert(
      a.decide(ApiIds.approval(open.head.id), Approval.Outcome("reject"), DecisionRequest())
        .left
        .exists(_.code.exists(_.value == AgentsApi.CodeConflict))
    )

  test("plan-goal: a rejected plan changes nothing; a cancelled waiting run rejects its approval"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(text(planJson)), Seq(text(planJson)))
    val (a, h, up) = handlers(c); seed(h); stubPlan(up)
    val run = start(a, RunRequest(PlanGoal, GoalTarget))
    val id =
      a.approvalsList(None, None).left.map(Api.Error(_)).toOption.get.find(_.subject.contains(GoalSubject)).get.id
    assert(a.decide(ApiIds.approval(id), Approval.Outcome("reject"), DecisionRequest()).isRight)
    val done = runAgain(a, run.id)
    assertEquals(done.status, "done", done.error)
    assert(done.progress.exists(_.value.contains("rejected")), done.progress)
    assert(!Transactor(ds).read(GoalRepo.exists(domain.automation.Goal.Slug("zzagent-tin-mines"))))
    assertEquals(connect(ds)(GoalLinkRepo.links(Goal)), Nil)
    assert(
      h.workListView
        .list(WorkListControllers.KindOfList("acquire"), all = WorkListView.IncludeAll(true))
        .toOption
        .get
        .items
        .forall(!_.text.value.contains("zzagent.out"))
    )

    val second = start(a, RunRequest(PlanGoal, GoalTarget))
    assertEquals(second.status, "waiting")
    assertEquals(
      a.control(ApiIds.run(second.id), Agents.Action("cancel"), None, None).map(_.status),
      Right("cancelled")
    )
    val closed = TestRuns.approvals(ds).forStep(ApiIds.job(second.id), ApprovalService.StepSeq(4)).get
    assertEquals(closed.state, "rejected")
    assert(a.control(ApiIds.run(second.id), Agents.Action("resume"), None, None).isLeft)

  // ── compile-article ──────────────────────────────────────────────────────────────────────────────

  private def extract(quote: String, claim: String) =
    s"""{"relevant":true,"reason":"bears on tin","claims":[{"claim":${str(claim)},"quote":${str(
        quote
      )},"date":null,"place":null,"confidence":"high"}]}"""

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
    s"""{"title":"Zz tin","summary":"Where Aegean tin came from.","body":${str(body)},"section":${section.fold("null")(
        str
      )},"reasons":[$reasons]}"""

  private def compileScripts(c: ScriptedClient, draft: String): Unit =
    c.scripts ++= List(
      Seq(text(extract("The Uluburun ship carried ten tons of copper and one ton of tin.", "The ship carried tin."))),
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draft))
    )

  test(
    "compile-article: claim table, evidence, draft, then the run WAITS; approval writes the new article and chains a review"
  ):
    val c = ScriptedClient(Model)
    // the first draft forgets the contested status; code sends it back; the second passes
    compileScripts(c, draftJson(newBody.replace(" (contested)", "")))
    c.scripts += Seq(text(draftJson(newBody)))
    val (a, h, _) = handlers(c); seed(h)
    val run = start(
      a,
      RunRequest(CompileArticle, SlugTarget, title = Some(ZzTitle), topics = TinTopics, goal = Some(GoalSlug))
    )
    assertEquals(run.status, "waiting", why(run))
    assert(h.research.articles.get(Slug).isLeft, "nothing is written before approval")
    val steps = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(4000)))
    assertEquals(
      steps.map(s => (s.name, s.status)),
      List(
        ("snapshot", "done"),
        ("filter", "done"),
        ("extract-1", "done"),
        ("extract-2", "done"),
        ("evidence", "done"),
        ("draft", "failed"),
        ("draft", "done")
      )
    )
    assert(
      steps.exists(s => s.name.value == "draft" && s.error.exists(_.sentence.contains("does not say it is contested"))),
      steps
    )
    // the extraction saw the record as data and was offered no tool; the writer only the dry run of its draft
    val offered = c.steps.map(_.flatMap(_.toolsOffered).map(_.name).toSet)
    assertEquals(offered.toSet, Set(Set.empty[String], Set("check_edits")))
    assert(offered.dropRight(2).forall(_.isEmpty), offered)
    val appr = a.approvalsList(None, None).left.map(Api.Error(_)).toOption.get.find(_.subject.contains(SlugSubject)).get
    assertEquals(appr.kind, "article-publish")
    val d = appr.article.get
    assertEquals((d.mode, d.title, d.kind), (DraftMode.Create, "Zz tin", "concept"))
    assertEquals(d.cited.toSet, Set(Ck, Ck2))
    assertEquals(d.problems, Nil)

    assert(a.decide(ApiIds.approval(appr.id), Approval.Outcome("approve"), DecisionRequest()).isRight)
    val done = runAgain(a, run.id)
    assertEquals(done.status, "done", done.error)
    assertEquals(h.research.articles.get(Slug).toOption.get.article.body.value.trim, newBody.trim)
    // the chained review: a review-article run with fix, started by this one, for the same goal
    val child = connect(ds)(AgentRepo.list(None, PageSize(50))).find(r => r.parent.contains(run.id)).get
    assertEquals(
      (child.pipeline, child.target.value: String, child.params.fixes, child.origin, child.goal.map(_.value: String)),
      ("review-article", Slug, true, Some("run"), Some(Goal))
    )
    assert(connect(ds)(GoalLinkRepo.links(Goal)).exists(l => l.kind == LinkKind.Article && l.ref == SlugRef))
    val log = h.workListView
      .list(WorkListControllers.KindOfList("log"), all = WorkListView.IncludeAll(true))
      .toOption
      .get
      .items
      .map(_.text)
      .filter(_.value.contains(Slug.value))
    assert(log.exists(_.value.contains("compiled a new article from 2 source(s)")), log)
    // #958: the article records the run's filter, so its Not used list and Method note are generated
    assertEquals(
      ArticleFixtures.load(ds, Slug).flatMap(_.filter),
      Some(Article.Filter(topics = List(Filter.Topics("zzagent_tin"))))
    )
    val cov = h.research.articles.coverage(Slug).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      (cov.total, cov.cited.map(_.citekey.value: String).toSet, cov.notUsed, cov.drift),
      (2, Set(Ck, Ck2), Nil, Nil)
    )

  test(
    "compile-article (#958): a hand-written Not used section is sent back; the writer's reason is stored, the list generated"
  ):
    val c = ScriptedClient(Model)
    val onlyCk = newBody.replace(
      s"Its origin is disputed: [[$Ck2]] (contested) places it in Central Asia.",
      s"Its origin is argued [[$Ck]]."
    )
    val handWritten = onlyCk + s"\n\n## Not used\n\n- [[$Ck2]]: later\n\n_Method: search {topics:[zzagent_tin]} → 2._"
    compileScripts(c, draftJson(handWritten))
    c.scripts += Seq(
      text(draftJson(onlyCk, reasons = s"""{"citekey":"$Ck2","reason":"Its isotope study dates a later trade."}"""))
    )
    val auto = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.approvals)
          .setTo(baseCfg.agents.approvals.modify(_.articlePublish).setTo(AgentApprovalsConfig.Gate("auto")))
      )
      .modify(_.researchAgents)
      .setTo(
        baseCfg.researchAgents
          .modify(_.compile)
          .setTo(baseCfg.researchAgents.compile.modify(_.review).setTo(AgentCompileConfig.Review(false)))
      )
    val (a, h, _) = handlers(c, auto); seed(h)
    val run = start(a, RunRequest(CompileArticle, SlugTarget, title = Some(ZzTitle), topics = TinTopics))
    assertEquals(run.status, "done", why(run))
    val first = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(4000)))
      .find(s => s.name.value == "draft" && s.status.value == "failed")
      .flatMap(_.error)
      .map(_.sentence)
      .getOrElse("")
    assert(
      first.contains("a Not used section: code generates it") && first.contains("a _Method_ note: code generates it"),
      first
    )
    assertEquals(h.research.articles.get(Slug).toOption.get.article.body.value.trim, onlyCk.trim)
    val cov = h.research.articles.coverage(Slug).fold(e => fail(e.problems.sentences.mkString("; ")), identity)
    assertEquals(
      cov.notUsed.map(e => (e.source.citekey.value: String, e.reason.map(w => s"$w"))),
      List((Ck2, Some("Its isotope study dates a later trade.")))
    )
    assertEquals(cov.drift, Nil)
    assert(
      ArticleMarkdown
        .article(h.research.articles.get(Slug).toOption.get)
        .contains(s"- [[$Ck2]] (contested) — Its isotope study dates a later trade.")
    )

  test(
    "compile-article: extending appends under a heading with ifUpdatedAt; a write meanwhile drops it (412), never overwrites"
  ):
    val c = ScriptedClient(Model)
    val current =
      s"Tin reached the Aegean [[$Ck]].\n\n## Disagreements\n\nNone recorded [[$Ck]].\n\n## Open questions\n\nOpen [[$Ck]]."
    val (a, h, _) = handlers(c); seed(h)
    assert(
      h.research.articles
        .put(Slug, Article.Put(Article.Title("Zz tin"), Article.Kind.Wire("concept"), Article.Body(current)))
        .isRight
    )
    c.scripts ++= List(
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(
        text(
          draftJson(
            s"A newer study is contested: [[$Ck2]] (contested) places the tin in Central Asia.",
            Some("## Disagreements")
          )
        )
      )
    )
    val run = start(a, RunRequest(CompileArticle, SlugTarget, topics = TinTopics))
    assertEquals(run.status, "waiting", why(run))
    // only the source the article does not cite yet was extracted
    assertEquals(
      connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(10)))
        .map(_.name)
        .count(_.value.startsWith("extract")),
      1
    )
    // someone edits the article while the draft waits
    val theirs = current.replace("None recorded", "Nothing recorded")
    assert(h.research.articles.patch(Slug, Article.Patch(body = Some(Article.Body(theirs)))).isRight)
    val id =
      a.approvalsList(None, None).left.map(Api.Error(_)).toOption.get.find(_.subject.contains(SlugSubject)).get.id
    assert(a.decide(ApiIds.approval(id), Approval.Outcome("approve"), DecisionRequest()).isRight)
    val done = runAgain(a, run.id)
    assertEquals(done.status, "done", done.error)
    assertEquals(h.research.articles.get(Slug).toOption.get.article.body.value.trim, theirs.trim, "their text stands")
    assert(done.warnings.exists(_.value.contains("NOT appended")), done.warnings)
    assert(
      h.workListView
        .list(WorkListControllers.KindOfList("tasks"), all = WorkListView.IncludeAll(false))
        .toOption
        .get
        .items
        .exists(_.text.value.contains(s"[[$Slug]]"))
    )
    assert(
      connect(ds)(AgentRepo.list(None, PageSize(50))).forall(r => !r.parent.contains(run.id)),
      "no review of an unwritten article"
    )

  test("compile-article: extending, approved in time, appends under the named heading"):
    val c = ScriptedClient(Model)
    val current =
      s"Tin reached the Aegean [[$Ck]].\n\n## Disagreements\n\nNone recorded [[$Ck]].\n\n## Open questions\n\nOpen [[$Ck]]."
    val (a, h, _) =
      handlers(
        c,
        baseCfg
          .modify(_.researchAgents)
          .setTo(
            baseCfg.researchAgents
              .modify(_.compile)
              .setTo(baseCfg.researchAgents.compile.modify(_.review).setTo(AgentCompileConfig.Review(false)))
          )
      )
    seed(h)
    assert(
      h.research.articles
        .put(Slug, Article.Put(Article.Title("Zz tin"), Article.Kind.Wire("concept"), Article.Body(current)))
        .isRight
    )
    val added = s"A newer study is contested: [[$Ck2]] (contested) places the tin in Central Asia."
    c.scripts ++= List(
      Seq(text(extract("the tin of the Uluburun cargo came from Central Asia", "The tin came from Central Asia."))),
      Seq(text(draftJson(added, Some("## Disagreements"))))
    )
    val run = start(a, RunRequest(CompileArticle, SlugTarget, topics = TinTopics))
    val id =
      a.approvalsList(None, None).left.map(Api.Error(_)).toOption.get.find(_.subject.contains(SlugSubject)).get.id
    assert(a.decide(ApiIds.approval(id), Approval.Outcome("approve"), DecisionRequest()).isRight)
    assertEquals(runAgain(a, run.id).status, "done")
    val body = h.research.articles.get(Slug).toOption.get.article.body.value
    assert(
      body.indexOf(added) > body.indexOf("## Disagreements") && body.indexOf(added) < body.indexOf("## Open questions"),
      body
    )
    assert(h.research.articles.versions(Slug).toOption.get.nonEmpty, "the previous text is kept")
    // #958: an article that recorded no filter records the run's
    assertEquals(
      ArticleFixtures.load(ds, Slug).flatMap(_.filter),
      Some(Article.Filter(topics = List(Filter.Topics("zzagent_tin"))))
    )

  test("compile-article: with article-publish = auto the run does not wait (still never overwrites a slug)"):
    val c = ScriptedClient(Model)
    compileScripts(c, draftJson(newBody))
    val auto = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents
          .modify(_.approvals)
          .setTo(baseCfg.agents.approvals.modify(_.articlePublish).setTo(AgentApprovalsConfig.Gate("auto")))
      )
      .modify(_.researchAgents)
      .setTo(
        baseCfg.researchAgents
          .modify(_.compile)
          .setTo(baseCfg.researchAgents.compile.modify(_.review).setTo(AgentCompileConfig.Review(false)))
      )
    val (a, h, _) = handlers(c, auto); seed(h)
    val run = start(a, RunRequest(CompileArticle, SlugTarget, title = Some(ZzTitle), topics = TinTopics))
    assertEquals(run.status, "done", why(run))
    assert(h.research.articles.get(Slug).isRight)
    val appr =
      a.approvalsList(Some(ApprovalQuery.State("all")), None)
        .left
        .map(Api.Error(_))
        .toOption
        .get
        .find(_.subject.contains(SlugSubject))
        .get
    assertEquals(appr.state, "approved")

  test(
    "refused up front: a new article without a filter or a title; compile options on another pipeline; an unknown goal"
  ):
    val c = ScriptedClient(Model)
    val (a, h, _) = handlers(c); seed(h)
    assert(
      a.startRun(RunRequest(CompileArticle, SlugTarget, title = Some(RunRequest.Title("x"))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("filter"))
    )
    assert(
      a.startRun(RunRequest(CompileArticle, SlugTarget, topics = List(RunRequest.Topics("t"))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("title"))
    )
    assert(
      a.startRun(
        RunRequest(
          CompileArticle,
          RunRequest.Target("Not A Slug"),
          title = Some(RunRequest.Title("x")),
          topics = List(RunRequest.Topics("t"))
        )
      ).isLeft
    )
    assert(
      a.startRun(
        RunRequest(
          CompileArticle,
          SlugTarget,
          title = Some(RunRequest.Title("x")),
          topics = List(RunRequest.Topics("t")),
          kind = Some(RunRequest.Kind("BAD KIND"))
        )
      ).isLeft
    )
    assert(
      a.startRun(RunRequest(EnrichSource, CkTarget, query = Some(RunRequest.Query("q"))))
        .left
        .exists(_.problems.sentences.mkString("; ").contains("compile-article"))
    )
    assert(a.startRun(RunRequest(PlanGoal, RunRequest.Target("zzagent-nope"))).isLeft)
    assert(
      a.startRun(
        RunRequest(EnrichSource, CkTarget, goal = Some(api.automation.Goal.Slug("zzagent-nope")))
      ).isLeft
    )
    val noWriter = baseCfg
      .modify(_.agents)
      .setTo(
        baseCfg.agents.modify(_.roles).setTo(baseCfg.agents.roles.modify(_.writer).setTo(AgentRolesConfig.Writer("")))
      )
    val (b, _, _) = handlers(c, noWriter)
    assert(
      b.startRun(
        RunRequest(
          CompileArticle,
          SlugTarget,
          title = Some(RunRequest.Title("x")),
          topics = List(RunRequest.Topics("t"))
        )
      ).left
        .exists(_.problems.sentences.mkString("; ").contains("writer"))
    )

  // ── #992: dry runs ───────────────────────────────────────────────────────────────────────────────

  test("plan-goal dry run: no approval is opened and nothing is created; the plan it would apply is on its step"):
    val c = ScriptedClient(Model)
    c.scripts ++= List(Seq(text(planJson)))
    val (a, h, up) = handlers(c); seed(h); stubPlan(up)
    val before = CorpusSnapshot.of(ds)
    val run = start(a, RunRequest(PlanGoal, GoalTarget, dryRun = Some(RunRequest.DryRun(true))))
    assertEquals(run.status, "done", why(run))
    assertEquals(CorpusSnapshot.of(ds), before, "no approval, no sub-goal, no link, no queue item, no log line")
    val steps = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(100000)))
    assertEquals(steps.map(_.name), List("load", "propose", "verify", "approve", "apply"))
    val approve = steps.find(_.name.value == "approve").get
    assertEquals(approve.wouldWrite.map(_.what), List(s"goal-plan approval for $Goal (treated as approved)"))
    assert(approve.output.exists(_.value.contains(Engine.DryRunApproval)), approve.output)
    val apply = steps.find(_.name.value == "apply").get.wouldWrite
    assertEquals(apply.map(_.what), List(s"apply the plan of goal $Goal: 1 sub-goal(s), 1 query(ies), 2 work(s)"))
    assert(apply.head.payload.value.contains("zzagent-tin-mines"), apply.head.payload)

  test(
    "compile-article dry run: the draft is treated as approved, the article it would create is on its step; no review run"
  ):
    val c = ScriptedClient(Model)
    compileScripts(c, draftJson(newBody))
    val (a, h, _) = handlers(c); seed(h)
    val before = CorpusSnapshot.of(ds)
    val run = start(
      a,
      RunRequest(
        CompileArticle,
        SlugTarget,
        title = Some(ZzTitle),
        topics = TinTopics,
        goal = Some(GoalSlug),
        dryRun = Some(RunRequest.DryRun(true))
      )
    )
    assertEquals(run.status, "done", why(run))
    assertEquals(CorpusSnapshot.of(ds), before, "no article, no approval, no goal link, no log line")
    assert(h.research.articles.get(Slug).isLeft)
    val steps = connect(ds)(AgentRepo.steps(ApiIds.job(run.id), AgentRepo.OutputChars(100000)))
    assertEquals(
      steps.filter(_.status.value == "done").map(_.name),
      List("snapshot", "filter", "extract-1", "extract-2", "evidence", "draft", "approve", "write", "review", "close")
    )
    val write = steps.find(_.name.value == "write").get.wouldWrite
    assertEquals(write.map(_.what), List(s"create article [[$Slug]]"))
    assert(
      write.head.payload.value.contains("Tin reached the Aegean by sea") && write.head.payload.value
        .contains("zzagent_tin"),
      write.head.payload
    )
    assertEquals(steps.find(_.name.value == "close").get.wouldWrite.map(_.what), List("log line"))
    assert(
      connect(ds)(AgentRepo.list(None, PageSize(50))).forall(r => !r.parent.contains(run.id)),
      "no review of an article that was not written"
    )
