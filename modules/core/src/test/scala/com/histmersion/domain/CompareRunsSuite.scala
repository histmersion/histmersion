package com.histmersion.domain

import com.histmersion.config.*
import com.histmersion.api.given
import com.histmersion.config.given

import com.histmersion.domain.CompareRuns.{ModelChoice, Run, RunHead, Step, Tokens}
import com.histmersion.domain.EnrichSource.*
import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import com.histmersion.domain.automation.agents.OutputComparison
import com.histmersion.domain.library.workflows.EnrichComparison
import com.histmersion.domain.research.workflows.ReviewComparison
import io.circe.syntax.*
import com.histmersion.api.catalogue.Period
import com.histmersion.api.library.FindingInput
import com.histmersion.domain.library.Source

/** `agent compare` (#992), the pure half: what can be compared, the step union, the verifier's quote check read back
  * from accepted AND rejected answers, the claims only one run made, and the major findings two review runs share.
  */
class CompareRunsSuite extends munit.FunSuite:

  private def step(
      seq: Int,
      name: String,
      status: String = "done",
      output: Option[String] = None,
      model: Option[String] = None,
      actual: Option[String] = None,
      error: Option[String] = None,
      usage: Tokens = Tokens.Zero,
      cost: Double = 0,
      started: String = "2026-10-08T10:00:00Z",
      finished: String = "2026-10-08T10:00:03Z",
      attempt: Int = 1
  ): Step =
    Step(name, status, model, actual, usage, cost, started, finished, None, None, output, error)

  private def run(
      id: String,
      pipeline: String,
      target: String,
      steps: List[Step],
      status: String = "done",
      dry: Boolean = false,
      overrides: List[ModelChoice] = Nil
  ): Run =
    Run(RunHead(id, pipeline, target, status, dry, None, overrides), steps)

  private val billing: String => Option[String] = {
    case "claude-code:haiku" => Some("plan pool: claude-plan")
    case "anthropic:x"       => Some("price: 1/5 USD per million tokens")
    case _                   => None
  }

  /** The runs compared as `agent compare` does: enrich-source's outputs by the library's comparison. */
  private def compare(runs: List[Run]) =
    CompareRuns.compare(
      runs,
      billing,
      runs.headOption.map(_.run.pipeline) match
        case Some("enrich-source")  => EnrichComparison
        case Some("review-article") => ReviewComparison
        case _                      => OutputComparison.none
    )

  test("refused: fewer than two runs, a run twice, two pipelines, two targets"):
    val a = run("a", "enrich-source", "x2020", Nil)
    assert(compare(List(a)).left.exists(_.contains("at least two")))
    assert(compare(List(a, a)).left.exists(_.contains("twice")))
    assert(
      compare(List(a, run("b", "review-article", "x2020", Nil))).left
        .exists(_.contains("one pipeline"))
    )
    assert(
      compare(List(a, run("b", "enrich-source", "y2021", Nil))).left
        .exists(_.contains("one target"))
    )

  test("the step union keeps each run's order; a run with more windows or rounds slots its extra steps in"):
    assertEquals(
      CompareRuns.unionInOrder(
        List(
          List("load", "classify", "findings-1", "geocode"),
          List("load", "classify", "findings-1", "findings-2", "geocode")
        )
      ),
      List("load", "classify", "findings-1", "findings-2", "geocode")
    )
    assertEquals(CompareRuns.unionInOrder(List(List("a", "c"), List("b", "c", "d"))), List("b", "a", "c", "d"))

  test("a step's attempts are summed: models tried, what served them, how each was paid, tokens, time, failures"):
    val s = CompareRuns.stepSide(
      List(
        step(
          3,
          "classify",
          "failed",
          model = Some("claude-code:haiku"),
          actual = Some("claude-haiku-5-5"),
          usage = Tokens(10, 2),
          error = Some("bad")
        ),
        step(
          3,
          "classify",
          model = Some("anthropic:x"),
          actual = Some("x-2026"),
          usage = Tokens(20, 4, 5),
          cost = 0.01,
          attempt = 2
        )
      ),
      billing
    )
    assertEquals(
      s,
      CompareRuns.StepSide(
        List("claude-code:haiku", "anthropic:x"),
        List("claude-haiku-5-5", "x-2026"),
        List("plan pool: claude-plan", "price: 1/5 USD per million tokens"),
        Tokens(30, 6, 5),
        0.01,
        6,
        2,
        1
      )
    )
    assertEquals(CompareRuns.seconds(step(1, "x", started = "bad")), 0L)

  test("V072: a step's first call is its first attempt's that recorded one, its turns every recorded attempt's"):
    val before = step(3, "classify", "failed", model = Some("claude-code:haiku"), error = Some("bad"))
    val a1 = step(3, "classify", "failed", model = Some("claude-code:haiku"), error = Some("bad"), attempt = 2)
      .copy(firstCallTokens = Some(6200), turns = Some(7))
    val a2 = step(3, "classify", model = Some("claude-code:haiku"), attempt = 3)
      .copy(firstCallTokens = Some(6900), turns = Some(5))
    val s = CompareRuns.stepSide(List(before, a1, a2), billing)
    assertEquals((s.firstCallTokens, s.turns), (Some(6200L), Some(12)))
    val old = CompareRuns.stepSide(List(before), billing)
    assertEquals((old.firstCallTokens, old.turns), (None, None), "a run before V072 says nothing")

  // ── enrich-source ──────────────────────────────────────────────────────────────────────────────

  private def vf(claim: String) = VerifiedFinding(claim, s"quote of $claim", 1.0, None, None, None, None, Nil, "high")

  test("the quote check reads accepted answers (their own counts) and rejected ones (the verifier's reasons)"):
    val rejected = Findings(
      List(
        FindingProposal("a", "q1", None, None, None, None, None, Nil, "high"),
        FindingProposal("b", "q2", None, None, None, None, None, Nil, "high")
      )
    ).asJson.noSpaces
    val steps = List(
      step(
        4,
        "findings-1",
        "failed",
        output = Some(rejected),
        model = Some("m"),
        error = Some("finding 2 (b): the quote is not in the text (best match 0.40) — copy it exactly")
      ),
      step(
        4,
        "findings-1",
        output = Some(VerifiedFindings(List(vf("a")), Some(1), Some(0)).asJson.noSpaces),
        model = Some("m"),
        attempt = 2
      ),
      step(
        5,
        "findings-2",
        output = Some(VerifiedFindings(List(vf("c")), Some(3), Some(2)).asJson.noSpaces),
        model = Some("m")
      ),
      // a checkpoint older than the counts: what it kept is what it checked
      step(6, "findings-3", output = Some(VerifiedFindings(List(vf("d"), vf("e"))).asJson.noSpaces), model = Some("m")),
      // an answer that was not JSON checked nothing
      step(7, "findings-4", "failed", output = None, model = Some("m"), error = Some("the answer is not the JSON"))
    )
    assertEquals(EnrichComparison.quoteCheck(steps), (2 + 1 + 3 + 2, 1 + 1 + 1 + 2))

  test("enrich-source: findings, places, dates, period, text lengths, and the claims only one run made"):
    def enrich(id: String, claims: List[FindingInput], choices: List[GeocodeChoice], notes: String) = run(
      id,
      "enrich-source",
      "x2020",
      List(
        step(
          1,
          "classify",
          model = Some("anthropic:x"),
          output = Some(
            Classified(
              "A summary.",
              Nil,
              Nil,
              Nil,
              Some(Period(-1600, -1200)),
              None,
              Source.Status.Current,
              ""
            ).asJson.noSpaces
          )
        ),
        step(
          2,
          "findings-1",
          model = Some("anthropic:x"),
          output = Some(VerifiedFindings(claims.map(c => vf(c.claim)), Some(claims.size), Some(0)).asJson.noSpaces)
        ),
        step(3, "geocode", output = Some(Geocoded(claims, choices).asJson.noSpaces)),
        step(4, "notes", model = Some("anthropic:x"), output = Some(notes.asJson.noSpaces))
      )
    )
    val a = enrich(
      "a",
      List(FindingInput("Tin came by sea.", periodStart = Some(-1300)), FindingInput("Copper came from Cyprus.")),
      List(
        GeocodeChoice("Tin came by sea.", "Uluburun", Some("p1"), Some("Uluburun"), None),
        GeocodeChoice("Copper came from Cyprus.", "Atlantis", None, None, None)
      ),
      "Notes [[x2020]]."
    )
    val b = enrich("b", List(FindingInput("tin  came by SEA."), FindingInput("Tin was rare.")), Nil, "N.")
    val cmp = compare(List(a, b)).fold(fail(_), identity)
    assertEquals(
      cmp.enrich,
      List(
        CompareRuns.EnrichSide(2, 2, 2, 2, 1, 1, Some(-1600), Some(-1200), 10, 16, List("Copper came from Cyprus.")),
        CompareRuns.EnrichSide(2, 2, 2, 0, 0, 0, Some(-1600), Some(-1200), 10, 2, List("Tin was rare."))
      ),
      "the same claim worded with other case and spacing is not unique"
    )
    assertEquals(cmp.review, Nil)
    assertEquals(cmp.steps.map(_.name), List("classify", "findings-1", "geocode", "notes"))
    assertEquals(cmp.runs.map(_.seconds), List(12L, 12L))

  test("a run that did not finish is compared with a note saying so"):
    val a = run("aaaaaaaa1", "enrich-source", "x2020", List(step(1, "load")))
    val b = run("bbbbbbbb2", "enrich-source", "x2020", List(step(1, "load")), status = "paused")
    val cmp = compare(List(a, b)).fold(fail(_), identity)
    assertEquals(cmp.notes, List("run bbbbbbbb is paused, not done: its outputs are what it had so far"))
    assertEquals(
      cmp.steps,
      List(
        CompareRuns.StepRow(
          "load",
          List(
            Some(CompareRuns.StepSide(Nil, Nil, Nil, Tokens.Zero, 0, 3, 1, 0)),
            Some(CompareRuns.StepSide(Nil, Nil, Nil, Tokens.Zero, 0, 3, 1, 0))
          )
        )
      )
    )

  // ── review-article ─────────────────────────────────────────────────────────────────────────────

  private def f(id: String, sev: Severity, where: String, problem: String, quote: String = "", kind: String = "term") =
    Finding(
      id,
      Reviewer
        .parse(if id.startsWith("R") then "rules" else if id.startsWith("S") then "skeptic" else "cold-reader")
        .get,
      sev,
      kind,
      where,
      quote,
      problem,
      ""
    )

  test("same major: the same where and a similar problem, or the same quoted passage"):
    assert(
      ReviewComparison.sameMajor(
        f("R1", Severity.Major, "Intro", "tin is not defined"),
        f("R2", Severity.Major, "intro", "Tin is not defined at all")
      )
    )
    assert(
      !ReviewComparison.sameMajor(
        f("R1", Severity.Major, "Intro", "tin is not defined"),
        f("R2", Severity.Major, "Dates", "tin is not defined")
      )
    )
    assert(
      !ReviewComparison.sameMajor(
        f("R1", Severity.Major, "Intro", "tin is not defined"),
        f("R2", Severity.Major, "Intro", "the date is wrong")
      )
    )
    assert(
      ReviewComparison.sameMajor(
        f("S1", Severity.Major, "A", "x", "Its cargo dates to 1320"),
        f("R4", Severity.Major, "B", "y", "its cargo dates to 1320")
      )
    )
    assertEquals(CompareRuns.similarity("", ""), 0.0)
    // one-to-one: a finding is matched once
    val pairs = ReviewComparison.overlap(
      List(f("R1", Severity.Major, "I", "tin undefined here"), f("R2", Severity.Major, "I", "tin undefined here")),
      List(f("R9", Severity.Major, "I", "tin undefined here"))
    )
    assertEquals(pairs.map((a, b) => (a.id, b.id)), List(("R1", "R9")))

  test("review-article: last verdict and rounds, round 1 per reviewer and by kind, the majors two runs share"):
    def review(id: String, rounds: List[Merged], reports: List[Report]) = run(
      id,
      "review-article",
      "tin",
      rounds.flatMap { m =>
        List(step(m.round * 10, s"merge-${m.round}", output = Some(m.asJson.noSpaces)))
      } ++ reports.map(r =>
        step(5, s"${r.reviewer.wire}-1", model = Some("anthropic:x"), output = Some(r.asJson.noSpaces))
      ) ++
        List(
          step(
            4,
            "mechanical-1",
            output = Some(List(f("M1", Severity.Minor, "Body", "no Disagreements", kind = "structure")).asJson.noSpaces)
          )
        )
    )
    val r1 = f("R1", Severity.Major, "Intro", "tin is not defined")
    val s1 = f("S1", Severity.Major, "Dates", "the date is wrong", kind = "date")
    val c1 = f("C1", Severity.Minor, "Intro", "what is bronze")
    val a = review(
      "a",
      List(Merged(1, Verdict.Major, List(r1, s1, c1), None, Nil, Nil), Merged(2, Verdict.Pass, Nil, None, Nil, Nil)),
      List(
        Report(Reviewer.Rules, Verdict.Major, List(r1)),
        Report(Reviewer.ColdReader, Verdict.Minor, List(c1)),
        Report(Reviewer.Skeptic, Verdict.Major, List(s1))
      )
    )
    val r1b = f("R1", Severity.Major, "intro", "Tin is never defined, tin is not defined")
    val b = review(
      "b",
      List(Merged(1, Verdict.Major, List(r1b), None, Nil, Nil)),
      List(Report(Reviewer.Rules, Verdict.Major, List(r1b)))
    )
    val cmp = compare(List(a, b)).fold(fail(_), identity)
    assertEquals(cmp.review.map(r => (r.verdict, r.rounds)), List((Some("PASS"), 2), (Some("MAJOR"), 1)))
    assertEquals(
      cmp.review.head.reviewers.map(r => (r.reviewer, r.verdict, r.majors, r.minors)),
      List(
        ("rules", Some("MAJOR"), 1, 0),
        ("cold-reader", Some("MINOR"), 0, 1),
        ("skeptic", Some("MAJOR"), 1, 0),
        ("mechanical", None, 0, 1)
      )
    )
    assertEquals(cmp.review.head.kinds, List(CompareRuns.KindCount("term", 2), CompareRuns.KindCount("date", 1)))
    assertEquals(
      cmp.review.head.majors,
      List("R1 major (Intro): tin is not defined", "S1 major (Dates): the date is wrong")
    )
    assertEquals(cmp.overlaps, List(CompareRuns.MajorOverlap("a", "b", 2, 1, 1, List("R1 ↔ R1"))))
    assertEquals(cmp.enrich, Nil)
