package com.histmersion.domain

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.api.Agents.*
import com.histmersion.domain.EnrichSource.*
import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import io.circe.syntax.*

/** `agent compare` (#992), the pure half: what can be compared, the step union, the verifier's quote
  * check read back from accepted AND rejected answers, the claims only one run made, and the major
  * findings two review runs share. */
class CompareRunsSuite extends munit.FunSuite:

  private def step(seq: Int, name: String, status: String = "done", output: Option[String] = None, model: Option[String] = None,
      actual: Option[String] = None, error: Option[String] = None, usage: Usage = Usage.Zero, cost: Double = 0,
      started: String = "2026-10-08T10:00:00Z", finished: String = "2026-10-08T10:00:03Z", attempt: Int = 1): StepInfo =
    StepInfo(seq, name, if model.isDefined then "llm" else "code", attempt, status, model, None, usage, cost, error, output,
      started, finished, actual)

  private def run(id: String, pipeline: String, target: String, steps: List[StepInfo], status: String = "done",
      dry: Boolean = false, overrides: List[ModelOverride] = Nil): RunDetail =
    RunDetail(RunInfo(id, pipeline, target, status, dryRun = dry, params = RunParams(modelFor = Option.when(overrides.nonEmpty)(overrides))),
      steps, Nil)

  private val billing: String => Option[String] = {
    case "claude-code:haiku" => Some("plan pool: claude-plan")
    case "anthropic:x"       => Some("price: 1/5 USD per million tokens")
    case _                   => None
  }

  test("refused: fewer than two runs, a run twice, two pipelines, two targets"):
    val a = run("a", "enrich-source", "x2020", Nil)
    assert(CompareRuns.compare(List(a), billing).left.exists(_.contains("at least two")))
    assert(CompareRuns.compare(List(a, a), billing).left.exists(_.contains("twice")))
    assert(CompareRuns.compare(List(a, run("b", "review-article", "x2020", Nil)), billing).left.exists(_.contains("one pipeline")))
    assert(CompareRuns.compare(List(a, run("b", "enrich-source", "y2021", Nil)), billing).left.exists(_.contains("one target")))

  test("the step union keeps each run's order; a run with more windows or rounds slots its extra steps in"):
    assertEquals(CompareRuns.unionInOrder(List(List("load", "classify", "findings-1", "geocode"),
      List("load", "classify", "findings-1", "findings-2", "geocode"))),
      List("load", "classify", "findings-1", "findings-2", "geocode"))
    assertEquals(CompareRuns.unionInOrder(List(List("a", "c"), List("b", "c", "d"))), List("b", "a", "c", "d"))

  test("a step's attempts are summed: models tried, what served them, how each was paid, tokens, time, failures"):
    val s = CompareRuns.stepSide(List(
      step(3, "classify", "failed", model = Some("claude-code:haiku"), actual = Some("claude-haiku-5-5"), usage = Usage(10, 2), error = Some("bad")),
      step(3, "classify", model = Some("anthropic:x"), actual = Some("x-2026"), usage = Usage(20, 4, 5), cost = 0.01, attempt = 2)), billing)
    assertEquals(s, RunCompare.StepSide(List("claude-code:haiku", "anthropic:x"), List("claude-haiku-5-5", "x-2026"),
      List("plan pool: claude-plan", "price: 1/5 USD per million tokens"), Usage(30, 6, 5), 0.01, 6, 2, 1))
    assertEquals(CompareRuns.seconds(step(1, "x", started = "bad")), 0L)

  test("V072: a step's first call is its first attempt's that recorded one, its turns every recorded attempt's; both shown"):
    val before = step(3, "classify", "failed", model = Some("claude-code:haiku"), error = Some("bad"))
    val a1 = step(3, "classify", "failed", model = Some("claude-code:haiku"), error = Some("bad"), attempt = 2)
      .copy(firstCallTokens = Some(6200), turns = Some(7))
    val a2 = step(3, "classify", model = Some("claude-code:haiku"), attempt = 3).copy(firstCallTokens = Some(6900), turns = Some(5))
    val s = CompareRuns.stepSide(List(before, a1, a2), billing)
    assertEquals((s.firstCallTokens, s.turns), (Some(6200L), Some(12)))
    val old = CompareRuns.stepSide(List(before), billing)
    assertEquals((old.firstCallTokens, old.turns), (None, None), "a run before V072 says nothing")
    val now   = run("a", "enrich-source", "x2020", List(a2))
    val prior = run("b", "enrich-source", "x2020", List(before.copy(status = "done", error = None)))
    val md = AgentsMarkdown.compare(CompareRuns.compare(List(now, prior), billing).toOption.get)
    val row = md.linesIterator.find(_.startsWith("| classify")).get
    val cells = row.split(" \\| ").toList
    assert(cells(1).contains("first call 6900 tok · 5 turns"), row)
    assert(!cells(2).contains("first call"), "the old run's cell has no reading")
    assertEquals(AgentsMarkdown.callShape(Some(4100), Some(1)), "first call 4100 tok · 1 turn")
    assertEquals(AgentsMarkdown.callShape(None, Some(3)), "3 turns")
    assertEquals(AgentsMarkdown.callShape(None, None), "")

  // ── enrich-source ──────────────────────────────────────────────────────────────────────────────

  private def vf(claim: String) = VerifiedFinding(claim, s"quote of $claim", 1.0, None, None, None, None, Nil, "high")

  test("the quote check reads accepted answers (their own counts) and rejected ones (the verifier's reasons)"):
    val rejected = Findings(List(FindingProposal("a", "q1", None, None, None, None, None, Nil, "high"),
      FindingProposal("b", "q2", None, None, None, None, None, Nil, "high"))).asJson.noSpaces
    val steps = List(
      step(4, "findings-1", "failed", output = Some(rejected), model = Some("m"),
        error = Some("finding 2 (b): the quote is not in the text (best match 0.40) — copy it exactly")),
      step(4, "findings-1", output = Some(VerifiedFindings(List(vf("a")), Some(1), Some(0)).asJson.noSpaces), model = Some("m"), attempt = 2),
      step(5, "findings-2", output = Some(VerifiedFindings(List(vf("c")), Some(3), Some(2)).asJson.noSpaces), model = Some("m")),
      // a checkpoint older than the counts: what it kept is what it checked
      step(6, "findings-3", output = Some(VerifiedFindings(List(vf("d"), vf("e"))).asJson.noSpaces), model = Some("m")),
      // an answer that was not JSON checked nothing
      step(7, "findings-4", "failed", output = None, model = Some("m"), error = Some("the answer is not the JSON")))
    assertEquals(CompareRuns.quoteCheck(steps), (2 + 1 + 3 + 2, 1 + 1 + 1 + 2))

  test("enrich-source: findings, places, dates, period, text lengths, and the claims only one run made"):
    def enrich(id: String, claims: List[FindingInput], choices: List[GeocodeChoice], notes: String) = run(id, "enrich-source", "x2020", List(
      step(1, "classify", model = Some("anthropic:x"), output = Some(Classified("A summary.", Nil, Nil, Nil, Some(Period(-1600, -1200)), None,
        SourceStatus.Current, "").asJson.noSpaces)),
      step(2, "findings-1", model = Some("anthropic:x"), output = Some(VerifiedFindings(claims.map(c => vf(c.claim)), Some(claims.size), Some(0)).asJson.noSpaces)),
      step(3, "geocode", output = Some(Geocoded(claims, choices).asJson.noSpaces)),
      step(4, "notes", model = Some("anthropic:x"), output = Some(notes.asJson.noSpaces))))
    val a = enrich("a", List(FindingInput("Tin came by sea.", periodStart = Some(-1300)), FindingInput("Copper came from Cyprus.")),
      List(GeocodeChoice("Tin came by sea.", "Uluburun", Some("p1"), Some("Uluburun"), None),
        GeocodeChoice("Copper came from Cyprus.", "Atlantis", None, None, None)), "Notes [[x2020]].")
    val b = enrich("b", List(FindingInput("tin  came by SEA."), FindingInput("Tin was rare.")), Nil, "N.")
    val cmp = CompareRuns.compare(List(a, b), billing).fold(fail(_), identity)
    assertEquals(cmp.enrich, List(
      RunCompare.EnrichSide(2, 2, 2, 2, 1, 1, Some(-1600), Some(-1200), 10, 16, List("Copper came from Cyprus.")),
      RunCompare.EnrichSide(2, 2, 2, 0, 0, 0, Some(-1600), Some(-1200), 10, 2, List("Tin was rare."))),
      "the same claim worded with other case and spacing is not unique")
    assertEquals(cmp.review, Nil)
    assertEquals(cmp.steps.map(_.name), List("classify", "findings-1", "geocode", "notes"))
    assertEquals(cmp.runs.map(_.seconds), List(12L, 12L))

  test("a run that did not finish is compared with a note saying so"):
    val a = run("aaaaaaaa1", "enrich-source", "x2020", List(step(1, "load")))
    val b = run("bbbbbbbb2", "enrich-source", "x2020", List(step(1, "load")), status = "paused")
    val cmp = CompareRuns.compare(List(a, b), billing).fold(fail(_), identity)
    assertEquals(cmp.notes, List("run bbbbbbbb is paused, not done: its outputs are what it had so far"))
    assertEquals(cmp.steps, List(RunCompare.StepRow("load", List(Some(RunCompare.StepSide(Nil, Nil, Nil, Usage.Zero, 0, 3, 1, 0)),
      Some(RunCompare.StepSide(Nil, Nil, Nil, Usage.Zero, 0, 3, 1, 0))))))

  // ── review-article ─────────────────────────────────────────────────────────────────────────────

  private def f(id: String, sev: Severity, where: String, problem: String, quote: String = "", kind: String = "term") =
    Finding(id, Reviewer.parse(if id.startsWith("R") then "rules" else if id.startsWith("S") then "skeptic" else "cold-reader").get,
      sev, kind, where, quote, problem, "")

  test("same major: the same where and a similar problem, or the same quoted passage"):
    assert(CompareRuns.sameMajor(f("R1", Severity.Major, "Intro", "tin is not defined"), f("R2", Severity.Major, "intro", "Tin is not defined at all")))
    assert(!CompareRuns.sameMajor(f("R1", Severity.Major, "Intro", "tin is not defined"), f("R2", Severity.Major, "Dates", "tin is not defined")))
    assert(!CompareRuns.sameMajor(f("R1", Severity.Major, "Intro", "tin is not defined"), f("R2", Severity.Major, "Intro", "the date is wrong")))
    assert(CompareRuns.sameMajor(f("S1", Severity.Major, "A", "x", "Its cargo dates to 1320"), f("R4", Severity.Major, "B", "y", "its cargo dates to 1320")))
    assertEquals(CompareRuns.similarity("", ""), 0.0)
    // one-to-one: a finding is matched once
    val pairs = CompareRuns.overlap(List(f("R1", Severity.Major, "I", "tin undefined here"), f("R2", Severity.Major, "I", "tin undefined here")),
      List(f("R9", Severity.Major, "I", "tin undefined here")))
    assertEquals(pairs.map((a, b) => (a.id, b.id)), List(("R1", "R9")))

  test("review-article: last verdict and rounds, round 1 per reviewer and by kind, the majors two runs share"):
    def review(id: String, rounds: List[Merged], reports: List[Report]) = run(id, "review-article", "tin", rounds.flatMap { m =>
      List(step(m.round * 10, s"merge-${m.round}", output = Some(m.asJson.noSpaces)))
    } ++ reports.map(r => step(5, s"${r.reviewer.wire}-1", model = Some("anthropic:x"), output = Some(r.asJson.noSpaces))) ++
      List(step(4, "mechanical-1", output = Some(List(f("M1", Severity.Minor, "Body", "no Disagreements", kind = "structure")).asJson.noSpaces))))
    val r1 = f("R1", Severity.Major, "Intro", "tin is not defined")
    val s1 = f("S1", Severity.Major, "Dates", "the date is wrong", kind = "date")
    val c1 = f("C1", Severity.Minor, "Intro", "what is bronze")
    val a = review("a", List(Merged(1, Verdict.Major, List(r1, s1, c1), None, Nil, Nil), Merged(2, Verdict.Pass, Nil, None, Nil, Nil)),
      List(Report(Reviewer.Rules, Verdict.Major, List(r1)), Report(Reviewer.ColdReader, Verdict.Minor, List(c1)),
        Report(Reviewer.Skeptic, Verdict.Major, List(s1))))
    val r1b = f("R1", Severity.Major, "intro", "Tin is never defined, tin is not defined")
    val b = review("b", List(Merged(1, Verdict.Major, List(r1b), None, Nil, Nil)), List(Report(Reviewer.Rules, Verdict.Major, List(r1b))))
    val cmp = CompareRuns.compare(List(a, b), billing).fold(fail(_), identity)
    assertEquals(cmp.review.map(r => (r.verdict, r.rounds)), List((Some("PASS"), 2), (Some("MAJOR"), 1)))
    assertEquals(cmp.review.head.reviewers.map(r => (r.reviewer, r.verdict, r.majors, r.minors)), List(
      ("rules", Some("MAJOR"), 1, 0), ("cold-reader", Some("MINOR"), 0, 1), ("skeptic", Some("MAJOR"), 1, 0), ("mechanical", None, 0, 1)))
    assertEquals(cmp.review.head.kinds, List(RunCompare.KindCount("term", 2), RunCompare.KindCount("date", 1)))
    assertEquals(cmp.review.head.majors, List("R1 major (Intro): tin is not defined", "S1 major (Dates): the date is wrong"))
    assertEquals(cmp.overlaps, List(RunCompare.MajorOverlap("a", "b", 2, 1, 1, List("R1 ↔ R1"))))
    assertEquals(cmp.enrich, Nil)
    val md = AgentsMarkdown.compare(cmp)
    assert(md.contains("| verdict (last round) | PASS | MAJOR |") && md.contains("| rounds | 2 | 1 |") &&
      md.contains("| kind: date (round 1) | 1 | 0 |") && md.contains("- a × b: 1 shared of 2 and 1 (R1 ↔ R1)"), md)
    assert(md.contains("| merge-1 | code · 0/0 · 3 s | code · 0/0 · 3 s |"), md)
