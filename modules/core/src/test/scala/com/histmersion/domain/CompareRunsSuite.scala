package com.histmersion.domain

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

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
import com.histmersion.api.library.Source

/** `agent compare` (#992), the pure half: what can be compared, the step union, the verifier's quote check read back
  * from accepted AND rejected answers, the claims only one run made, and the major findings two review runs share.
  */
class CompareRunsSuite extends munit.FunSuite:

  private def step(
      seq: Int,
      name: Step.Name,
      status: Step.Status = Step.Status("done"),
      output: Option[Step.Output] = None,
      model: Option[Step.Model] = None,
      actual: Option[Step.ActualModel] = None,
      error: Option[Step.Error] = None,
      usage: Tokens = Tokens.Zero,
      cost: Step.CostUsd = Step.CostUsd(0.0),
      started: Step.StartedAt = Step.StartedAt("2026-10-08T10:00:00Z"),
      finished: Step.FinishedAt = Step.FinishedAt("2026-10-08T10:00:03Z"),
      attempt: Int = 1
  ): Step =
    Step(name, status, model, actual, usage, cost, started, finished, None, None, output, error)

  private def run(
      id: RunHead.Id,
      pipeline: RunHead.Pipeline,
      target: RunHead.Target,
      steps: List[Step],
      status: RunHead.Status = RunHead.Status("done"),
      dry: RunHead.DryRun = RunHead.DryRun(false),
      overrides: List[ModelChoice] = Nil
  ): Run =
    Run(RunHead(id, pipeline, target, status, dry, None, overrides), steps)

  private val billing: Step.Model => Option[CompareRuns.StepSide.Billing] = model =>
    model.value match
      case "claude-code:haiku" => Some(CompareRuns.StepSide.Billing("plan pool: claude-plan"))
      case "anthropic:x"       => Some(CompareRuns.StepSide.Billing("price: 1/5 USD per million tokens"))
      case _                   => None


  /** The runs compared as `agent compare` does: enrich-source's outputs by the library's comparison. */
  private def compare(runs: List[Run]) =
    CompareRuns.compare(
      runs,
      billing,
      runs.headOption.map(_.run.pipeline.value) match
        case Some("enrich-source")  => EnrichComparison
        case Some("review-article") => ReviewComparison
        case _                      => OutputComparison.none
    )

  test("refused: fewer than two runs, a run twice, two pipelines, two targets"):
    val a = run(RunHead.Id("a"), RunHead.Pipeline("enrich-source"), RunHead.Target("x2020"), Nil)
    assert(compare(List(a)).left.exists(_.value.contains("at least two")))
    assert(compare(List(a, a)).left.exists(_.value.contains("twice")))
    assert(
      compare(List(a, run(RunHead.Id("b"), RunHead.Pipeline("review-article"), RunHead.Target("x2020"), Nil))).left
        .exists(_.value.contains("one pipeline"))
    )
    assert(
      compare(List(a, run(RunHead.Id("b"), RunHead.Pipeline("enrich-source"), RunHead.Target("y2021"), Nil))).left
        .exists(_.value.contains("one target"))
    )

  test("the step union keeps each run's order; a run with more windows or rounds slots its extra steps in"):
    assertEquals(
      CompareRuns.unionInOrder(
        List(
          List(com.histmersion.domain.CompareRuns.Step.Name("load"), com.histmersion.domain.CompareRuns.Step.Name("classify"), com.histmersion.domain.CompareRuns.Step.Name("findings-1"), com.histmersion.domain.CompareRuns.Step.Name("geocode")),
          List(com.histmersion.domain.CompareRuns.Step.Name("load"), com.histmersion.domain.CompareRuns.Step.Name("classify"), com.histmersion.domain.CompareRuns.Step.Name("findings-1"), com.histmersion.domain.CompareRuns.Step.Name("findings-2"), com.histmersion.domain.CompareRuns.Step.Name("geocode"))
        )
      ),
      List("load", "classify", "findings-1", "findings-2", "geocode")
    )
    assertEquals(CompareRuns.unionInOrder(List(List(com.histmersion.domain.CompareRuns.Step.Name("a"), com.histmersion.domain.CompareRuns.Step.Name("c")), List(com.histmersion.domain.CompareRuns.Step.Name("b"), com.histmersion.domain.CompareRuns.Step.Name("c"), com.histmersion.domain.CompareRuns.Step.Name("d")))), List("b", "a", "c", "d"))

  test("a step's attempts are summed: models tried, what served them, how each was paid, tokens, time, failures"):
    val s = CompareRuns.stepSide(
      List(
        step(
          3,
          Step.Name("classify"),
          Step.Status("failed"),
          model = Some(Step.Model("claude-code:haiku")),
          actual = Some(Step.ActualModel("claude-haiku-5-5")),
          usage = Tokens(com.histmersion.domain.CompareRuns.Tokens.Input(10), com.histmersion.domain.CompareRuns.Tokens.Output(2)),
          error = Some(Step.Error("bad"))
        ),
        step(
          3,
          Step.Name("classify"),
          model = Some(Step.Model("anthropic:x")),
          actual = Some(Step.ActualModel("x-2026")),
          usage = Tokens(com.histmersion.domain.CompareRuns.Tokens.Input(20), com.histmersion.domain.CompareRuns.Tokens.Output(4), com.histmersion.domain.CompareRuns.Tokens.CacheRead(5)),
          cost = Step.CostUsd(0.01),
          attempt = 2
        )
      ),
      billing
    )
    assertEquals(
      s,
      CompareRuns.StepSide(
        List(com.histmersion.domain.CompareRuns.Step.Model("claude-code:haiku"), com.histmersion.domain.CompareRuns.Step.Model("anthropic:x")),
        List(com.histmersion.domain.CompareRuns.Step.ActualModel("claude-haiku-5-5"), com.histmersion.domain.CompareRuns.Step.ActualModel("x-2026")),
        List(com.histmersion.domain.CompareRuns.StepSide.Billing("plan pool: claude-plan"), com.histmersion.domain.CompareRuns.StepSide.Billing("price: 1/5 USD per million tokens")),
        Tokens(com.histmersion.domain.CompareRuns.Tokens.Input(30), com.histmersion.domain.CompareRuns.Tokens.Output(6), com.histmersion.domain.CompareRuns.Tokens.CacheRead(5)),
        CompareRuns.StepSide.CostUsd(0.01),
        com.histmersion.domain.CompareRuns.StepSide.Seconds(6),
        com.histmersion.domain.CompareRuns.StepSide.Attempts(2),
        com.histmersion.domain.CompareRuns.StepSide.Failures(1)
      )
    )
    assertEquals(CompareRuns.seconds(step(1, Step.Name("x"), started = Step.StartedAt("bad"))), 0L)

  test("V072: a step's first call is its first attempt's that recorded one, its turns every recorded attempt's"):
    val before = step(3, Step.Name("classify"), Step.Status("failed"), model = Some(Step.Model("claude-code:haiku")), error = Some(Step.Error("bad")))
    val a1 = step(3, Step.Name("classify"), Step.Status("failed"), model = Some(Step.Model("claude-code:haiku")), error = Some(Step.Error("bad")), attempt = 2)
      .modify(_.firstCallTokens).setTo(Some(Step.FirstCallTokens(6200L))).modify(_.turns).setTo(Some(Step.Turns(7)))
    val a2 = step(3, Step.Name("classify"), model = Some(Step.Model("claude-code:haiku")), attempt = 3)
      .modify(_.firstCallTokens).setTo(Some(Step.FirstCallTokens(6900L))).modify(_.turns).setTo(Some(Step.Turns(5)))
    val s = CompareRuns.stepSide(List(before, a1, a2), billing)
    assertEquals((s.firstCallTokens, s.turns), (Some(6200L), Some(12)))
    val old = CompareRuns.stepSide(List(before), billing)
    assertEquals((old.firstCallTokens, old.turns), (None, None), "a run before V072 says nothing")

  // ── enrich-source ──────────────────────────────────────────────────────────────────────────────

  private def vf(claim: VerifiedFinding.Claim) = VerifiedFinding(claim, VerifiedFinding.Quote(s"quote of ${claim.value}"), AgentVerify.Ratio(1.0), None, None, None, None, Nil, com.histmersion.domain.EnrichSource.VerifiedFinding.Confidence("high"))

  test("the quote check reads accepted answers (their own counts) and rejected ones (the verifier's reasons)"):
    val rejected = Findings(
      List(
        FindingProposal(com.histmersion.domain.EnrichSource.FindingProposal.Claim("a"), com.histmersion.domain.AgentProposals.Proposal.Quote("q1"), None, None, None, None, None, Nil, com.histmersion.domain.library.Confidence.Wire("high")),
        FindingProposal(com.histmersion.domain.EnrichSource.FindingProposal.Claim("b"), com.histmersion.domain.AgentProposals.Proposal.Quote("q2"), None, None, None, None, None, Nil, com.histmersion.domain.library.Confidence.Wire("high"))
      )
    ).asJson.noSpaces
    val steps = List(
      step(
        4,
        Step.Name("findings-1"),
        Step.Status("failed"),
        output = Some(Step.Output(rejected)),
        model = Some(Step.Model("m")),
        error = Some(Step.Error("finding 2 (b): the quote is not in the text (best match 0.40) — copy it exactly"))
      ),
      step(
        4,
        Step.Name("findings-1"),
        output = Some(Step.Output(VerifiedFindings(List(vf(VerifiedFinding.Claim("a"))), Some(com.histmersion.domain.EnrichSource.VerifiedFindings.Proposed(1)), Some(com.histmersion.domain.EnrichSource.VerifiedFindings.QuoteFailed(0))).asJson.noSpaces)),
        model = Some(Step.Model("m")),
        attempt = 2
      ),
      step(
        5,
        Step.Name("findings-2"),
        output = Some(Step.Output(VerifiedFindings(List(vf(VerifiedFinding.Claim("c"))), Some(com.histmersion.domain.EnrichSource.VerifiedFindings.Proposed(3)), Some(com.histmersion.domain.EnrichSource.VerifiedFindings.QuoteFailed(2))).asJson.noSpaces)),
        model = Some(Step.Model("m"))
      ),
      // a checkpoint older than the counts: what it kept is what it checked
      step(6, Step.Name("findings-3"), output = Some(Step.Output(VerifiedFindings(List(vf(VerifiedFinding.Claim("d")), vf(VerifiedFinding.Claim("e"))), None, None).asJson.noSpaces)), model = Some(Step.Model("m"))),
      // an answer that was not JSON checked nothing
      step(7, Step.Name("findings-4"), Step.Status("failed"), output = None, model = Some(Step.Model("m")), error = Some(Step.Error("the answer is not the JSON")))
    )
    assertEquals(EnrichComparison.quoteCheck(steps), (2 + 1 + 3 + 2, 1 + 1 + 1 + 2))

  test("enrich-source: findings, places, dates, period, text lengths, and the claims only one run made"):
    def enrich(
        id: RunHead.Id,
        claims: List[com.histmersion.api.library.Finding.Input],
        choices: List[GeocodeChoice],
        notes: String
    ) = run(
      id,
      RunHead.Pipeline("enrich-source"),
      RunHead.Target("x2020"),
      List(
        step(
          1,
          Step.Name("classify"),
          model = Some(Step.Model("anthropic:x")),
          output = Some(Step.Output(
            Classified(
              com.histmersion.domain.EnrichSource.Classified.Summary("A summary."),
              Nil,
              Nil,
              Nil,
              Some(Period(com.histmersion.api.catalogue.Year(-1600), com.histmersion.api.catalogue.Year(-1200))),
              None,
              Source.Status.Current,
              com.histmersion.domain.EnrichSource.Classified.StatusEvidence("")
            ).asJson.noSpaces)
          )
        ),
        step(
          2,
          Step.Name("findings-1"),
          model = Some(Step.Model("anthropic:x")),
          output = Some(Step.Output(VerifiedFindings(claims.map(c => vf(VerifiedFinding.Claim(c.claim.value))), Some(com.histmersion.domain.EnrichSource.VerifiedFindings.Proposed(claims.size)), Some(com.histmersion.domain.EnrichSource.VerifiedFindings.QuoteFailed(0))).asJson.noSpaces))
        ),
        step(3, Step.Name("geocode"), output = Some(Step.Output(Geocoded(claims, choices).asJson.noSpaces))),
        step(4, Step.Name("notes"), model = Some(Step.Model("anthropic:x")), output = Some(Step.Output(notes.asJson.noSpaces)))
      )
    )
    val a = enrich(
      RunHead.Id("a"),
      List(
        com.histmersion.api.library.Finding.Input(com.histmersion.api.library.Finding.Claim("Tin came by sea."), periodStart = Some(com.histmersion.api.catalogue.Year(-1300))),
        com.histmersion.api.library.Finding.Input(com.histmersion.api.library.Finding.Claim("Copper came from Cyprus."))
      ),
      List(
        GeocodeChoice(com.histmersion.domain.EnrichSource.GeocodeChoice.Claim("Tin came by sea."), com.histmersion.domain.EnrichSource.GeocodeChoice.Asked("Uluburun"), Some(com.histmersion.domain.catalogue.Place.Id("p1")), Some(com.histmersion.domain.EnrichSource.GeocodeChoice.Label("Uluburun")), None),
        GeocodeChoice(com.histmersion.domain.EnrichSource.GeocodeChoice.Claim("Copper came from Cyprus."), com.histmersion.domain.EnrichSource.GeocodeChoice.Asked("Atlantis"), None, None, None)
      ),
      "Notes [[x2020]]."
    )
    val b = enrich(
      RunHead.Id("b"),
      List(
        com.histmersion.api.library.Finding.Input(com.histmersion.api.library.Finding.Claim("tin  came by SEA.")),
        com.histmersion.api.library.Finding.Input(com.histmersion.api.library.Finding.Claim("Tin was rare."))
      ),
      Nil,
      "N."
    )
    val cmp = compare(List(a, b)).fold(p => fail(p.value), identity)
    assertEquals(
      cmp.enrich,
      List(
        CompareRuns.EnrichSide(com.histmersion.domain.CompareRuns.EnrichSide.Findings(2), com.histmersion.domain.CompareRuns.EnrichSide.QuotesChecked(2), com.histmersion.domain.CompareRuns.EnrichSide.QuotesPassed(2), com.histmersion.domain.CompareRuns.EnrichSide.PlacesAsked(2), com.histmersion.domain.CompareRuns.EnrichSide.PlacesResolved(1), com.histmersion.domain.CompareRuns.EnrichSide.DatedFindings(1), Some(com.histmersion.api.catalogue.Year(-1600)), Some(com.histmersion.api.catalogue.Year(-1200)), com.histmersion.domain.CompareRuns.EnrichSide.SummaryChars(10), com.histmersion.domain.CompareRuns.EnrichSide.NotesChars(16), List(com.histmersion.domain.CompareRuns.EnrichSide.UniqueClaims("Copper came from Cyprus."))),
        CompareRuns.EnrichSide(com.histmersion.domain.CompareRuns.EnrichSide.Findings(2), com.histmersion.domain.CompareRuns.EnrichSide.QuotesChecked(2), com.histmersion.domain.CompareRuns.EnrichSide.QuotesPassed(2), com.histmersion.domain.CompareRuns.EnrichSide.PlacesAsked(0), com.histmersion.domain.CompareRuns.EnrichSide.PlacesResolved(0), com.histmersion.domain.CompareRuns.EnrichSide.DatedFindings(0), Some(com.histmersion.api.catalogue.Year(-1600)), Some(com.histmersion.api.catalogue.Year(-1200)), com.histmersion.domain.CompareRuns.EnrichSide.SummaryChars(10), com.histmersion.domain.CompareRuns.EnrichSide.NotesChars(2), List(com.histmersion.domain.CompareRuns.EnrichSide.UniqueClaims("Tin was rare.")))
      ),
      "the same claim worded with other case and spacing is not unique"
    )
    assertEquals(cmp.review, Nil)
    assertEquals(cmp.steps.map(_.name), List("classify", "findings-1", "geocode", "notes"))
    assertEquals(cmp.runs.map(_.seconds), List(12L, 12L))

  test("a run that did not finish is compared with a note saying so"):
    val a = run(RunHead.Id("aaaaaaaa1"), RunHead.Pipeline("enrich-source"), RunHead.Target("x2020"), List(step(1, Step.Name("load"))))
    val b = run(RunHead.Id("bbbbbbbb2"), RunHead.Pipeline("enrich-source"), RunHead.Target("x2020"), List(step(1, Step.Name("load"))), status = RunHead.Status("paused"))
    val cmp = compare(List(a, b)).fold(p => fail(p.value), identity)
    assertEquals(cmp.notes, List("run bbbbbbbb is paused, not done: its outputs are what it had so far"))
    assertEquals(
      cmp.steps,
      List(
        CompareRuns.StepRow(
          com.histmersion.domain.CompareRuns.StepRow.Name("load"),
          List(
            Some(CompareRuns.StepSide(Nil, Nil, Nil, Tokens.Zero, com.histmersion.domain.CompareRuns.StepSide.CostUsd(0), com.histmersion.domain.CompareRuns.StepSide.Seconds(3), com.histmersion.domain.CompareRuns.StepSide.Attempts(1), com.histmersion.domain.CompareRuns.StepSide.Failures(0))),
            Some(CompareRuns.StepSide(Nil, Nil, Nil, Tokens.Zero, com.histmersion.domain.CompareRuns.StepSide.CostUsd(0), com.histmersion.domain.CompareRuns.StepSide.Seconds(3), com.histmersion.domain.CompareRuns.StepSide.Attempts(1), com.histmersion.domain.CompareRuns.StepSide.Failures(0)))
          )
        )
      )
    )

  // ── review-article ─────────────────────────────────────────────────────────────────────────────

  private def f(id: Finding.Id, sev: Severity, where: Finding.Where, problem: Finding.Problem, quote: Finding.Quote = Finding.Quote(""), kind: Finding.Kind = Finding.Kind("term")) =
    Finding(
      id,
      Reviewer
        .parse(if id.value.startsWith("R") then com.histmersion.domain.ReviewArticle.Reviewer.Wire("rules") else if id.value.startsWith("S") then com.histmersion.domain.ReviewArticle.Reviewer.Wire("skeptic") else com.histmersion.domain.ReviewArticle.Reviewer.Wire("cold-reader"))
        .get,
      sev,
      kind,
      where,
      quote,
      problem,
      com.histmersion.domain.ReviewArticle.Finding.Fix("")
    )

  test("same major: the same where and a similar problem, or the same quoted passage"):
    assert(
      ReviewComparison.sameMajor(
        f(Finding.Id("R1"), Severity.Major, Finding.Where("Intro"), Finding.Problem("tin is not defined")),
        f(Finding.Id("R2"), Severity.Major, Finding.Where("intro"), Finding.Problem("Tin is not defined at all"))
      )
    )
    assert(
      !ReviewComparison.sameMajor(
        f(Finding.Id("R1"), Severity.Major, Finding.Where("Intro"), Finding.Problem("tin is not defined")),
        f(Finding.Id("R2"), Severity.Major, Finding.Where("Dates"), Finding.Problem("tin is not defined"))
      )
    )
    assert(
      !ReviewComparison.sameMajor(
        f(Finding.Id("R1"), Severity.Major, Finding.Where("Intro"), Finding.Problem("tin is not defined")),
        f(Finding.Id("R2"), Severity.Major, Finding.Where("Intro"), Finding.Problem("the date is wrong"))
      )
    )
    assert(
      ReviewComparison.sameMajor(
        f(Finding.Id("S1"), Severity.Major, Finding.Where("A"), Finding.Problem("x"), Finding.Quote("Its cargo dates to 1320")),
        f(Finding.Id("R4"), Severity.Major, Finding.Where("B"), Finding.Problem("y"), Finding.Quote("its cargo dates to 1320"))
      )
    )
    assertEquals(CompareRuns.similarity(com.histmersion.domain.CompareRuns.Text(""), com.histmersion.domain.CompareRuns.Text("")), 0.0)
    // one-to-one: a finding is matched once
    val pairs = ReviewComparison.overlap(
      List(f(Finding.Id("R1"), Severity.Major, Finding.Where("I"), Finding.Problem("tin undefined here")), f(Finding.Id("R2"), Severity.Major, Finding.Where("I"), Finding.Problem("tin undefined here"))),
      List(f(Finding.Id("R9"), Severity.Major, Finding.Where("I"), Finding.Problem("tin undefined here")))
    )
    assertEquals(pairs.map((a, b) => (a.id, b.id)), List(("R1", "R9")))

  test("review-article: last verdict and rounds, round 1 per reviewer and by kind, the majors two runs share"):
    def review(id: RunHead.Id, rounds: List[Merged], reports: List[Report]) = run(
      id,
      RunHead.Pipeline("review-article"),
      RunHead.Target("tin"),
      rounds.flatMap { m =>
        List(step(m.round.value * 10, Step.Name(s"merge-${m.round}"), output = Some(Step.Output(m.asJson.noSpaces))))
      } ++ reports.map(r =>
        step(5, Step.Name(s"${r.reviewer.wire}-1"), model = Some(Step.Model("anthropic:x")), output = Some(Step.Output(r.asJson.noSpaces)))
      ) ++
        List(
          step(
            4,
            Step.Name("mechanical-1"),
            output = Some(Step.Output(List(f(Finding.Id("M1"), Severity.Minor, Finding.Where("Body"), Finding.Problem("no Disagreements"), kind = Finding.Kind("structure"))).asJson.noSpaces))
          )
        )
    )
    val r1 = f(Finding.Id("R1"), Severity.Major, Finding.Where("Intro"), Finding.Problem("tin is not defined"))
    val s1 = f(Finding.Id("S1"), Severity.Major, Finding.Where("Dates"), Finding.Problem("the date is wrong"), kind = Finding.Kind("date"))
    val c1 = f(Finding.Id("C1"), Severity.Minor, Finding.Where("Intro"), Finding.Problem("what is bronze"))
    val a = review(
      RunHead.Id("a"),
      List(Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), Verdict.Major, List(r1, s1, c1), None, Nil, Nil), Merged(com.histmersion.domain.ReviewArticle.Merged.Round(2), Verdict.Pass, Nil, None, Nil, Nil)),
      List(
        Report(Reviewer.Rules, Verdict.Major, List(r1)),
        Report(Reviewer.ColdReader, Verdict.Minor, List(c1)),
        Report(Reviewer.Skeptic, Verdict.Major, List(s1))
      )
    )
    val r1b = f(Finding.Id("R1"), Severity.Major, Finding.Where("intro"), Finding.Problem("Tin is never defined, tin is not defined"))
    val b = review(
      RunHead.Id("b"),
      List(Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), Verdict.Major, List(r1b), None, Nil, Nil)),
      List(Report(Reviewer.Rules, Verdict.Major, List(r1b)))
    )
    val cmp = compare(List(a, b)).fold(p => fail(p.value), identity)
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
    assertEquals(cmp.review.head.kinds, List(CompareRuns.KindCount(com.histmersion.domain.CompareRuns.KindCount.Kind("term"), com.histmersion.domain.CompareRuns.KindCount.Count(2)), CompareRuns.KindCount(com.histmersion.domain.CompareRuns.KindCount.Kind("date"), com.histmersion.domain.CompareRuns.KindCount.Count(1))))
    assertEquals(
      cmp.review.head.majors,
      List("R1 major (Intro): tin is not defined", "S1 major (Dates): the date is wrong")
    )
    assertEquals(cmp.overlaps, List(CompareRuns.MajorOverlap(com.histmersion.domain.CompareRuns.MajorOverlap.A("a"), com.histmersion.domain.CompareRuns.MajorOverlap.B("b"), com.histmersion.domain.CompareRuns.MajorOverlap.AMajors(2), com.histmersion.domain.CompareRuns.MajorOverlap.BMajors(1), com.histmersion.domain.CompareRuns.MajorOverlap.Shared(1), List(com.histmersion.domain.CompareRuns.MajorOverlap.Pairs("R1 ↔ R1")))))
    assertEquals(cmp.enrich, Nil)
