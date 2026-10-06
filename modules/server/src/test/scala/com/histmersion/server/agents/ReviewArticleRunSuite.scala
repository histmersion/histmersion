package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.core.*
import com.histmersion.core.Agents.*
import com.histmersion.db.{AgentRepo, Db, Migrate}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.*
import sttp.ai.core.agent.testing.{RecordedRequest, ScriptedAgent}
import sttp.client4.testing.SyncBackendStub

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters.*

/** A scripted model for steps that run IN PARALLEL: each call is routed by its system prompt to the
  * script queue of its reviewer, so the order the three panel calls arrive in does not matter. A
  * reviewer with no script left answers as an unreachable provider would. `before` runs before a
  * reviewer's answer is produced (a concurrent writer, in one test). */
final class PanelClient(val model: ModelRef) extends LlmClient:
  val scripts  = ConcurrentHashMap[String, ConcurrentLinkedQueue[Seq[AgentResponse]]]()
  val requests = ConcurrentHashMap[String, ConcurrentLinkedQueue[Seq[RecordedRequest]]]()
  val turns    = ConcurrentHashMap[String, Int]()   // each reviewer's last call's tool-loop limit
  @volatile var before: String => Unit = _ => ()

  def add(who: String, script: Seq[AgentResponse]*): Unit =
    script.foreach(s => scripts.computeIfAbsent(who, _ => ConcurrentLinkedQueue()).add(s))

  def served(who: String): Int = Option(requests.get(who)).fold(0)(_.size)

  /** Which reviewer a call is for, from the shared agent definition its system prompt starts with. */
  def who(system: String): String =
    // the fixer's definition names the other three, so it is recognised first
    if system.contains("You are the **fixer**") then "fixer"
    else if system.contains("You are a **curious general reader**") then "cold"
    else if system.contains("You are the **specialist skeptic**") then "skeptic"
    else if system.contains("You are the **rules reviewer**") then "rules"
    else "unknown"

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val w = who(call.system)
    turns.put(w, call.maxIterations)
    before(w)
    val next = Option(scripts.get(w)).flatMap(q => Option(q.poll()))
      .getOrElse(throw java.net.ConnectException(s"scripted provider has nothing for $w"))
    val script = ScriptedAgent.synchronous(next*)
    val res    = LlmClient.configure(script.builder, call).run(call.user)(SyncBackendStub)
    requests.computeIfAbsent(w, _ => ConcurrentLinkedQueue()).add(script.requests)
    res

/** `review-article` end to end against a `*_test` database, with a scripted panel: the three
  * reviewers in parallel (each with its own tool allowlist — the cold reader none), the merge, the
  * approvals for the skeptic's proposals, the fixer's revision written with `ifUpdatedAt`, the next
  * round's escalation of a re-raised rejection, the tasks item for an open MAJOR, a 412 from a
  * concurrent writer, and a resume that replays the finished parallel steps. Skipped without
  * HISTMERSION_TEST_DB_URL. */
class ReviewArticleRunSuite extends munit.FunSuite:
  private val testDb                = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  // a DB suite driving parallel steps: never let a hang run for hours (#865)
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Slug  = "zzagent_review"
  private val Ck    = "zzagent2019_tin"
  private val Ck2   = "zzagent2024_tin"
  private val Model = ModelRef(ProviderKind.Local, "scripted")
  private val Usage1 = Some(TokenUsage(Tokens(1000), Tokens(100), Tokens.Zero, Tokens.Zero))

  private val Body =
    s"""Tin reached the Aegean by sea [[$Ck]].
       |
       |## Disagreements
       |
       |The date of the wreck is argued [[$Ck]].
       |
       |## Open questions
       |
       |Where the tin was mined is open [[$Ck]].
       |
       |## Not used
       |
       |- none
       |""".stripMargin

  private val Revised = Body.replace("Tin reached the Aegean by sea", "Tin, the scarce half of bronze, reached the Aegean by sea")

  /** An edit as JSON. */
  private def edit(op: String, anchor: String, text: String = null) =
    val t = Option(text).fold("null")(io.circe.Json.fromString(_).noSpaces)
    s"""{"op":"$op","anchor":${io.circe.Json.fromString(anchor).noSpaces},"text":$t}"""
  /** The edit that turns Body into Revised (it answers "tin is not defined"). */
  private val gloss = edit("replace", "Tin reached the Aegean by sea", "Tin, the scarce half of bronze, reached the Aegean by sea")
  private def fixerAnswer(edits: List[String], applied: List[String] = List("R1"), rejected: String = "", reasons: String = "") =
    s"""{"edits":[${edits.mkString(",")}],"applied":[${applied.map(a => s"\"$a\"").mkString(",")}],"rejected":[$rejected],"unresolvedMoved":[],"notes":"","reasons":[$reasons]}"""
  private def userPrompt(r: RecordedRequest): String =
    r.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.getOrElse("")
  /** The points a retry lists as rejected. */
  private def retryPoints(prompt: String): List[String] =
    prompt.split("rejected by validation for these points only:\n").lift(1).toList
      .flatMap(_.split("\nEverything else").head.linesIterator.map(_.stripPrefix("- ")).toList)

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(enabled = true, prices = "scripted=1/2",
        roles = b.agents.roles.copy(reviewer = Model.wire, coldReader = Model.wire, skeptic = Model.wire, fixer = Model.wire)))

  private lazy val ds = Db.dataSource(baseCfg.db)

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def tool(name: String, args: String) =
    AgentResponse("", Seq(ToolCall("call_1", name, args)), StopReason.ToolUse, Usage1, Some("scripted"))

  private def finding(sev: String, where: String, problem: String, quote: String = "", evidence: String = "") =
    s"""{"severity":"$sev","kind":"date","where":"$where","quote":"$quote","problem":"$problem","fix":"fix it","evidence":[$evidence]}"""
  private def rules(verdict: String, findings: String*) =
    s"""{"verdict":"$verdict","findings":[${findings.mkString(",")}],"filter":"topics:[tin] → 2","uncitedRelevant":[],"statusesSilent":[],"accuracyChecked":5}"""
  private val coldPass =
    """{"verdict":"PASS","explainBack":"It asks how tin reached the Aegean. By sea. The date is argued. The mines are open.","emptySlots":[],"findings":[],"termsUndefined":0,"opaqueSentences":0,"lostAt":null}"""
  private def skeptic(verdict: String, findings: List[String], proposals: List[String] = Nil) =
    s"""{"verdict":"$verdict","findings":[${findings.mkString(",")}],"proposals":[${proposals.mkString(",")}],"claimsChecked":6}"""
  private val edgeProposal =
    s"""{"kind":"edge","citekey":"$Ck","status":null,"edgeKind":"refutes","target":"$Ck2","evidence":"the newer study reassigns the tin","quote":"the tin of the Uluburun cargo came from Central Asia"}"""
  private val dateMajor = finding("major", "Disagreements", "the date has no support in the record", "The date of the wreck is argued")

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers) =
    val deps = Deps(cfg, ds)
    val h    = Handlers(deps)
    (AgentsHandlers(deps, h, (_, _) => Right(c)), h)

  private def clean(): Unit =
    Migrate.run(ds)
    connect(ds):
      sql"DELETE FROM jobs WHERE kind = 'agent' AND target_id LIKE 'zzagent%'".update.run()
      sql"DELETE FROM approvals WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM article_versions WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM articles WHERE slug LIKE 'zzagent%'".update.run()
      sql"DELETE FROM sources WHERE citekey LIKE 'zzagent%'".update.run()
      sql"DELETE FROM queue_items WHERE text LIKE '%zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit   = clean()

  private def seed(h: Handlers): Unit =
    assert(h.putSource(Ck, SourcePut(title = "Bronze Age tin", year = Some(2019),
      body = Some("The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE."))).isRight)
    assert(h.putSource(Ck2, SourcePut(title = "Tin isotopes", year = Some(2024),
      body = Some("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia."))).isRight)
    assert(h.putArticle(Slug, ArticlePut("Tin in the Bronze Age", "concept", Body)).isRight)

  private def start(a: AgentsHandlers, req: RunRequest): RunInfo =
    val run = a.startRun(req).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    AgentRepo.get(ds, run.id).get

  test("review only: the panel runs in parallel with its own tools, merged by code; a proposal becomes an approval"):
    val c = PanelClient(Model)
    c.add("rules", Seq(tool("get_source", s"""{"citekey":"$Ck"}"""), text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("MINOR", List(finding("minor", "Open questions", "a source is missing")), List(edgeProposal)))))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug))
    assertEquals(run.status, "done", run.error)
    assertEquals(run.targetKind, "article")

    val steps = AgentRepo.steps(ds, run.id, 4000)
    assertEquals(steps.map(_.name), List("snapshot-1", "mechanical-1", "rules-1", "cold-reader-1", "skeptic-1",
      "merge-1", "proposals-1", "record"))
    val merged = io.circe.parser.decode[ReviewArticle.Merged](steps.find(_.name == "merge-1").get.output.get).toOption.get
    assertEquals(merged.verdict, ReviewArticle.Verdict.Minor)
    assertEquals(merged.findings.map(_.id), List("S1"))

    // the allowlists: the cold reader is offered nothing; the skeptic the citation graph forward
    def offered(w: String) = c.requests.get(w).asScala.flatMap(_.flatMap(_.toolsOffered).map(_.name)).toSet
    assertEquals(offered("cold"), Set.empty[String])
    assertEquals(offered("rules"), Set("get_source", "passages", "neighbors", "search", "query", "facets", "article_coverage"))
    assertEquals(offered("skeptic"), Set("get_source", "passages", "neighbors", "search", "query", "followups"))
    // each step's tool loop is its agent's max-turns, not the global agents.step.max-iterations (6)
    assertEquals(c.turns.asScala.toMap, Map("rules" -> 20, "cold" -> 6, "skeptic" -> 20))
    // the article travels as data, the shared agent definition and the guide as the system prompt
    val first = c.requests.get("rules").peek().head
    assert(first.systemPrompt.exists(p => p.contains("Running inside the Histmersion server") && p.contains("# Writing guide")))
    assert(!first.systemPrompt.exists(_.contains("Tin reached the Aegean")))

    // the edge was verified against the target's record and waits for a person
    val open = AgentRepo.approvals(ds, Some(ApprovalState.Open), 50).filter(_.citekey == Ck)
    assertEquals(open.map(x => (x.kind, x.edgeKind, x.target)), List(("edge", Some("refutes"), Some(Ck2))))
    assertEquals(h.neighbors(Ck, List("refutes")), Nil, "nothing is applied before approval")
    val log = h.ops.queueList("log", all = true).toOption.get.items.filter(_.text.contains(Slug))
    assertEquals(log.map(_.text), List(s"review-article [[$Slug]]: round 1 → MINOR (R:0 C:0 S:1 M:0) (agent run ${run.id.take(8)})"))
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Body.trim, "review only: nothing written")

  test("fix: the fixer's revision is written with ifUpdatedAt; a rejection raised again is escalated to the backlog"):
    val c = PanelClient(Model)
    // round 1: a major from the rules reviewer (applied) and one from the skeptic (rejected, with a quote)
    c.add("rules", Seq(text(rules("MAJOR", finding("major", "Intro", "tin is not defined")))), Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("MAJOR", List(dateMajor)))), Seq(text(skeptic("MAJOR", List(dateMajor)))))
    val fixer = fixerAnswer(List(gloss),
      rejected = s"""{"id":"S1","reason":"the record dates it","citekey":"$Ck","quote":"Its cargo dates to about 1320 BCE."}""")
    c.add("fixer", Seq(text(fixer)))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assertEquals(run.params, RunParams(fix = Some(true), rounds = Some(2)))

    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Revised.trim)
    val versions = h.articleVersions(Slug).toOption.get
    assert(versions.nonEmpty, "the previous text is kept, so the run can be undone")
    val names = AgentRepo.steps(ds, run.id, 10).map(_.name)
    assertEquals(names, List("snapshot-1", "mechanical-1", "rules-1", "cold-reader-1", "skeptic-1", "merge-1",
      "proposals-1", "fixer-1", "patch-1", "snapshot-2", "mechanical-2", "rules-2", "cold-reader-2", "skeptic-2",
      "merge-2", "proposals-2", "record"))
    val tasks = h.ops.queueList("tasks", all = false).toOption.get.items.filter(_.text.contains(Slug))
    assertEquals(tasks.size, 1, tasks)
    assert(tasks.head.text.contains("raised again after the fixer rejected it"), tasks.head.text)
    val log = h.ops.queueList("log", all = true).toOption.get.items.filter(_.text.contains(Slug)).map(_.text)
    assert(log.exists(_.contains("round 2 → MAJOR") && log.exists(_.contains("fixer applied 1 / rejected 1"))), log)
    // the fixer saw the findings; the second round saw the revised text
    assert(c.requests.get("fixer").peek().head.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }
      .exists(p => p.contains("S1 [skeptic, major") && p.contains("<article>")))
    assertEquals(c.served("rules"), 2)
    // the step output records exactly what changed, edit by edit
    val fixStep = AgentRepo.steps(ds, run.id, 40000).find(_.name == "fixer-1").flatMap(_.output)
      .flatMap(io.circe.parser.decode[ReviewArticle.Fix](_).toOption).get
    assertEquals(fixStep.edits.map(e => (e.index, e.op.wire, e.anchor, e.applied)),
      List((1, "replace", "Tin reached the Aegean by sea", true)))
    assertEquals(fixStep.edits.head.text, "Tin, the scarce half of bronze, reached the Aegean by sea")
    // its tools: the read tools and the dry run of its edits; its turns: 12 + one per major (R1, S1)
    def offered(w: String) = c.requests.get(w).asScala.flatMap(_.flatMap(_.toolsOffered).map(_.name)).toSet
    assertEquals(offered("fixer"), Set("get_source", "passages", "neighbors", "search", "query", "check_edits"))
    assertEquals(c.turns.get("fixer"), 14)
    assertEquals(c.turns.get("rules"), 20, "the reviewers keep their own max-turns")

  test("fix: a revision rejected by a check is retried WITH that revision, to be amended rather than redone"):
    val c = PanelClient(Model)
    c.add("rules", Seq(text(rules("MAJOR", finding("major", "Intro", "tin is not defined")))), Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    // the first revision adds a paragraph with no citation (and no list after it): the check rejects it
    val uncited = "Bronze needs tin, and tin is rare in the Aegean."
    val addUncited = edit("insertAfter", s"Where the tin was mined is open [[$Ck]].", "\n\n" + uncited)
    c.add("fixer", Seq(text(fixerAnswer(List(gloss, addUncited)))), Seq(text(fixerAnswer(List(gloss)))))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assertEquals(c.served("fixer"), 2)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Revised.trim)
    val prompts = c.requests.get("fixer").asScala.toList.map(_.head.history.entries.collectFirst {
      case ConversationEntry.UserPrompt(p) => p }.getOrElse(""))
    assert(!prompts.head.contains("<previous-answer>"), "the first attempt has nothing to amend")
    val retry = prompts(1)
    assert(retry.contains("rejected by validation for these points only") && retry.contains("Do not repeat your lookups"), retry.takeRight(800))
    assert(retry.contains("<previous-answer>") && retry.contains(uncited), "the rejected revision travels back")

  test("fix: a retry after a bad anchor carries the previous edits back, and the amended edits are written"):
    val c = PanelClient(Model)
    c.add("rules", Seq(text(rules("MAJOR", finding("major", "Intro", "tin is not defined")))), Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    // the anchor paraphrases the article instead of copying it
    val bad = edit("replace", "Tin came to the Aegean by sea", "Tin, the scarce half of bronze, came to the Aegean by sea")
    c.add("fixer", Seq(text(fixerAnswer(List(bad)))), Seq(text(fixerAnswer(List(gloss)))))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assertEquals(c.served("fixer"), 2)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Revised.trim)
    val retry = userPrompt(c.requests.get("fixer").asScala.toList(1).head)
    assertEquals(retryPoints(retry), List("edit 1 (replace \"Tin came to the Aegean by sea\"): the anchor is not in the article — " +
      "copy it exactly from the current body (earlier edits included)",
      "you list R1 as applied, but no edit changed the article"))
    assert(retry.contains("<previous-answer>") && retry.contains("Tin came to the Aegean by sea"), "the rejected edits travel back")
    assert(!retry.contains("\"body\":") && retry.contains("\"edits\":"), "edits travel back, never a whole body")

  test("fix: the skeptic's evidence is verified against the record and reaches the fixer under its finding"):
    val c = PanelClient(Model)
    val good = s"""{"citekey":"$Ck","offset":75,"quote":"Its cargo dates to about 1320 BCE."}"""
    val made = s"""{"citekey":"$Ck","offset":null,"quote":"the cargo was Cornish tin from the far west"}"""
    val withEvidence = finding("major", "Disagreements", "the date is given in the record", "The date of the wreck is argued",
      evidence = s"$good,$made")
    c.add("rules", Seq(text(rules("PASS"))), Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("MAJOR", List(withEvidence)))), Seq(text(skeptic("PASS", Nil))))
    val dated = edit("replace", "The date of the wreck is argued", "The cargo dates to about 1320 BCE")
    c.add("fixer", Seq(text(fixerAnswer(List(dated), applied = List("S1")))))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    val prompt = userPrompt(c.requests.get("fixer").peek().head)
    assert(prompt.contains(s"""S1 [skeptic, major, date]"""), prompt)
    assert(prompt.contains(s"""\n  evidence: [[$Ck]] @75: "Its cargo dates to about 1320 BCE.""""), prompt)
    assert(!prompt.contains("Cornish"), "a quote that is not in the record never reaches the fixer")
    assert(run.warnings.exists(w => w.contains("S1 evidence dropped") && w.contains("not in the record")), run.warnings)
    val merged = io.circe.parser.decode[ReviewArticle.Merged](AgentRepo.steps(ds, run.id, 40000).find(_.name == "merge-1").get.output.get).toOption.get
    assertEquals(merged.findings.flatMap(_.evidence).map(e => (e.citekey, e.offset)), List((Ck, Some(75))))
    assert(h.getArticle(Slug).toOption.get.article.body.contains("The cargo dates to about 1320 BCE"))

  test("fix: check_edits, called inside the step, reports exactly the problems the validation then raises"):
    val c = PanelClient(Model)
    c.add("rules", Seq(text(rules("MAJOR", finding("major", "Intro", "tin is not defined")))), Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    val draft = List(gloss, edit("insertAfter", s"Where the tin was mined is open [[$Ck]].", "\n\nBronze needs tin."),
      edit("delete", "No such sentence."), edit("insertAfter", "by sea", " [[zzagent_ghost]]"))
    val args = s"""{"slug":"$Slug","edits":[${draft.mkString(",")}]}"""
    // attempt 1: dry-run the edits, then answer with them anyway; attempt 2: the amended answer
    c.add("fixer", Seq(tool("check_edits", args), text(fixerAnswer(draft))), Seq(text(fixerAnswer(List(gloss)))))
    val (a, h) = handlers(c); seed(h)
    val direct = h.checkArticle(Slug, io.circe.parser.decode[ArticleEdits.ArticleCheckRequest](args).toOption.get).toOption.get
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Revised.trim)
    val first = c.requests.get("fixer").asScala.toList.head
    val toolOut = first.flatMap(_.history.entries.collect { case ConversationEntry.ToolResult(_, "check_edits", r) => r }).head
    val reported = toolOut.split("### Result").last.linesIterator.filter(_.startsWith("- ")).map(_.stripPrefix("- ")).toList
    val raised = retryPoints(userPrompt(c.requests.get("fixer").asScala.toList(1).head))
    assertEquals(reported, direct.problems)
    assertEquals(raised, direct.problems, "the tool and the validation are one function")
    assert(direct.problems.exists(_.contains("no [[citekey]]")) && direct.problems.exists(_.startsWith("edit 3 (delete")) &&
      direct.problems.exists(p => p.startsWith("edit 4 (insertAfter") && p.contains("zzagent_ghost")), direct.problems)
    assert(toolOut.contains("- edit 1 (replace \"Tin reached the Aegean by sea\"): applies"), toolOut)

  test("check_edits: a whole draft body, an unknown article, and neither edits nor body"):
    val c = PanelClient(Model)
    val (_, h) = handlers(c); seed(h)
    val draft = h.checkArticle("zzagent_new", ArticleEdits.ArticleCheckRequest(body = Some(s"New [[zzagent_ghost]].\n\nUncited.")))
      .toOption.get
    assert(draft.fatal && draft.problems.exists(_.contains("zzagent_ghost")) && draft.problems.exists(_.contains("no [[citekey]]")), draft.problems)
    assertEquals(draft.kind, "concept")
    assert(h.checkArticle("zzagent_new", ArticleEdits.ArticleCheckRequest(edits = List(ArticleEdits.EditAnswer(ArticleEdits.Op.Delete, "x")))).isLeft)
    assert(h.checkArticle(Slug, ArticleEdits.ArticleCheckRequest()).isLeft)
    val clean = h.checkArticle(Slug, ArticleEdits.ArticleCheckRequest(List(ArticleEdits.EditAnswer(ArticleEdits.Op.Replace,
      "Tin reached", Some("Tin, a metal, reached"))))).toOption.get
    assertEquals((clean.problems, clean.fatal, clean.edits.map(_.applied)), (Nil, false, List(true)))
    assert(ArticleEdits.markdown(clean).contains("No problems"), ArticleEdits.markdown(clean))
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Body.trim, "a check writes nothing")

  test("fix (#958): coverage drift is a mechanical finding; the fixer answers it with a reason and deletes the hand-written list"):
    val c = PanelClient(Model)
    c.add("rules", Seq(text(rules("PASS"))), Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    // M1: the hand-written Not used section; M2: the filter's second source is neither cited nor given a reason
    val reason = s"""{"citekey":"$Ck2","reason":"Its isotopes speak to the cargo's origin, not its route."}"""
    c.add("fixer", Seq(text(fixerAnswer(List(edit("delete", "## Not used\n\n- none")), applied = List("M1", "M2"), reasons = reason))))
    val (a, h) = handlers(c); seed(h)
    // the two sources under a topic of their own, the article's filter
    List(Ck, Ck2).foreach(ck => assert(h.patchSource(ck, SourcePatch(topics = Some(List("zzagent_review_tin")))).isRight))
    assert(h.patchArticle(Slug, ArticlePatch(filter = Some(ArticleFilter(topics = List("zzagent_review_tin"))))).isRight)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)

    val steps = AgentRepo.steps(ds, run.id, 40000)
    val mech1 = io.circe.parser.decode[List[ReviewArticle.Finding]](steps.find(_.name == "mechanical-1").get.output.get).toOption.get
    assertEquals(mech1.map(f => (f.id, f.kind, f.major)), List(("M1", "coverage", true), ("M2", "coverage", true)))
    assertEquals(mech1(1).problem, s"1 source the filter returns is neither cited nor given a reason: [[$Ck2]]")
    // the panel and the fixer read the generated sections after the body
    assert(userPrompt(c.requests.get("rules").peek().head).contains("<generated-sections>"))
    val fixerPrompt = userPrompt(c.requests.get("fixer").peek().head)
    assert(fixerPrompt.contains("<generated-sections>") && fixerPrompt.contains(s"[[$Ck2]] (unassessed) — not yet assessed"), fixerPrompt)
    // written: the body without the hand-written list, and the reason
    val after = h.getArticle(Slug).toOption.get
    assert(!after.article.body.contains("## Not used"), after.article.body)
    assertEquals(after.coverage.map(_.drift), Some(Nil))
    assertEquals(after.coverage.toList.flatMap(_.notUsed).map(e => (e.source.citekey, e.reason)),
      List((Ck2, Some("Its isotopes speak to the cargo's origin, not its route."))))
    val fixStep = steps.find(_.name == "fixer-1").flatMap(_.output).flatMap(io.circe.parser.decode[ReviewArticle.Fix](_).toOption).get
    assertEquals(fixStep.reasons.map(_.citekey), List(Ck2))
    assertEquals(io.circe.parser.decode[List[ReviewArticle.Finding]](steps.find(_.name == "mechanical-2").get.output.get).toOption.get, Nil)
    assert(!run.warnings.exists(_.contains("records no filter")), run.warnings)

  test("review (#958): an article with no filter keeps the old rule (a hand-written Not used is required) and the run says so"):
    val c = PanelClient(Model)
    c.add("rules", Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug))
    assertEquals(run.status, "done", run.error)
    assert(run.warnings.exists(_.contains("records no filter")), run.warnings)
    assert(!userPrompt(c.requests.get("rules").peek().head).contains("<generated-sections>"))

  test("fix: a concurrent writer wins — the fix is dropped on 412 and the next round reviews their text"):
    val c = PanelClient(Model)
    c.add("rules", Seq(text(rules("MAJOR", finding("major", "Intro", "tin is not defined")))), Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    c.add("fixer", Seq(text(fixerAnswer(List(gloss)))))
    val (a, h) = handlers(c); seed(h)
    val theirs = Body.replace("by sea", "by sea and by land")
    c.before = w => if w == "fixer" then assert(h.patchArticle(Slug, ArticlePatch(body = Some(theirs))).isRight)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, theirs.trim, "a stale fix never overwrites")
    assert(run.warnings.exists(_.contains("changed while the fixer worked")), run.warnings)
    val patched = AgentRepo.steps(ds, run.id, 4000).find(_.name == "patch-1").flatMap(_.output)
      .flatMap(io.circe.parser.decode[ReviewArticle.Patched](_).toOption)
    assertEquals(patched, Some(ReviewArticle.Patched(written = false, None, conflict = true)))
    // round 2 reviewed the other writer's text
    val round2 = c.requests.get("rules").asScala.toList(1).head.history.entries
      .collectFirst { case ConversationEntry.UserPrompt(p) => p }.get
    assert(round2.contains("by sea and by land"), round2)

  test("resume after a provider failure in the panel replays the finished parallel steps"):
    val first = PanelClient(Model)
    first.add("rules", Seq(text(rules("PASS"))))
    first.add("cold", Seq(text(coldPass)))
    // the skeptic's provider is down
    val (a, h) = handlers(first); seed(h)
    val paused = start(a, RunRequest("review-article", Slug))
    assertEquals(paused.status, "paused", paused.error)
    assert(paused.error.exists(_.startsWith("provider:")), paused.error)
    val second = PanelClient(Model)
    second.add("skeptic", Seq(text(skeptic("PASS", Nil))))
    val (a2, _) = handlers(second)
    assert(a2.control(paused.id, "resume", None, None).isRight)
    a2.engine.execute(paused.id)
    val done = AgentRepo.get(ds, paused.id).get
    assertEquals(done.status, "done", done.error)
    assertEquals((second.served("rules"), second.served("cold"), second.served("skeptic")), (0, 0, 1))

  test("refused up front: an unknown article, a role with no model, too many rounds"):
    val c = PanelClient(Model)
    val (a, h) = handlers(c); seed(h)
    assert(a.startRun(RunRequest("review-article", "zzagent_nope")).isLeft)
    assert(a.startRun(RunRequest("review-article", Slug, rounds = Some(99))).left.exists(_.message.contains("rounds")))
    val noSkeptic = baseCfg.copy(agents = baseCfg.agents.copy(roles = baseCfg.agents.roles.copy(skeptic = "")))
    val (b, _) = handlers(c, noSkeptic)
    assert(b.startRun(RunRequest("review-article", Slug)).left.exists(_.message.contains("skeptic")))
