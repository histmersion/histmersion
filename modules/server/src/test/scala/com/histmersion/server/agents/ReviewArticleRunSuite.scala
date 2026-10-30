package com.histmersion.server.agents

import com.augustnagro.magnum.*
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.*
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.{AgentRepo, Db, Migrate}
import com.histmersion.server.{Deps, Handlers}
import sttp.ai.core.agent.*
import sttp.ai.core.agent.testing.{RecordedRequest, ScriptedAgent}
import sttp.client4.testing.SyncBackendStub

import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue}
import scala.jdk.CollectionConverters.*
import com.histmersion.values.EdgeKind

/** A scripted model for steps that run IN PARALLEL: each call is routed by its system prompt to the script queue of its
  * reviewer, so the order the three panel calls arrive in does not matter. A reviewer with no script left answers as an
  * unreachable provider would. `before` runs before a reviewer's answer is produced (a concurrent writer, in one test).
  */
final class PanelClient(val model: ModelRef) extends LlmClient:
  val scripts = ConcurrentHashMap[String, ConcurrentLinkedQueue[Seq[AgentResponse]]]()
  val requests = ConcurrentHashMap[String, ConcurrentLinkedQueue[Seq[RecordedRequest]]]()
  val turns = ConcurrentHashMap[String, Int]() // each reviewer's last call's tool-loop limit
  val allTurns = ConcurrentHashMap[String, ConcurrentLinkedQueue[Int]]() // every call's, in order
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

  /** The configured model of each reviewer's calls, in order, when the engine reached them through [[as]] (a client per
    * model, as the server's factory builds them).
    */
  val modelsUsed = ConcurrentHashMap[String, ConcurrentLinkedQueue[String]]()

  /** This panel as the client of `ref`: the same scripts, the call recorded under `ref`. */
  def as(ref: ModelRef): LlmClient = new LlmClient:
    val model = ref
    def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
      modelsUsed.computeIfAbsent(who(call.system), _ => ConcurrentLinkedQueue()).add(ref.wire)
      PanelClient.this.run(call)

  def run[Out](call: LlmCall[Out]): AgentResult[Either[AgentFailure, Out]] =
    val w = who(call.system)
    turns.put(w, call.maxIterations)
    allTurns.computeIfAbsent(w, _ => ConcurrentLinkedQueue()).add(call.maxIterations)
    before(w)
    val next = Option(scripts.get(w))
      .flatMap(q => Option(q.poll()))
      .getOrElse(throw java.net.ConnectException(s"scripted provider has nothing for $w"))
    val script = ScriptedAgent.synchronous(next*)
    val res = LlmClient.configure(script.builder, call).run(call.user)(SyncBackendStub)
    requests.computeIfAbsent(w, _ => ConcurrentLinkedQueue()).add(script.requests)
    res

/** `review-article` end to end against a `*_test` database, with a scripted panel: the three reviewers in parallel
  * (each with its own tool allowlist — the cold reader none), the merge, the approvals for the skeptic's proposals, the
  * fixer's revision written with `ifUpdatedAt`, the next round's escalation of a re-raised rejection, the tasks item
  * for an open MAJOR, a 412 from a concurrent writer, and a resume that replays the finished parallel steps. Skipped
  * without HISTMERSION_TEST_DB_URL.
  */
class ReviewArticleRunSuite extends com.histmersion.db.DbSuite:
  private val testDb = com.histmersion.db.TestDb.url
  override def munitIgnore: Boolean = testDb.isEmpty
  // a DB suite driving parallel steps: never let a hang run for hours (#865)
  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val Slug = "zzagent_review"
  private val Ck = "zzagent2019_tin"
  private val Ck2 = "zzagent2024_tin"
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

  private val Revised =
    Body.replace("Tin reached the Aegean by sea", "Tin, the scarce half of bronze, reached the Aegean by sea")

  /** An edit as JSON. */
  private def edit(op: String, anchor: String, text: String = null) =
    val t = Option(text).fold("null")(io.circe.Json.fromString(_).noSpaces)
    s"""{"op":"$op","anchor":${io.circe.Json.fromString(anchor).noSpaces},"text":$t}"""

  /** The edit that turns Body into Revised (it answers "tin is not defined"). */
  private val gloss =
    edit("replace", "Tin reached the Aegean by sea", "Tin, the scarce half of bronze, reached the Aegean by sea")
  private def fixerAnswer(
      edits: List[String],
      applied: List[String] = List("R1"),
      rejected: String = "",
      reasons: String = ""
  ) =
    s"""{"edits":[${edits.mkString(",")}],"applied":[${applied
        .map(a => s"\"$a\"")
        .mkString(",")}],"rejected":[$rejected],"unresolvedMoved":[],"notes":"","reasons":[$reasons]}"""
  private def userPrompt(r: RecordedRequest): String =
    r.history.entries.collectFirst { case ConversationEntry.UserPrompt(p) => p }.getOrElse("")

  /** The points a retry lists as rejected. */
  private def retryPoints(prompt: String): List[String] =
    prompt
      .split("rejected by validation for these points only:\n")
      .lift(1)
      .toList
      .flatMap(_.split("\nEverything else").head.linesIterator.map(_.stripPrefix("- ")).toList)

  private lazy val baseCfg =
    val b = AppConfig.load()
    b.copy(
      db = b.db.copy(url = testDb.get),
      agents = b.agents.copy(
        enabled = true,
        prices = "scripted=1/2",
        roles =
          b.agents.roles.copy(reviewer = Model.wire, coldReader = Model.wire, skeptic = Model.wire, fixer = Model.wire)
      )
    )

  private lazy val ds = testPool(baseCfg.db)

  private def text(json: String) = AgentResponse(json, Nil, StopReason.EndTurn, Usage1, Some("scripted"))
  private def tool(name: String, args: String) =
    AgentResponse("", Seq(ToolCall("call_1", name, args)), StopReason.ToolUse, Usage1, Some("scripted"))

  private def finding(sev: String, where: String, problem: String, quote: String = "", evidence: String = "") =
    s"""{"severity":"$sev","kind":"date","where":"$where","quote":"$quote","problem":"$problem","fix":"fix it","evidence":[$evidence]}"""
  private def rules(verdict: String, findings: String*) = rulesChecking(verdict, Nil, findings*)
  private def rulesChecking(verdict: String, checks: List[String], findings: String*) =
    rulesSpotting(verdict, checks, List(spotTin), findings*)
  private def rulesSpotting(verdict: String, checks: List[String], spots: List[String], findings: String*) =
    s"""{"verdict":"$verdict","findings":[${findings.mkString(
        ","
      )}],"filter":"topics:[tin] → 2","uncitedRelevant":[],"statusesSilent":[],"spotChecks":[${spots.mkString(
        ","
      )}],"checks":[${checks.mkString(",")}]}"""

  /** The spot-check every full-pass rules answer carries (#1840): a claim of the article checked against the record of
    * [[Ck]] — which the step must have read ([[looked]]).
    */
  private val spotTin =
    """{"citekey":"zzagent2019_tin","claim":"reached the Aegean by sea","verdict":"attributable","recordQuote":"The Uluburun ship carried ten tons of copper and one ton of tin."}"""

  /** A full-pass rules answer after the lookup its spot-check rests on. */
  private def looked(json: String): Seq[AgentResponse] = Seq(tool("get_source", s"""{"citekey":"$Ck"}"""), text(json))
  private val coldPass =
    """{"verdict":"PASS","explainBack":"It asks how tin reached the Aegean. By sea. The date is argued. The mines are open.","emptySlots":[],"findings":[],"termsUndefined":0,"opaqueSentences":0,"lostAt":null}"""
  private def skeptic(
      verdict: String,
      findings: List[String],
      proposals: List[String] = Nil,
      checks: List[String] = Nil,
      notMaterial: List[(String, String)] = Nil
  ) =
    val nm = notMaterial.map((p, r) => s"""{"pair":"$p","reason":${io.circe.Json.fromString(r).noSpaces}}""")
    s"""{"verdict":"$verdict","findings":[${findings.mkString(",")}],"proposals":[${proposals.mkString(
        ","
      )}],"claimsChecked":6,"checks":[${checks.mkString(",")}],"notMaterial":[${nm.mkString(",")}]}"""

  /** A missing-critique finding naming a checklist pair (or none). */
  private def critique(
      sev: String,
      pair: Option[String],
      where: String = "Intro",
      quote: String = "Tin reached the Aegean by sea"
  ) =
    s"""{"severity":"$sev","kind":"missing-critique","where":"$where","quote":"$quote","problem":"the article omits a critique of its source","fix":"cite the critic","evidence":[],"pair":${pair
        .fold("null")(p => s"\"$p\"")}}"""
  private val edgeProposal =
    s"""{"kind":"edge","citekey":"$Ck","status":null,"edgeKind":"refutes","target":"$Ck2","evidence":"the newer study reassigns the tin","quote":"the tin of the Uluburun cargo came from Central Asia"}"""
  private val dateMajor =
    finding("major", "Disagreements", "the date has no support in the record", "The date of the wreck is argued")

  /** A verification pass's answer: checks (id, fixed, quote of the current text), new findings, lessons. */
  private def check(id: String, fixed: Boolean, quote: String, note: String = "checked") =
    s"""{"id":"$id","fixed":$fixed,"quote":${io.circe.Json.fromString(quote).noSpaces},"note":"$note"}"""
  private def recheck(checks: List[String], findings: List[String] = Nil, lessons: List[String] = Nil) =
    s"""{"checks":[${checks.mkString(",")}],"findings":[${findings
        .mkString(",")}],"lessons":[${lessons.map(l => io.circe.Json.fromString(l).noSpaces).mkString(",")}]}"""

  /** The rules reviewer's check that the gloss fixed R1. */
  private val glossFixed = recheck(
    List(check("R1", fixed = true, "Tin, the scarce half of bronze, reached the Aegean by sea"))
  )
  private def ledgerOf(h: Handlers): Option[ReviewLedger.Ledger] =
    com.histmersion.db.ReviewLedgerRepo.get(ds, Slug).toOption.flatten

  private def handlers(c: LlmClient, cfg: AppConfig = baseCfg): (AgentsHandlers, Handlers) =
    val deps = Deps(cfg, ds)
    val h = Handlers(deps)
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
      sql"DELETE FROM review_ledgers WHERE slug LIKE 'zzagent%'".update.run()

  override def beforeEach(context: BeforeEach): Unit = clean()
  override def afterEach(context: AfterEach): Unit = clean()

  private def seed(h: Handlers): Unit =
    assert(
      h.putSource(
        Ck,
        SourcePut(
          title = "Bronze Age tin",
          authors = List("Ann Tinley"),
          year = Some(2019),
          body =
            Some("The Uluburun ship carried ten tons of copper and one ton of tin. Its cargo dates to about 1320 BCE.")
        )
      ).isRight
    )
    assert(
      h.putSource(
        Ck2,
        SourcePut(
          title = "Tin isotopes",
          year = Some(2024),
          body = Some("We show that the tin of the Uluburun cargo came from Central Asia, not Anatolia.")
        )
      ).isRight
    )
    assert(h.putArticle(Slug, ArticlePut("Tin in the Bronze Age", "concept", Body)).isRight)

  private def start(a: AgentsHandlers, req: RunRequest): RunInfo =
    val run = a.startRun(req).fold(e => fail(e.message), identity)
    a.engine.execute(run.id)
    AgentRepo.get(ds, run.id).get

  test("review only: the panel runs in parallel with its own tools, merged by code; a proposal becomes an approval"):
    val c = PanelClient(Model)
    c.add("rules", Seq(tool("get_source", s"""{"citekey":"$Ck"}"""), text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(text(skeptic("MINOR", List(finding("minor", "Open questions", "a source is missing")), List(edgeProposal))))
    )
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug))
    assertEquals(run.status, "done", run.error)
    assertEquals(run.targetKind, "article")

    val steps = AgentRepo.steps(ds, run.id, 4000)
    assertEquals(
      steps.map(_.name),
      List(
        "ledger",
        "snapshot-1",
        "mechanical-1",
        "rules-1",
        "cold-reader-1",
        "skeptic-1",
        "ledger-1",
        "merge-1",
        "proposals-1",
        "record"
      )
    )
    val merged =
      io.circe.parser.decode[ReviewArticle.Merged](steps.find(_.name == "merge-1").get.output.get).toOption.get
    assertEquals(merged.verdict, ReviewArticle.Verdict.Minor)
    assertEquals(merged.findings.map(_.id), List("S1"))

    // the allowlists: the cold reader is offered nothing; the skeptic the citation graph forward
    def offered(w: String) = c.requests.get(w).asScala.flatMap(_.flatMap(_.toolsOffered).map(_.name)).toSet
    assertEquals(offered("cold"), Set.empty[String])
    assertEquals(offered("rules"), Set("get_source", "passages", "neighbors", "search", "query", "article_coverage"))
    assertEquals(offered("skeptic"), Set("get_source", "passages", "neighbors", "search", "query", "followups"))
    // each step's tool loop is its agent's max-turns, not the global agents.step.max-iterations (6)
    assertEquals(c.turns.asScala.toMap, Map("rules" -> 20, "cold" -> 6, "skeptic" -> 20))
    // the article travels as data, the shared agent definition and the guide as the system prompt
    val first = c.requests.get("rules").peek().head
    assert(
      first.systemPrompt.exists(p =>
        p.contains("Running inside the Histmersion server") && p.contains("# Writing guide")
      )
    )
    assert(!first.systemPrompt.exists(_.contains("Tin reached the Aegean")))

    // the edge was verified against the target's record and waits for a person
    val open = AgentRepo.approvals(ds, Some(ApprovalState.Open), 50).filter(_.citekey == Ck)
    assertEquals(open.map(x => (x.kind, x.edgeKind, x.target)), List(("edge", Some("refutes"), Some(Ck2))))
    assertEquals(h.neighbors(Ck, List("refutes")), Nil, "nothing is applied before approval")
    val log = h.ops.queueList("log", all = true).toOption.get.items.filter(_.text.contains(Slug))
    assertEquals(
      log.map(_.text),
      List(
        s"review-article [[$Slug]]: round 1 → MINOR (R:0 C:0 S:1 M:0) (agent run ${run.id.take(8)}); " +
          "ledger 0 fixed, 1 open"
      )
    )
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Body.trim, "review only: nothing written")

  test("fix: the fixer's revision is written with ifUpdatedAt; a rejection raised again is escalated to the backlog"):
    val c = PanelClient(Model)
    // pass 1: a major from the rules reviewer (applied) and one from the skeptic (rejected, with a quote);
    // pass 2 verifies R1 (the skeptic owns nothing claimed: not called); pass 3 confirms, and the skeptic
    // raises the rejected finding again
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined"))),
      Seq(text(glossFixed)),
      looked(rules("PASS"))
    )
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("MAJOR", List(dateMajor)))), Seq(text(skeptic("MAJOR", List(dateMajor)))))
    val fixer = fixerAnswer(
      List(gloss),
      rejected =
        s"""{"id":"S1","reason":"the record dates it","citekey":"$Ck","quote":"Its cargo dates to about 1320 BCE."}"""
    )
    c.add("fixer", Seq(text(fixer)))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assertEquals(run.params, RunParams(fix = Some(true), rounds = Some(2)))

    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Revised.trim)
    val versions = h.articleVersions(Slug).toOption.get
    assert(versions.nonEmpty, "the previous text is kept, so the run can be undone")
    val names = AgentRepo.steps(ds, run.id, 10).map(_.name)
    assertEquals(
      names,
      List(
        "ledger",
        "snapshot-1",
        "mechanical-1",
        "rules-1",
        "cold-reader-1",
        "skeptic-1",
        "ledger-1",
        "merge-1",
        "proposals-1",
        "fixer-1",
        "patch-1",
        "snapshot-2",
        "mechanical-2",
        "rules-check-2",
        "ledger-2",
        "merge-2",
        "snapshot-3",
        "mechanical-3",
        "rules-3",
        "cold-reader-3",
        "skeptic-3",
        "ledger-3",
        "merge-3",
        "proposals-3",
        "record"
      )
    )
    val tasks = h.ops.queueList("tasks", all = false).toOption.get.items.filter(_.text.contains(Slug))
    assertEquals(tasks.size, 1, tasks)
    assert(
      tasks.head.text
        .contains("S1 (Disagreements): the date has no support in the record [raised again after a rejection]"),
      tasks.head.text
    )
    val log = h.ops.queueList("log", all = true).toOption.get.items.filter(_.text.contains(Slug)).map(_.text)
    assert(
      log.exists(l =>
        l.contains("round 3 → MAJOR") && l.contains("fixer applied 1 / rejected 1") &&
          l.contains("confirmed by a full panel")
      ),
      log
    )
    val ledger = ledgerOf(h).get
    assertEquals(
      ledger.entries.map(e => (e.id, e.status, e.finding.escalated)),
      List(("R1", ReviewLedger.Status.Fixed, false), ("S1", ReviewLedger.Status.Open, true))
    )
    assertEquals(ledger.task, Some(tasks.head.id))
    assert(ledger.lessons.exists(_.text.startsWith(s"[[$Ck]]: the record dates it")), ledger.lessons)
    // the fixer saw the findings; the second round saw the revised text
    assert(
      c.requests
        .get("fixer")
        .peek()
        .head
        .history
        .entries
        .collectFirst { case ConversationEntry.UserPrompt(p) => p }
        .exists(p => p.contains("S1 [skeptic, major") && p.contains("<article>"))
    )
    assertEquals((c.served("rules"), c.served("cold"), c.served("skeptic")), (3, 2, 2))
    // the step output records exactly what changed, edit by edit
    val fixStep = AgentRepo
      .steps(ds, run.id, 40000)
      .find(_.name == "fixer-1")
      .flatMap(_.output)
      .flatMap(io.circe.parser.decode[ReviewArticle.Fix](_).toOption)
      .get
    assertEquals(
      fixStep.edits.map(e => (e.index, e.op.wire, e.anchor, e.applied)),
      List((1, "replace", "Tin reached the Aegean by sea", true))
    )
    assertEquals(fixStep.edits.head.text, "Tin, the scarce half of bronze, reached the Aegean by sea")
    // its tools: the read tools and the dry run of its edits; its turns: 12 + one per major (R1, S1)
    def offered(w: String) = c.requests.get(w).asScala.flatMap(_.flatMap(_.toolsOffered).map(_.name)).toSet
    assertEquals(offered("fixer"), Set("get_source", "passages", "neighbors", "search", "query", "check_edits"))
    assertEquals(c.turns.get("fixer"), 14)
    assertEquals(c.turns.get("rules"), 20, "the reviewers keep their own max-turns")

  test("fix: a revision rejected by a check is retried WITH that revision, to be amended rather than redone"):
    val c = PanelClient(Model)
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined"))),
      Seq(text(glossFixed)),
      looked(rules("PASS"))
    )
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
    val prompts = c.requests
      .get("fixer")
      .asScala
      .toList
      .map(
        _.head.history.entries
          .collectFirst { case ConversationEntry.UserPrompt(p) =>
            p
          }
          .getOrElse("")
      )
    assert(!prompts.head.contains("<previous-answer>"), "the first attempt has nothing to amend")
    val retry = prompts(1)
    assert(
      retry.contains("rejected by validation for these points only") && retry.contains("Do not repeat your lookups"),
      retry.takeRight(800)
    )
    assert(retry.contains("<previous-answer>") && retry.contains(uncited), "the rejected revision travels back")

  test("fix: a retry after a bad anchor carries the previous edits back, and the amended edits are written"):
    val c = PanelClient(Model)
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined"))),
      Seq(text(glossFixed)),
      looked(rules("PASS"))
    )
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    // the anchor paraphrases the article instead of copying it
    val bad =
      edit("replace", "Tin came to the Aegean by sea", "Tin, the scarce half of bronze, came to the Aegean by sea")
    c.add("fixer", Seq(text(fixerAnswer(List(bad)))), Seq(text(fixerAnswer(List(gloss)))))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assertEquals(c.served("fixer"), 2)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Revised.trim)
    val retry = userPrompt(c.requests.get("fixer").asScala.toList(1).head)
    assertEquals(
      retryPoints(retry),
      List(
        "edit 1 (replace \"Tin came to the Aegean by sea\"): the anchor is not in the article — " +
          "copy it exactly from the current body (earlier edits included)",
        "you list R1 as applied, but no edit changed the article"
      )
    )
    assert(
      retry.contains("<previous-answer>") && retry.contains("Tin came to the Aegean by sea"),
      "the rejected edits travel back"
    )
    assert(!retry.contains("\"body\":") && retry.contains("\"edits\":"), "edits travel back, never a whole body")

  test("fix: the skeptic's evidence is verified against the record and reaches the fixer under its finding"):
    val c = PanelClient(Model)
    val good = s"""{"citekey":"$Ck","offset":75,"quote":"Its cargo dates to about 1320 BCE."}"""
    val made = s"""{"citekey":"$Ck","offset":null,"quote":"the cargo was Cornish tin from the far west"}"""
    val withEvidence = finding(
      "major",
      "Disagreements",
      "the date is given in the record",
      "The date of the wreck is argued",
      evidence = s"$good,$made"
    )
    c.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(text(skeptic("MAJOR", List(withEvidence)))),
      Seq(text(recheck(List(check("S1", fixed = true, "The cargo dates to about 1320 BCE"))))),
      Seq(text(skeptic("PASS", Nil)))
    )
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
    val merged = io.circe.parser
      .decode[ReviewArticle.Merged](AgentRepo.steps(ds, run.id, 40000).find(_.name == "merge-1").get.output.get)
      .toOption
      .get
    assertEquals(merged.findings.flatMap(_.evidence).map(e => (e.citekey, e.offset)), List((Ck, Some(75))))
    assert(h.getArticle(Slug).toOption.get.article.body.contains("The cargo dates to about 1320 BCE"))

  test("fix: check_edits, called inside the step, reports exactly the problems the validation then raises"):
    val c = PanelClient(Model)
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined"))),
      Seq(text(glossFixed)),
      looked(rules("PASS"))
    )
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    val draft = List(
      gloss,
      edit("insertAfter", s"Where the tin was mined is open [[$Ck]].", "\n\nBronze needs tin."),
      edit("delete", "No such sentence."),
      edit("insertAfter", "by sea", " [[zzagent_ghost]]")
    )
    val args = s"""{"slug":"$Slug","edits":[${draft.mkString(",")}]}"""
    // attempt 1: dry-run the edits, then answer with them anyway; attempt 2: the amended answer
    c.add("fixer", Seq(tool("check_edits", args), text(fixerAnswer(draft))), Seq(text(fixerAnswer(List(gloss)))))
    val (a, h) = handlers(c); seed(h)
    val direct =
      h.checkArticle(Slug, io.circe.parser.decode[ArticleEdits.ArticleCheckRequest](args).toOption.get).toOption.get
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Revised.trim)
    val first = c.requests.get("fixer").asScala.toList.head
    val toolOut =
      first.flatMap(_.history.entries.collect { case ConversationEntry.ToolResult(_, "check_edits", r) => r }).head
    val reported =
      toolOut.split("### Result").last.linesIterator.filter(_.startsWith("- ")).map(_.stripPrefix("- ")).toList
    val raised = retryPoints(userPrompt(c.requests.get("fixer").asScala.toList(1).head))
    assertEquals(reported, direct.problems)
    assertEquals(raised, direct.problems, "the tool and the validation are one function")
    assert(
      direct.problems.exists(_.contains("no [[citekey]]")) && direct.problems.exists(_.startsWith("edit 3 (delete")) &&
        direct.problems.exists(p => p.startsWith("edit 4 (insertAfter") && p.contains("zzagent_ghost")),
      direct.problems
    )
    assert(toolOut.contains("- edit 1 (replace \"Tin reached the Aegean by sea\"): applies"), toolOut)

  test("check_edits: a whole draft body, an unknown article, and neither edits nor body"):
    val c = PanelClient(Model)
    val (_, h) = handlers(c); seed(h)
    val draft = h
      .checkArticle("zzagent_new", ArticleEdits.ArticleCheckRequest(body = Some(s"New [[zzagent_ghost]].\n\nUncited.")))
      .toOption
      .get
    assert(
      draft.fatal && draft.problems.exists(_.contains("zzagent_ghost")) && draft.problems.exists(
        _.contains("no [[citekey]]")
      ),
      draft.problems
    )
    assertEquals(draft.kind, "concept")
    assert(
      h.checkArticle(
        "zzagent_new",
        ArticleEdits.ArticleCheckRequest(edits = List(ArticleEdits.EditAnswer(ArticleEdits.Op.Delete, "x")))
      ).isLeft
    )
    assert(h.checkArticle(Slug, ArticleEdits.ArticleCheckRequest()).isLeft)
    val clean = h
      .checkArticle(
        Slug,
        ArticleEdits.ArticleCheckRequest(
          List(ArticleEdits.EditAnswer(ArticleEdits.Op.Replace, "Tin reached", Some("Tin, a metal, reached")))
        )
      )
      .toOption
      .get
    assertEquals((clean.problems, clean.fatal, clean.edits.map(_.applied)), (Nil, false, List(true)))
    assert(ArticleEdits.markdown(clean).contains("No problems"), ArticleEdits.markdown(clean))
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Body.trim, "a check writes nothing")

  test(
    "fix (#958): coverage drift is a mechanical finding; the fixer answers it with a reason and deletes the hand-written list"
  ):
    val c = PanelClient(Model)
    c.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    // M1: the hand-written Not used section; M2: the filter's second source is neither cited nor given a reason
    val reason = s"""{"citekey":"$Ck2","reason":"Its isotopes speak to the cargo's origin, not its route."}"""
    c.add(
      "fixer",
      Seq(
        text(fixerAnswer(List(edit("delete", "## Not used\n\n- none")), applied = List("M1", "M2"), reasons = reason))
      )
    )
    val (a, h) = handlers(c); seed(h)
    // the two sources under a topic of their own, the article's filter
    List(Ck, Ck2).foreach(ck =>
      assert(h.patchSource(ck, SourcePatch(topics = Some(List("zzagent_review_tin")))).isRight)
    )
    assert(
      h.patchArticle(Slug, ArticlePatch(filter = Some(ArticleFilter(topics = List("zzagent_review_tin"))))).isRight
    )
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)

    val steps = AgentRepo.steps(ds, run.id, 40000)
    val mech1 = io.circe.parser
      .decode[List[ReviewArticle.Finding]](steps.find(_.name == "mechanical-1").get.output.get)
      .toOption
      .get
    assertEquals(mech1.map(f => (f.id, f.kind, f.major)), List(("M1", "coverage", true), ("M2", "coverage", true)))
    assertEquals(mech1(1).problem, s"1 source the filter returns is neither cited nor given a reason: [[$Ck2]]")
    // the panel and the fixer read the generated sections after the body
    assert(userPrompt(c.requests.get("rules").peek().head).contains("<generated-sections>"))
    val fixerPrompt = userPrompt(c.requests.get("fixer").peek().head)
    assert(
      fixerPrompt.contains("<generated-sections>") && fixerPrompt.contains(s"[[$Ck2]] (unassessed) — not yet assessed"),
      fixerPrompt
    )
    // written: the body without the hand-written list, and the reason
    val after = h.getArticle(Slug).toOption.get
    assert(!after.article.body.contains("## Not used"), after.article.body)
    assertEquals(after.coverage.map(_.drift), Some(Nil))
    assertEquals(
      after.coverage.toList.flatMap(_.notUsed).map(e => (e.source.citekey, e.reason)),
      List((Ck2, Some("Its isotopes speak to the cargo's origin, not its route.")))
    )
    val fixStep = steps
      .find(_.name == "fixer-1")
      .flatMap(_.output)
      .flatMap(io.circe.parser.decode[ReviewArticle.Fix](_).toOption)
      .get
    assertEquals(fixStep.reasons.map(_.citekey), List(Ck2))
    assertEquals(
      io.circe.parser
        .decode[List[ReviewArticle.Finding]](steps.find(_.name == "mechanical-2").get.output.get)
        .toOption
        .get,
      Nil
    )
    assert(!run.warnings.exists(_.contains("records no filter")), run.warnings)

  test(
    "review (#958): an article with no filter keeps the old rule (a hand-written Not used is required) and the run says so"
  ):
    val c = PanelClient(Model)
    c.add("rules", looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug))
    assertEquals(run.status, "done", run.error)
    assert(run.warnings.exists(_.contains("records no filter")), run.warnings)
    assert(!userPrompt(c.requests.get("rules").peek().head).contains("<generated-sections>"))

  test("fix: a concurrent writer wins — the fix is dropped on 412 and the next round reviews their text"):
    val c = PanelClient(Model)
    // the fix is lost, so pass 2 is a full panel on their text: R1 is open, and checked again
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined"))),
      looked(rulesChecking("PASS", List(check("R1", fixed = false, "Tin reached the Aegean by sea and by land"))))
    )
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
    val patched = AgentRepo
      .steps(ds, run.id, 4000)
      .find(_.name == "patch-1")
      .flatMap(_.output)
      .flatMap(io.circe.parser.decode[ReviewArticle.Patched](_).toOption)
    assertEquals(patched, Some(ReviewArticle.Patched(written = false, None, conflict = true)))
    // round 2 reviewed the other writer's text
    val round2 = c.requests
      .get("rules")
      .asScala
      .toList(1)
      .head
      .history
      .entries
      .collectFirst { case ConversationEntry.UserPrompt(p) => p }
      .get
    assert(round2.contains("by sea and by land"), round2)

  test("resume after a provider failure in the panel replays the finished parallel steps"):
    val first = PanelClient(Model)
    first.add("rules", looked(rules("PASS")))
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

  // ── the review ledger (docs/research-review-convergence.md §6) ───────────────────────────────────

  test("fix: pass 1 full → fixer → targeted re-check → fixer → re-check → a full panel confirms"):
    val c = PanelClient(Model)
    val good = s"""{"citekey":"$Ck","offset":null,"quote":"Its cargo dates to about 1320 BCE."}"""
    val undated =
      finding("major", "Disagreements", "the date has no support", "The date of the wreck is argued", evidence = good)
    val mines =
      """{"severity":"major","kind":"missing-source","where":"Open questions","quote":"Where the tin was mined is open","problem":"a study of the mines is missing","fix":"cite it","evidence":[]}"""
    val lesson = "give the year the record gives, with its [[citekey]]"
    // pass 1: R1 (rules) and S1 (skeptic) MAJOR
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined", "Tin reached the Aegean by sea"))),
      Seq(text(glossFixed)),
      looked(rules("PASS"))
    )
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(text(skeptic("MAJOR", List(undated)))),
      // pass 2: S1 is not fixed; a new MAJOR outside the edited passages that is no factual error → minor
      Seq(
        text(
          recheck(List(check("S1", fixed = false, "The wreck is dated", "still no date")), List(mines), List(lesson))
        )
      ),
      // pass 3: fixed
      Seq(text(recheck(List(check("S1", fixed = true, "The cargo dates to about 1320 BCE"))))),
      Seq(text(skeptic("PASS", Nil)))
    )
    val vague = edit("replace", "The date of the wreck is argued", "The wreck is dated")
    val dated = edit("replace", "The wreck is dated", "The cargo dates to about 1320 BCE")
    c.add(
      "fixer",
      Seq(text(fixerAnswer(List(gloss, vague), applied = List("R1", "S1")))),
      Seq(
        text(
          fixerAnswer(List(dated), applied = List("S1")).replace(
            "\"reasons\":[]",
            "\"reasons\":[],\"wontFix\":[{\"id\":\"S2\",\"reason\":\"the mines are another article's subject\"}]"
          )
        )
      )
    )
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(3)))
    assertEquals(run.status, "done", run.error)

    val steps = AgentRepo.steps(ds, run.id, 100000)
    assertEquals(
      steps.map(_.name).distinct,
      List(
        "ledger",
        "snapshot-1",
        "mechanical-1",
        "rules-1",
        "cold-reader-1",
        "skeptic-1",
        "ledger-1",
        "merge-1",
        "proposals-1",
        "fixer-1",
        "patch-1",
        "snapshot-2",
        "mechanical-2",
        "rules-check-2",
        "skeptic-check-2",
        "ledger-2",
        "merge-2",
        "fixer-2",
        "patch-2",
        "snapshot-3",
        "mechanical-3",
        "skeptic-check-3",
        "ledger-3",
        "merge-3",
        "snapshot-4",
        "mechanical-4",
        "rules-4",
        "cold-reader-4",
        "skeptic-4",
        "ledger-4",
        "merge-4",
        "proposals-4",
        "record"
      )
    )
    // only the reviewer owning a claimed finding is asked again; the cold reader owns none
    assertEquals((c.served("rules"), c.served("cold"), c.served("skeptic"), c.served("fixer")), (3, 2, 4, 2))

    // the verification prompt: its own claimed findings, the edited passages, the current article
    val rulesCheck = userPrompt(c.requests.get("rules").asScala.toList(1).head)
    assert(
      rulesCheck.contains("VERIFICATION PASS") && rulesCheck.contains("<findings-to-check>") &&
        rulesCheck.contains("- R1 [rules, major") && !rulesCheck.contains("S1 [skeptic"),
      rulesCheck
    )
    val passages = rulesCheck.split("<edited-passages>")(1).split("</edited-passages>")(0)
    assert(
      passages.contains("Tin, the scarce half of bronze") && passages.contains("The wreck is dated") &&
        passages.contains("Removed:") && !passages.contains("Where the tin was mined"),
      passages
    )
    // a re-check's turns: recheck-turns (6) + one per finding, below the agent's max-turns
    assertEquals(c.allTurns.get("rules").asScala.toList, List(20, 7, 20))
    assertEquals(c.allTurns.get("skeptic").asScala.toList, List(20, 7, 7, 20))

    // the second fixer reads what was not fixed, what was closed, and the lesson
    val fixer2 = userPrompt(c.requests.get("fixer").asScala.toList(1).head)
    assert(fixer2.contains("- S1 [skeptic, major, date]") && fixer2.contains("not fixed yet — still no date"), fixer2)
    assert(fixer2.contains("- S2 [skeptic, minor, missing-source]"), "recorded as minor, outside the edits")
    assert(
      fixer2.contains("<review-ledger>") && fixer2.contains(
        "R1 major (Intro): tin is not defined → fixed, verified by rules in pass 2"
      ) &&
        fixer2.contains(s"- $lesson"),
      fixer2
    )
    assert(!fixer2.contains("- R1 [rules"), "a closed finding is not handed to the fixer again")
    assert(
      run.warnings.exists(_.contains("is outside the edited passages and not a factual error with evidence")),
      run.warnings
    )

    // the confirming panel reads the ledger
    val confirm = userPrompt(c.requests.get("skeptic").asScala.toList(3).head)
    assert(confirm.contains("<review-ledger>") && confirm.contains("S1 major (Disagreements)"), confirm)

    // the ledger in the store: who closed what, when
    val led = ledgerOf(h).get
    assertEquals(
      led.entries.map(e => (e.id, e.status, e.closed.map(x => (x.by, x.round)))),
      List(
        ("R1", ReviewLedger.Status.Fixed, Some(("rules", 2))),
        ("S1", ReviewLedger.Status.Fixed, Some(("skeptic", 3))),
        ("S2", ReviewLedger.Status.WontFix, Some(("fixer", 2)))
      )
    )
    assertEquals(led.entry("S1").flatMap(_.closed).map(_.quote), Some("The cargo dates to about 1320 BCE"))
    assertEquals(led.entry("S2").map(_.finding.severity), Some(ReviewArticle.Severity.Minor))
    assertEquals(led.lessons.map(l => (l.text, l.source)), List((lesson, ReviewLedger.LessonSource.Recheck)))
    assert(h.getArticle(Slug).toOption.get.article.body.contains("The cargo dates to about 1320 BCE"))
    val log = h.ops.queueList("log", all = true).toOption.get.items.filter(_.text.contains(Slug)).map(_.text)
    assert(
      log.exists(l =>
        l.contains("round 4 → PASS") && l.contains("confirmed by a full panel") &&
          l.contains("ledger 2 fixed, 0 open")
      ),
      log
    )
    assertEquals(h.ops.queueList("tasks", all = false).toOption.get.items.filter(_.text.contains(Slug)), Nil)

  test("fix: the confirming panel is skipped while a MAJOR is open (final-panel = clear), and runs with always"):
    def go(mode: String): (RunInfo, PanelClient) =
      clean()
      val c = PanelClient(Model)
      c.add(
        "rules",
        looked(rules("MAJOR", finding("major", "Intro", "tin is not defined", "Tin reached the Aegean by sea"))),
        Seq(
          text(
            recheck(
              List(
                check("R1", fixed = false, "Tin, the scarce half of bronze, reached the Aegean by sea", "still vague")
              )
            )
          )
        ),
        looked(
          rulesChecking(
            "PASS",
            List(check("R1", fixed = false, "Tin, the scarce half of bronze, reached the Aegean by sea"))
          )
        )
      )
      c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
      c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
      c.add("fixer", Seq(text(fixerAnswer(List(gloss)))))
      val cfg = baseCfg.copy(agents = baseCfg.agents.copy(review = baseCfg.agents.review.copy(finalPanel = mode)))
      val (a, h) = handlers(c, cfg); seed(h)
      (start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2))), c)
    val (clear, c1) = go("clear")
    assertEquals(clear.status, "done", clear.error)
    assertEquals((c1.served("rules"), c1.served("cold"), c1.served("skeptic")), (2, 1, 1))
    assert(!AgentRepo.steps(ds, clear.id, 100).exists(_.name == "rules-3"))
    val (always, c2) = go("always")
    assertEquals(always.status, "done", always.error)
    assertEquals((c2.served("rules"), c2.served("cold"), c2.served("skeptic")), (3, 2, 2))
    assert(AgentRepo.steps(ds, always.id, 100).exists(_.name == "merge-3"))

  test(
    "#934: one backlog item per article listing its open MAJORs — rewritten by the next run, closed when none is open"
  ):
    val (_, h) = handlers(PanelClient(Model)); seed(h)
    val legacy = h.ops
      .queueAdd("tasks", QueueAdd(s"review-article [[$Slug]]: MAJOR open — R9 major (x): an old finding"))
      .toOption
      .get
    def runWith(rulesAnswers: Seq[AgentResponse]*): RunInfo =
      val c = PanelClient(Model)
      c.add("rules", rulesAnswers*)
      c.add("cold", Seq(text(coldPass)))
      c.add("skeptic", Seq(text(skeptic("PASS", Nil))))
      val (a2, _) = handlers(c)
      val r = start(a2, RunRequest("review-article", Slug))
      assertEquals(r.status, "done", r.error)
      r
    def items = h.ops.queueList("tasks", all = true).toOption.get.items.filter(_.text.contains(Slug))

    // run 1: two MAJORs → ONE item; the per-finding item of an older run is superseded
    val r1 = runWith(
      looked(
        rules(
          "MAJOR",
          finding("major", "Intro", "tin is not defined", "Tin reached the Aegean by sea"),
          finding("major", "Open questions", "the mines are not named", "Where the tin was mined is open")
        )
      )
    )
    val item = items.filterNot(_.id == legacy.id).head
    assertEquals(
      item.text,
      s"review-article [[$Slug]]: 2 open MAJORs — R1 (Intro): tin is not defined; " +
        s"R2 (Open questions): the mines are not named (agent run ${r1.id.take(8)})"
    )
    assertEquals(item.status, "open")
    val old = items.find(_.id == legacy.id).get
    assertEquals((old.status, old.reason), ("done", Some(s"superseded by #${item.id} (one item per article)")))
    assertEquals(ledgerOf(h).flatMap(_.task), Some(item.id))

    // a person glosses tin; run 2 must check its open findings (an answer without checks is sent back)
    assert(h.patchArticle(Slug, ArticlePatch(body = Some(Revised))).isRight)
    // (the retry amends the answer without looking the record up again: the first attempt's call counts)
    val noChecks = looked(rules("PASS"))
    val checked = text(
      rulesChecking(
        "PASS",
        List(
          check("R1", fixed = true, "Tin, the scarce half of bronze"),
          check("R2", fixed = false, "Where the tin was mined is open")
        )
      )
    )
    val r2 = runWith(noChecks, Seq(checked))
    val after2 = items.filterNot(_.id == legacy.id)
    assertEquals(after2.map(i => (i.id, i.status)), List((item.id, "open")), "the same item, rewritten")
    assertEquals(
      after2.head.text,
      s"review-article [[$Slug]]: 1 open MAJOR — R2 (Open questions): the mines are not named " +
        s"(agent run ${r2.id.take(8)})"
    )
    val steps2 = AgentRepo.steps(ds, r2.id, 100000).filter(_.name == "rules-1")
    assertEquals(steps2.size, 2, "the answer without checks was sent back")
    assert(
      steps2.head.error.exists(e =>
        e.contains("R1: no check") && e.contains("R2: no check") && !e.contains("spot-check")
      ),
      steps2.head.error
    )

    // the mines are named; run 3 closes R2, and the item with it
    assert(
      h.patchArticle(
        Slug,
        ArticlePatch(body =
          Some(Revised.replace("Where the tin was mined is open", s"The tin was mined in Central Asia [[$Ck2]]"))
        )
      ).isRight
    )
    // it now cites two sources: a spot-check on each, each record read
    val spotIso =
      s"""{"citekey":"$Ck2","claim":"The tin was mined in Central Asia","verdict":"attributable","recordQuote":"the tin of the Uluburun cargo came from Central Asia"}"""
    val r3 = runWith(
      Seq(
        tool("get_source", s"""{"citekey":"$Ck"}"""),
        tool("passages", s"""{"citekey":"$Ck2","q":"Central Asia"}"""),
        text(
          rulesSpotting(
            "PASS",
            List(check("R2", fixed = true, "The tin was mined in Central Asia")),
            List(spotTin, spotIso)
          )
        )
      )
    )
    val closed = items.find(_.id == item.id).get
    assertEquals((closed.status, closed.reason), ("done", Some(s"no MAJOR open after agent run ${r3.id.take(8)}")))
    val led = ledgerOf(h).get
    assertEquals(led.task, None)
    assertEquals(
      led.entries.map(e => (e.id, e.status, e.closed.map(_.run.take(8)))),
      List(
        ("R1", ReviewLedger.Status.Fixed, Some(r2.id.take(8))),
        ("R2", ReviewLedger.Status.Fixed, Some(r3.id.take(8)))
      )
    )

  test(
    "#934: an open item of the article the ledger does not know (the interactive skill's) is rewritten, not duplicated"
  ):
    val (_, h) = handlers(PanelClient(Model)); seed(h)
    val mine =
      h.ops.queueAdd("tasks", QueueAdd(s"review-article $Slug: 1 open MAJOR — R4 (Intro): an old finding")).toOption.get
    val twice = h.ops
      .queueAdd("tasks", QueueAdd(s"review-article [[$Slug]]: 3 open MAJORs — a, b, c (agent run deadbeef)"))
      .toOption
      .get
    val c = PanelClient(Model)
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined", "Tin reached the Aegean by sea")))
    )
    c.add("cold", Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))))
    val (a2, _) = handlers(c)
    val r = start(a2, RunRequest("review-article", Slug))
    assertEquals(r.status, "done", r.error)
    val items = h.ops.queueList("tasks", all = true).toOption.get.items.filter(_.text.contains(Slug))
    assertEquals(items.filter(_.status == "open").map(_.id), List(mine.id), "the existing item, rewritten")
    assert(
      items.find(_.id == mine.id).exists(_.text.startsWith(s"review-article [[$Slug]]: 1 open MAJOR — R1 (Intro)"))
    )
    assertEquals(
      items.find(_.id == twice.id).map(i => (i.status, i.reason)),
      Some(("done", Some(s"superseded by #${mine.id} (one item per article)")))
    )
    assertEquals(ledgerOf(h).flatMap(_.task), Some(mine.id))

  test(
    "the term count: a MAJOR count finding an earlier run stored is superseded by the minor one; the article is MINOR"
  ):
    val (_, h) = handlers(PanelClient(Model)); seed(h)
    val legacy = ReviewArticle.Finding(
      "C1",
      ReviewArticle.Reviewer.ColdReader,
      ReviewArticle.Severity.Major,
      "term",
      "explain-back",
      "",
      "27 terms are undefined (more than 8)",
      "define each on first use, and add a Glossary"
    )
    com.histmersion.db.ReviewLedgerRepo.put(ds, ReviewLedger.Ledger(Slug, List(ReviewLedger.Entry(legacy, "old", 1))))
    val c = PanelClient(Model)
    c.add("rules", looked(rules("PASS")))
    // the reader still counts 12 and, as its instructions may say, calls that MAJOR
    c.add(
      "cold",
      Seq(
        text(
          s"""{"verdict":"MAJOR","explainBack":"It asks how tin reached the Aegean. By sea. The date is argued. The mines are open.","emptySlots":[],"findings":[],"termsUndefined":12,"opaqueSentences":0,"lostAt":null,"checks":[${check(
              "C1",
              fixed = false,
              "",
              "still twelve"
            )}]}"""
        )
      )
    )
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))))
    val (a, _) = handlers(c)
    val run = start(a, RunRequest("review-article", Slug))
    assertEquals(run.status, "done", run.error)
    assertEquals(c.served("cold"), 1, "the count's MAJOR verdict is corrected, not sent back")
    val merged = io.circe.parser
      .decode[ReviewArticle.Merged](AgentRepo.steps(ds, run.id, 4000).find(_.name == "merge-1").get.output.get)
      .toOption
      .get
    assertEquals(merged.verdict, ReviewArticle.Verdict.Minor)
    val led = ledgerOf(h).get
    assertEquals(
      led.entries.map(e => (e.id, e.status, e.finding.severity, e.finding.problem)),
      List(
        (
          "C1",
          ReviewLedger.Status.Open,
          ReviewArticle.Severity.Minor,
          "12 terms undefined (count only; the individual findings carry the severity)"
        )
      )
    )
    assertEquals(led.task, None)
    assert(!h.ops.queueList("tasks", all = true).toOption.get.items.exists(_.text.contains(Slug)))

  test("the ledger is the article's: it follows a rename and goes with the article"):
    val (_, h) = handlers(PanelClient(Model)); seed(h)
    val l = ReviewLedger.Ledger(
      Slug,
      List(
        ReviewLedger.Entry(
          ReviewArticle.Finding(
            "R1",
            ReviewArticle.Reviewer.Rules,
            ReviewArticle.Severity.Major,
            "term",
            "Intro",
            "Tin",
            "tin is not defined",
            "define it"
          ),
          "run",
          1
        )
      )
    )
    com.histmersion.db.ReviewLedgerRepo.put(ds, l)
    assertEquals(com.histmersion.db.ReviewLedgerRepo.get(ds, Slug), Right(Some(l)))
    val moved = Slug + "_moved"
    assert(h.renameArticle(Slug, LabelRename(moved)).isRight)
    assertEquals(com.histmersion.db.ReviewLedgerRepo.get(ds, moved).map(_.map(_.entries)), Right(Some(l.entries)))
    assert(h.deleteArticle(moved).isRight)
    assertEquals(com.histmersion.db.ReviewLedgerRepo.get(ds, moved), Right(None))

  // ── #992: dry runs, a role's own model, compare ──────────────────────────────────────────────────

  private val Haiku = ModelRef(ProviderKind.Local, "haiku-sim")
  private lazy val evalCfg = baseCfg.copy(agents = baseCfg.agents.copy(prices = "scripted=1/2; haiku-sim=3/4"))

  private def handlersWith(f: LlmClient.Factory, cfg: AppConfig = evalCfg): (AgentsHandlers, Handlers) =
    val deps = Deps(cfg, ds)
    val h = Handlers(deps)
    (AgentsHandlers(deps, h, f), h)

  test("fix dry run: nothing is written, yet round 2 reviews the fixer's unwritten revision"):
    val c = PanelClient(Model)
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined"))),
      Seq(text(glossFixed)),
      looked(rules("PASS"))
    )
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(text(skeptic("MINOR", List(finding("minor", "Open questions", "a source is missing")), List(edgeProposal)))),
      Seq(text(skeptic("PASS", Nil, checks = List(check("S1", fixed = false, s"Where the tin was mined is open")))))
    )
    c.add("fixer", Seq(text(fixerAnswer(List(gloss)))))
    val (a, h) = handlers(c); seed(h)
    val before = CorpusSnapshot.of(ds)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(3), dryRun = Some(true)))
    assertEquals(run.status, "done", run.error)
    assert(run.dryRun)
    assertEquals(CorpusSnapshot.of(ds), before, "no patch, no approval, no log or tasks line")
    assertEquals(h.getArticle(Slug).toOption.get.article.body.trim, Body.trim)

    val steps = AgentRepo.steps(ds, run.id, 100000)
    assertEquals(
      steps.map(_.name),
      List(
        "ledger",
        "snapshot-1",
        "mechanical-1",
        "rules-1",
        "cold-reader-1",
        "skeptic-1",
        "ledger-1",
        "merge-1",
        "proposals-1",
        "fixer-1",
        "patch-1",
        "snapshot-2",
        "mechanical-2",
        "rules-check-2",
        "ledger-2",
        "merge-2",
        "snapshot-3",
        "mechanical-3",
        "rules-3",
        "cold-reader-3",
        "skeptic-3",
        "ledger-3",
        "merge-3",
        "proposals-3",
        "record"
      )
    )
    // the revision travels to round 2 in memory
    val snap2 = steps
      .find(_.name == "snapshot-2")
      .flatMap(_.output)
      .flatMap(io.circe.parser.decode[ReviewArticle.Snapshot](_).toOption)
      .get
    assertEquals(snap2.body.trim, Revised.trim)
    assert(
      userPrompt(c.requests.get("rules").asScala.toList(1).head).contains("Tin, the scarce half of bronze"),
      "the second panel read the revised text"
    )
    val patch = steps.find(_.name == "patch-1").get
    assertEquals(
      patch.output.flatMap(io.circe.parser.decode[ReviewArticle.Patched](_).toOption),
      Some(ReviewArticle.Patched(written = false, None, conflict = false))
    )
    assertEquals(
      patch.wouldWrite.map(_.what),
      List(
        s"patch article [[$Slug]] (round 1): 1 finding(s) answered, 1 edit(s)",
        s"review ledger of [[$Slug]]: 2 open (1 major), 0 closed, 0 lesson(s)"
      )
    )
    assert(
      patch.wouldWrite.head.payload.contains("the scarce half of bronze") && patch.wouldWrite.head.payload
        .contains("ifUpdatedAt")
    )
    assert(steps.find(_.name == "proposals-1").get.wouldWrite.exists(_.what.startsWith("edge proposal")))
    assertEquals(
      steps.find(_.name == "record").get.wouldWrite.map(_.what),
      List(s"review ledger of [[$Slug]]: 1 open (0 major), 1 closed, 0 lesson(s)", "log line")
    )
    // the ledger travelled in memory: pass 2 verified R1, and nothing reached the store
    val led = steps
      .find(_.name == "ledger-2")
      .flatMap(_.output)
      .flatMap(io.circe.parser.decode[ReviewLedger.Ledger](_).toOption)
      .get
    assertEquals(
      led.entries.map(e => (e.id, e.status)),
      List(("R1", ReviewLedger.Status.Fixed), ("S1", ReviewLedger.Status.Open))
    )
    assertEquals(ledgerOf(h), None)

  test("--model-for: the overridden role's calls use its model, the other roles keep theirs; over --model too"):
    val c = PanelClient(Model)
    c.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    val (a, h) = handlersWith((_, ref) => Right(c.as(ref))); seed(h)
    val run = start(a, RunRequest("review-article", Slug, modelFor = List(ModelOverride("skeptic", Haiku.wire))))
    assertEquals(run.status, "done", run.error)
    def used = c.modelsUsed.asScala.view.mapValues(_.asScala.toList).toMap
    assertEquals(used, Map("rules" -> List(Model.wire), "cold" -> List(Model.wire), "skeptic" -> List(Haiku.wire)))
    assertEquals(
      AgentRepo.steps(ds, run.id, 100).filter(_.kind == "llm").map(s => s.name -> s.model),
      List("rules-1" -> Some(Model.wire), "cold-reader-1" -> Some(Model.wire), "skeptic-1" -> Some(Haiku.wire))
    )
    assert(AgentsMarkdown.runLine(run).contains(s"skeptic=${Haiku.wire}"), AgentsMarkdown.runLine(run))
    // --model moves every role; --model-for still wins for its own
    val second = start(
      a,
      RunRequest(
        "review-article",
        Slug,
        model = Some(Haiku.wire),
        modelFor = List(ModelOverride("reviewer", Model.wire))
      )
    )
    assertEquals(second.status, "done", second.error)
    assertEquals(
      used,
      Map(
        "rules" -> List(Model.wire, Model.wire),
        "cold" -> List(Model.wire, Haiku.wire),
        "skeptic" -> List(Haiku.wire, Haiku.wire)
      )
    )
    // a role the pipeline does not use is refused
    assert(
      a.startRun(RunRequest("review-article", Slug, modelFor = List(ModelOverride("writer", Haiku.wire))))
        .left
        .exists(_.message.contains("review-article uses no writer role"))
    )

  test("compare: two review-article runs — verdicts, findings per reviewer and kind, the major findings they share"):
    val c = PanelClient(Model)
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined"))),
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined at all")))
    )
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(text(skeptic("MAJOR", List(dateMajor)))),
      Seq(text(skeptic("MINOR", List(finding("minor", "Open questions", "a source is missing")))))
    )
    val (a, h) = handlersWith((_, ref) => Right(c.as(ref))); seed(h)
    val r1 = start(a, RunRequest("review-article", Slug, dryRun = Some(true)))
    val r2 = start(
      a,
      RunRequest("review-article", Slug, dryRun = Some(true), modelFor = List(ModelOverride("reviewer", Haiku.wire)))
    )
    assertEquals((r1.status, r2.status), ("done", "done"))
    val cmp = a.compare(List(r1.id, r2.id)).fold(e => fail(e.message), identity)
    assertEquals(
      cmp.steps.find(_.name == "rules-1").get.runs.map(_.map(_.models)),
      List(Some(List(Model.wire)), Some(List(Haiku.wire)))
    )
    assertEquals(cmp.review.map(r => (r.verdict, r.rounds)), List((Some("MAJOR"), 1), (Some("MAJOR"), 1)))
    def panel(i: Int) =
      cmp.review(i).reviewers.filter(_.reviewer != "mechanical").map(x => (x.reviewer, x.verdict, x.majors, x.minors))
    assertEquals(
      panel(0),
      List(("rules", Some("MAJOR"), 1, 0), ("cold-reader", Some("PASS"), 0, 0), ("skeptic", Some("MAJOR"), 1, 0))
    )
    assertEquals(
      panel(1),
      List(("rules", Some("MAJOR"), 1, 0), ("cold-reader", Some("PASS"), 0, 0), ("skeptic", Some("MINOR"), 0, 1))
    )
    assertEquals(
      cmp.review.map(_.kinds),
      List(List(RunCompare.KindCount("date", 2)), List(RunCompare.KindCount("date", 2)))
    )
    assertEquals(
      cmp.review.map(_.majors),
      List(
        List("R1 major (Intro): tin is not defined", "S1 major (Disagreements): the date has no support in the record"),
        List("R1 major (Intro): tin is not defined at all")
      )
    )
    assertEquals(cmp.overlaps, List(RunCompare.MajorOverlap(r1.id, r2.id, 2, 1, 1, List("R1 ↔ R1"))))
    val md = AgentsMarkdown.compare(cmp)
    assert(
      md.contains("| verdict (last round) | MAJOR | MAJOR |") && md.contains("1 shared of 2 and 1 (R1 ↔ R1)") &&
        md.contains("| skeptic (round 1) | MAJOR · 1 major, 0 minor | MINOR · 0 major, 1 minor |"),
      md
    )

  test(
    "#1840: a rules answer that reports spot-checks with no lookup is sent back; the retry that reads the record stands"
  ):
    val c = PanelClient(Model)
    // attempt 1 answers from the brief alone; attempt 2 reads the record, then answers
    c.add("rules", Seq(text(rules("PASS"))), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))))
    val (a, h) = handlers(c); seed(h)
    val run = start(a, RunRequest("review-article", Slug))
    assertEquals(run.status, "done", run.error)
    val steps = AgentRepo.steps(ds, run.id, 100000).filter(_.name == "rules-1")
    assertEquals(steps.map(_.status), List("failed", "done"))
    assert(steps.head.error.exists(_.contains("spot-checks: 0 of the 1 required are verified")), steps.head.error)
    assert(steps.head.error.exists(_.contains("no get_source or passages call for it in this step")), steps.head.error)
    // the retry is told what was missing
    assert(retryPoints(userPrompt(c.requests.get("rules").asScala.toList(1).head)).exists(_.startsWith("spot-checks:")))
    val report = io.circe.parser.decode[ReviewArticle.Report](steps(1).output.get).toOption.get
    assertEquals(report.spotChecks.map(s => (s.citekey, s.tool, s.verdict)), List((Ck, "get_source", "attributable")))

  test("#1840: on the last attempt an answer without verified spot-checks is kept, with a warning on the run"):
    val c = PanelClient(Model)
    val cfg = baseCfg.copy(agents = baseCfg.agents.copy(step = baseCfg.agents.step.copy(maxRetries = 0)))
    c.add("rules", Seq(text(rules("PASS"))))
    c.add("cold", Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))))
    val (a, h) = handlers(c, cfg); seed(h)
    val run = start(a, RunRequest("review-article", Slug))
    assertEquals(run.status, "done", run.error)
    assert(run.warnings.exists(_.contains("rules: unverified — spot-checks: 0 of the 1 required")), run.warnings)

  test(
    "#1840: the ledger tells the reviewers that read the database it does not replace their look-ups; not the cold reader"
  ):
    val c = PanelClient(Model)
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined", "Tin reached the Aegean by sea"))),
      looked(rulesChecking("PASS", List(check("R1", fixed = false, "Tin reached the Aegean by sea"))))
    )
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("PASS", Nil))), Seq(text(skeptic("PASS", Nil))))
    val (a, h) = handlers(c); seed(h)
    assertEquals(start(a, RunRequest("review-article", Slug)).status, "done")
    assertEquals(start(a, RunRequest("review-article", Slug)).status, "done")
    def second(w: String) = userPrompt(c.requests.get(w).asScala.toList(1).head)
    assert(second("rules").contains("<review-ledger>") && second("rules").contains("the database look-ups included"))
    assert(second("skeptic").contains("the database look-ups included"))
    // the cold reader reads only its own ledger findings (§6.9): it has none here, so no ledger at all
    assert(
      !second("cold").contains("<review-ledger>") && !second("cold").contains("tin is not defined"),
      second("cold")
    )

  // ── the critique checklist (docs/research-review-convergence.md §6.8) ────────────────────────────

  private val Ck3 = "zzagent2021_tin"

  /** Two critics of the cited source: Ck2 refutes it, Ck3 challenges it — two open pairs. */
  private def critics(h: Handlers): Unit =
    assert(
      h.putSource(
        Ck3,
        SourcePut(
          title = "Tin routes",
          year = Some(2021),
          body = Some("The tin went overland before it reached the coast.")
        )
      ).isRight
    )
    assert(
      h.patchSource(Ck2, SourcePatch(edges = Some(EdgesPatch(add = List(EdgeInput(Ck, EdgeKind.Refutes)))))).isRight
    )
    assert(
      h.patchSource(Ck3, SourcePatch(edges = Some(EdgesPatch(add = List(EdgeInput(Ck, EdgeKind.Challenges)))))).isRight
    )

  /** After a fix that cites a second source the confirming rules reviewer would owe two spot-checks; its scripted
    * answer carries one (the spot-checks are not what these tests are about).
    */
  private lazy val oneSpot =
    baseCfg.copy(agents = baseCfg.agents.copy(review = baseCfg.agents.review.copy(spotChecks = 1)))

  private def states(h: Handlers): List[(String, String)] =
    h.critiques(Slug, h.getArticle(Slug).toOption.get.article.body).map(p => (p.ref.wire, p.state.wire))

  test(
    "critiques: the skeptic rules on every open pair; one is taken up in one paragraph, one is not material — converged"
  ):
    val c = PanelClient(Model)
    val citing = edit(
      "replace",
      s"Tin reached the Aegean by sea [[$Ck]].",
      s"Tin reached the Aegean by sea [[$Ck]]; [[$Ck2]] traces it to Central Asia."
    )
    c.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(
        text(
          skeptic(
            "MAJOR",
            List(critique("major", Some(s"$Ck2>$Ck"))),
            notMaterial = List(
              s"$Ck3>$Ck" -> "The article cites it for the sea route's end, which the overland claim leaves alone."
            )
          )
        )
      ),
      Seq(text(recheck(List(check("S1", fixed = true, s"[[$Ck2]] traces it to Central Asia"))))),
      Seq(text(skeptic("PASS", Nil)))
    )
    c.add("fixer", Seq(text(fixerAnswer(List(citing), applied = List("S1")))))
    val (a, h) = handlers(c, oneSpot); seed(h); critics(h)
    assertEquals(states(h), List(s"$Ck3>$Ck" -> "open", s"$Ck2>$Ck" -> "open"))
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)

    // the skeptic's brief carries the checklist; the fixer reads the finding's pair
    val brief = userPrompt(c.requests.get("skeptic").peek().head)
    assert(
      brief.contains("<critique-checklist>") && brief.contains("2 pairs — 0 addressed, 0 reasoned, 2 open") &&
        brief.contains(
          s"$Ck2>$Ck: [[$Ck2]] (2024, \"Tin isotopes\", not cited) refutes [[$Ck]] (2019, \"Bronze Age tin\", cited)"
        ),
      brief
    )
    assert(
      !userPrompt(c.requests.get("rules").peek().head).contains("<critique-checklist>"),
      "only the skeptic rules on pairs"
    )
    assert(userPrompt(c.requests.get("fixer").peek().head).contains(s"pair: $Ck2>$Ck"))
    // one addressed by the fix, one reasoned by the skeptic's ruling (stored, with who set it)
    assertEquals(states(h), List(s"$Ck3>$Ck" -> "reasoned", s"$Ck2>$Ck" -> "addressed"))
    val stored = com.histmersion.db.CritiqueRepo.list(ds, Slug)
    assertEquals(
      stored.map(r => (r.src, r.dst, r.by.exists(_.startsWith("skeptic (agent run")))),
      List((Ck3, Ck, true))
    )
    val ledger = ledgerOf(h).get
    assertEquals(
      ledger.entries.map(e => (e.id, e.status, e.finding.pair.map(_.wire))),
      List(("S1", ReviewLedger.Status.Fixed, Some(s"$Ck2>$Ck")))
    )
    assertEquals(ledger.openMajors, Nil)
    val log = h.ops.queueList("log", all = true).toOption.get.items.filter(_.text.contains(Slug)).map(_.text)
    assert(
      log.exists(l =>
        l.contains("round 3 → PASS") && l.contains("critiques 2 pairs — 1 addressed, 1 reasoned, 0 open")
      ),
      log
    )
    // the confirming skeptic was shown no open pair, only the reasoned one
    val confirm = userPrompt(c.requests.get("skeptic").asScala.toList(2).head)
    assert(confirm.contains("No pair is open.") && confirm.contains(s"$Ck3>$Ck: The article cites it"), confirm)
    assertEquals(h.openCritiques(Slug), Some(0))

  test(
    "critiques: a missing-critique MAJOR on no pair, and an unruled open pair, are sent back; the fixer sets one aside"
  ):
    val c = PanelClient(Model)
    c.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      // attempt 1: no pair, and Ck3>Ck not ruled on; attempt 2: both ruled on
      Seq(text(skeptic("MAJOR", List(critique("major", None))))),
      Seq(
        text(
          skeptic(
            "MAJOR",
            List(critique("major", Some(s"$Ck>$Ck2"))),
            notMaterial =
              List(s"$Ck3>$Ck" -> "The overland claim does not touch the sea crossing the article cites it for.")
          )
        )
      ),
      Seq(text(skeptic("PASS", Nil)))
    )
    val aside =
      s"""{"id":"S1","pair":null,"reason":"The article cites the 2019 paper for the ship's cargo, which the isotope study leaves standing."}"""
    c.add(
      "fixer",
      Seq(
        text(
          s"""{"edits":[],"applied":[],"rejected":[],"unresolvedMoved":[],"notes":"","reasons":[],"setAside":[$aside]}"""
        )
      )
    )
    val (a, h) = handlers(c); seed(h); critics(h)
    val token = h.getArticle(Slug).toOption.get.article.updatedAt
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    val retry = retryPoints(userPrompt(c.requests.get("skeptic").asScala.toList(1).head))
    assert(
      retry.exists(
        _.contains("S1 (missing-critique, major) names no pair of the critique checklist (its `pair` is empty)")
      ),
      retry
    )
    assert(
      retry.exists(_.contains(s"open pairs of the critique checklist without a ruling: $Ck2>$Ck, $Ck3>$Ck")),
      retry
    )
    // the pair named in the other direction is the checklist's own
    val ledger = ledgerOf(h).get
    val s1 = ledger.entry("S1").get
    assertEquals((s1.status, s1.finding.pair.map(_.wire)), (ReviewLedger.Status.SetAside, Some(s"$Ck2>$Ck")))
    assert(ReviewLedger.closedLine(s1).contains("set aside as not material"), ReviewLedger.closedLine(s1))
    assertEquals(states(h), List(s"$Ck3>$Ck" -> "reasoned", s"$Ck2>$Ck" -> "reasoned"))
    assertEquals(
      com.histmersion.db.CritiqueRepo.list(ds, Slug).map(r => (r.src, r.by.exists(_.startsWith("fixer")))),
      List((Ck3, false), (Ck2, true))
    )
    // a set-aside alone writes no version and leaves the article's token as it was
    assertEquals(h.getArticle(Slug).toOption.get.article.updatedAt, token)
    assertEquals(h.articleVersions(Slug).toOption.get, Nil)
    assertEquals(ledger.openMajors, Nil)

  test(
    "critiques (§6.9): a missing critique on an edge not yet recorded is minor in pass 1 too; its proposal waits for a person"
  ):
    val c = PanelClient(Model)
    // no edge between Ck and Ck2 yet: the skeptic proposes it and names the pair
    val proposal =
      s"""{"kind":"edge","citekey":"$Ck2","status":null,"edgeKind":"refutes","target":"$Ck","evidence":"the newer study reassigns the tin","quote":"the tin of the Uluburun cargo came from Central Asia"}"""
    c.add("rules", looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)))
    c.add("skeptic", Seq(text(skeptic("MAJOR", List(critique("major", Some(s"$Ck2>$Ck"))), List(proposal)))))
    val (a, h) = handlers(c, oneSpot); seed(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    // recorded minor: no MAJOR is open, so the fixer never runs
    assertEquals(c.served("fixer"), 0)
    val ledger = ledgerOf(h).get
    assertEquals(
      ledger.entries.map(e => (e.id, e.status, e.finding.severity)),
      List(("S1", ReviewLedger.Status.Open, ReviewArticle.Severity.Minor))
    )
    assert(
      ledger
        .entry("S1")
        .get
        .finding
        .problem
        .contains(s"no edge is recorded between [[$Ck2]] and [[$Ck]] — its proposed edge waits for approval"),
      ledger.entry("S1").get.finding.problem
    )
    assert(run.warnings.exists(_.contains("recorded minor")), run.warnings)
    // the edge waits for a person (edges = manual): the checklist has no pair yet; the next run's will
    assertEquals(
      AgentRepo.approvals(ds, Some(ApprovalState.Open), 50).filter(_.citekey.startsWith("zzagent")).map(_.citekey),
      List(Ck2)
    )

  // ── convergence (docs/research-review-convergence.md §6.9): the three failure modes of 2026-10-19 ───────

  /** Ck2 refutes Ck: one open pair, on a work the article cites. */
  private def refuted(h: Handlers): Unit =
    assert(
      h.patchSource(Ck2, SourcePatch(edges = Some(EdgesPatch(add = List(EdgeInput(Ck, EdgeKind.Refutes)))))).isRight
    )

  private def logOf(h: Handlers) =
    h.ops.queueList("log", all = true).toOption.get.items.filter(_.text.contains(Slug)).map(_.text)
  private def tasksOf(h: Handlers) =
    h.ops.queueList("tasks", all = false).toOption.get.items.filter(_.text.contains(Slug)).map(_.text)
  private val coCite = edit(
    "replace",
    s"Tin reached the Aegean by sea [[$Ck]].",
    s"Tin reached the Aegean by sea [[$Ck]]; [[$Ck2]] traces it to Central Asia."
  )

  test(
    "§6.9 late new critiques: what the final pass finds new is deferred — the run ends MINOR, the next run fixes it and converges"
  ):
    // run 1: pass 1 raises S1 on the open pair, the fixer cites the critic beside the claim, the re-check closes it;
    // the confirming panel then raises a NEW MAJOR on text the run never touched
    val c = PanelClient(Model)
    c.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    val late = finding("major", "Open questions", "no source dates the mines", "Where the tin was mined is open")
    c.add(
      "skeptic",
      Seq(text(skeptic("MAJOR", List(critique("major", Some(s"$Ck2>$Ck")))))),
      Seq(text(recheck(List(check("S1", fixed = true, s"[[$Ck2]] traces it to Central Asia"))))),
      Seq(text(skeptic("MAJOR", List(late))))
    )
    c.add("fixer", Seq(text(fixerAnswer(List(coCite), applied = List("S1")))))
    val (a, h) = handlers(c, oneSpot); seed(h); refuted(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    // the final pass was told it verifies
    assert(userPrompt(c.requests.get("skeptic").asScala.toList(2).head).contains("This is the run's FINAL pass"))
    val led = ledgerOf(h).get
    assertEquals(
      led.entries.map(e => (e.id, e.status, e.deferred)),
      List(("S1", ReviewLedger.Status.Fixed, None), ("S2", ReviewLedger.Status.Open, Some(run.id)))
    )
    val merge3 = AgentRepo.steps(ds, run.id, 100000).find(_.name == "merge-3").get
    assert(merge3.output.exists(_.replace(" ", "").contains("\"deferred\":[\"S2\"]")), merge3.output)
    assert(
      logOf(h).exists(l =>
        l.contains("round 3 → MINOR") && l.contains("1 new MAJOR of the final pass deferred to the next run")
      ),
      logOf(h)
    )
    assert(run.warnings.exists(_.contains("1 new MAJOR deferred to the next run — S2")), run.warnings)
    // logged and visible: the article's backlog item names it, the ledger still has it open MAJOR (the next run's work)
    assert(
      tasksOf(h).exists(t => t.contains("1 open MAJOR") && t.contains("S2") && t.contains("deferred to the next run")),
      tasksOf(h)
    )
    assertEquals(led.openMajors.map(_.id), List("S2"))

    // run 2: pass 1 hands S2 to the fixer like any open MAJOR; fixed and confirmed — PASS, the item closed
    val c2 = PanelClient(Model)
    val dated = edit(
      "replace",
      s"Where the tin was mined is open [[$Ck]].",
      s"Where the tin was mined is not dated by any source here [[$Ck]]."
    )
    c2.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c2.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c2.add(
      "skeptic",
      Seq(
        text(
          skeptic(
            "PASS",
            Nil,
            checks = List(check("S2", fixed = false, "Where the tin was mined is open", "still undated"))
          )
        )
      ),
      Seq(text(recheck(List(check("S2", fixed = true, "Where the tin was mined is not dated by any source here"))))),
      Seq(text(skeptic("PASS", Nil)))
    )
    c2.add("fixer", Seq(text(fixerAnswer(List(dated), applied = List("S2")))))
    val (a2, _) = handlers(c2, oneSpot)
    val run2 = start(a2, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run2.status, "done", run2.error)
    assert(userPrompt(c2.requests.get("fixer").peek().head).contains("- S2 [skeptic, major, date]"))
    assertEquals(ledgerOf(h).get.openMajors, Nil)
    assert(logOf(h).exists(l => l.contains(run2.id.take(8)) && l.contains("round 3 → PASS")), logOf(h))
    assertEquals(tasksOf(h), Nil)

  test(
    "§6.9 not-fixed co-citation: a paragraph that names the work it answers gets the citation from code; the re-check confirms"
  ):
    val c = PanelClient(Model)
    c.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(text(skeptic("MAJOR", List(critique("major", Some(s"$Ck2>$Ck")))))),
      Seq(text(recheck(List(check("S1", fixed = true, "An isotope study answers Tinley's sea route"))))),
      Seq(text(skeptic("PASS", Nil)))
    )
    // the fixer answers nothing: the critique is written, only the citation of the work it answers is missing
    c.add("fixer", Seq(text(fixerAnswer(Nil, applied = Nil))))
    val (a, h) = handlers(c, oneSpot); seed(h); refuted(h)
    val body = h
      .getArticle(Slug)
      .toOption
      .get
      .article
      .body
      .replace(
        s"Tin reached the Aegean by sea [[$Ck]].",
        s"Tin reached the Aegean by sea [[$Ck]].\n\nAn isotope study answers Tinley's sea route [[$Ck2]]."
      )
    assert(h.patchArticle(Slug, ArticlePatch(body = Some(body))).isRight)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(2)))
    assertEquals(run.status, "done", run.error)
    assert(
      h.getArticle(Slug)
        .toOption
        .get
        .article
        .body
        .contains(s"An isotope study answers Tinley's sea route [[$Ck2]] [[$Ck]].")
    )
    // the repaired finding was claimed by code and verified by its reviewer
    assertEquals(
      ledgerOf(h).get.entries.map(e => (e.id, e.status, e.closed.map(_.by))),
      List(("S1", ReviewLedger.Status.Fixed, Some("skeptic")))
    )
    val patch = AgentRepo.steps(ds, run.id, 100000).find(_.name == "fixer-1").get
    assert(patch.output.exists(_.replace(" ", "").contains("\"repaired\":[\"S1\"]")), patch.output)
    assertEquals(states(h), List(s"$Ck2>$Ck" -> "addressed"))
    assert(logOf(h).exists(_.contains("round 3 → PASS")), logOf(h))

  test(
    "§6.9 not-fixed co-citation: a claim without the citation is sent back, then recorded not fixed — the next fixer is told where it goes"
  ):
    val c = PanelClient(Model)
    c.add("rules", looked(rules("PASS")), looked(rules("PASS")))
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(text(skeptic("MAJOR", List(critique("major", Some(s"$Ck2>$Ck")))))),
      Seq(text(recheck(List(check("S1", fixed = true, s"[[$Ck2]] traces it to Central Asia"))))),
      Seq(text(skeptic("PASS", Nil)))
    )
    // fixer-1, three attempts: the critique in a paragraph of its own, apart from the claim it answers
    val apart =
      edit("insertAfter", s"Tin reached the Aegean by sea [[$Ck]].", s"\n\nAn isotope study disagrees [[$Ck2]].")
    c.add("fixer", (1 to 3).map(_ => Seq(text(fixerAnswer(List(apart), applied = List("S1")))))*)
    c.add("fixer", Seq(text(fixerAnswer(List(coCite), applied = List("S1")))))
    val (a, h) = handlers(c, oneSpot); seed(h); refuted(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(3)))
    assertEquals(run.status, "done", run.error)
    val fixerCalls = c.requests.get("fixer").asScala.toList
    val retry = retryPoints(userPrompt(fixerCalls(1).head))
    assert(
      retry.exists(_.startsWith(s"S1 is listed as applied, but [[$Ck2]] and [[$Ck]] are not cited in one paragraph")),
      retry
    )
    // fixer-2 reads why it was not fixed, and where the critique goes
    val second = userPrompt(fixerCalls(3).head)
    assert(
      second.contains(
        s"not fixed yet — pass 1: listed as applied, but [[$Ck2]] and [[$Ck]] are not cited in one paragraph"
      ),
      second
    )
    assert(
      second.contains("<critique-work>") &&
        second.contains(s"in the paragraph beginning \"Tin reached the Aegean by sea\" (it cites [[$Ck]])"),
      second
    )
    // pass 2 had nothing claimed to re-check; pass 3 verified the second fix; the confirmation passed
    assert(!AgentRepo.steps(ds, run.id, 200).exists(_.name == "skeptic-check-2"))
    val e = ledgerOf(h).get.entry("S1").get
    assertEquals((e.status, e.notFixedCount), (ReviewLedger.Status.Fixed, 1))
    assert(logOf(h).exists(_.contains("round 4 → PASS")), logOf(h))

  test(
    "§6.9 regression: a later fix that splits a closed critique from its claim is sent back; the amended fix keeps it"
  ):
    val c = PanelClient(Model)
    c.add(
      "rules",
      looked(rules("MAJOR", finding("major", "Intro", "tin is not defined", "Tin reached the Aegean by sea"))),
      Seq(text(recheck(List(check("R1", fixed = false, "Tin reached the Aegean by sea", "still undefined"))))),
      Seq(text(recheck(List(check("R1", fixed = true, "Tin, the scarce half of bronze, reached the Aegean by sea"))))),
      looked(rules("PASS"))
    )
    c.add("cold", Seq(text(coldPass)), Seq(text(coldPass)))
    c.add(
      "skeptic",
      Seq(text(skeptic("MAJOR", List(critique("major", Some(s"$Ck2>$Ck")))))),
      Seq(text(recheck(List(check("S1", fixed = true, s"[[$Ck2]] traces it to Central Asia"))))),
      Seq(text(skeptic("PASS", Nil)))
    )
    val vague = edit("replace", "The date of the wreck is argued", "The wreck's date is argued")
    val split = edit(
      "replace",
      s"Tin reached the Aegean by sea [[$Ck]]; [[$Ck2]] traces it to Central Asia.",
      s"Tin, the scarce half of bronze, reached the Aegean by sea [[$Ck]].\n\nAn isotope study traces it to Central Asia [[$Ck2]]."
    )
    val gloss2 =
      edit("replace", "Tin reached the Aegean by sea", "Tin, the scarce half of bronze, reached the Aegean by sea")
    c.add(
      "fixer",
      Seq(text(fixerAnswer(List(coCite, vague), applied = List("R1", "S1")))),
      Seq(text(fixerAnswer(List(split), applied = List("R1")))),
      Seq(text(fixerAnswer(List(gloss2), applied = List("R1"))))
    )
    val (a, h) = handlers(c, oneSpot); seed(h); refuted(h)
    val run = start(a, RunRequest("review-article", Slug, fix = Some(true), rounds = Some(3)))
    assertEquals(run.status, "done", run.error)
    val retry = retryPoints(userPrompt(c.requests.get("fixer").asScala.toList(2).head))
    assert(
      retry.exists(
        _.startsWith(
          "your edits reopen a closed finding — S1 (closed): " +
            s"[[$Ck2]] and [[$Ck]] are no longer cited in one paragraph"
        )
      ),
      retry
    )
    val led = ledgerOf(h).get
    assertEquals(
      led.entries.map(e => (e.id, e.status, e.reopened)),
      List(("R1", ReviewLedger.Status.Fixed, 0), ("S1", ReviewLedger.Status.Fixed, 0))
    )
    assert(
      h.getArticle(Slug)
        .toOption
        .get
        .article
        .body
        .contains(
          s"Tin, the scarce half of bronze, reached the Aegean by sea [[$Ck]]; [[$Ck2]] traces it to Central Asia."
        )
    )
    assert(logOf(h).exists(_.contains("round 4 → PASS")), logOf(h))
