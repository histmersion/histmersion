package com.histmersion
package server.agents
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.sentence

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.workflows.ReviewComparison

import com.histmersion.domain.automation.agents.{CompareRunsViews, OutputComparison}
import com.histmersion.domain.library.workflows.EnrichComparison
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.CompareRuns
import com.histmersion.domain.ReviewArticle.{Finding, Merged, Report, Reviewer, Severity, Verdict}
import com.histmersion.api.automation.{AgentsMarkdown, RunCompare}
import com.histmersion.api.automation.Agents

/** `agent compare` at the server's edge: recorded runs as the store reads them, compared by the domain, shown on the
  * wire and in Markdown as before the domain had records of its own.
  */
class CompareRunsViewsSuite extends munit.FunSuite:

  private val EnrichSource = RunInfo.Pipeline("enrich-source")
  private val ReviewArticlePipeline = RunInfo.Pipeline("review-article")
  private val X2020 = RunInfo.Target("x2020")

  private def step(
      seq: StepInfo.Seq,
      name: StepInfo.Name,
      status: StepInfo.Status = StepInfo.Status("done"),
      output: Option[StepInfo.Output] = None,
      model: Option[StepInfo.Model] = None,
      actual: Option[StepInfo.ActualModel] = None,
      error: Option[Problem] = None,
      usage: Usage = Usage.Zero,
      cost: StepInfo.CostUsd = StepInfo.CostUsd(0),
      started: StepInfo.StartedAt = StepInfo.StartedAt("2026-10-08T10:00:00Z"),
      finished: StepInfo.FinishedAt = StepInfo.FinishedAt("2026-10-08T10:00:03Z"),
      attempt: StepInfo.Attempt = StepInfo.Attempt(1)
  ): StepInfo =
    StepInfo(
      seq,
      name,
      if model.isDefined then StepInfo.Kind("llm") else StepInfo.Kind("code"),
      attempt,
      status,
      model,
      None,
      usage,
      cost,
      error,
      output,
      started,
      finished,
      actual
    )

  private def run(
      id: RunInfo.Id,
      pipeline: RunInfo.Pipeline,
      target: RunInfo.Target,
      steps: List[StepInfo],
      status: RunInfo.Status = RunInfo.Status("done"),
      dry: RunInfo.DryRun = RunInfo.DryRun(false),
      overrides: List[ModelOverride] = Nil
  ): RunDetail =
    RunDetail(
      RunInfo(
        id,
        pipeline,
        target,
        status,
        model = Some(RunInfo.Model("anthropic:x")),
        dryRun = dry,
        params = RunParams(modelFor = Option.when(overrides.nonEmpty)(overrides))
      ),
      steps,
      Nil
    )

  private def compare(runs: RunDetail*): RunCompare.Comparison =
    CompareRuns
      .compare(
        runs.toList.map(CompareRunsViews.run),
        _ => None,
        runs.headOption.map(_.run.pipeline) match
          case Some(EnrichSource)          => EnrichComparison
          case Some(ReviewArticlePipeline) => ReviewComparison
          case _                           => OutputComparison.none
      )
      .map(CompareRunsViews.comparison)
      .fold(p => fail(p.sentence), identity)

  test("a recorded run reaches the domain whole: its head, the models it was told to use, every step's record"):
    val s = step(
      StepInfo.Seq(3),
      StepInfo.Name("classify"),
      StepInfo.Status("failed"),
      model = Some(StepInfo.Model("anthropic:x")),
      actual = Some(StepInfo.ActualModel("x-2026")),
      error = Some(Problem.Unclassified(Legacy.Text("bad"))),
      usage = Usage(Usage.Input(20), Usage.Output(4), Usage.CacheRead(5), Usage.CacheWrite(1)),
      cost = StepInfo.CostUsd(0.01),
      attempt = StepInfo.Attempt(2)
    ).modify(_.firstCallTokens)
      .setTo(Some(StepInfo.FirstCallTokens(6200L)))
      .modify(_.turns)
      .setTo(Some(StepInfo.Turns(7)))
      .modify(_.output)
      .setTo(Some(StepInfo.Output("{}")))
    val r = CompareRunsViews.run(
      run(
        RunInfo.Id("a"),
        EnrichSource,
        X2020,
        List(s),
        dry = RunInfo.DryRun(true),
        overrides = List(ModelOverride(ModelOverride.Role("writer"), ModelOverride.Model("anthropic:y")))
      )
    )
    assertEquals(
      r.run,
      CompareRuns.RunHead(
        CompareRuns.RunHead.Id("a"),
        CompareRuns.RunHead.Pipeline("enrich-source"),
        CompareRuns.RunHead.Target("x2020"),
        CompareRuns.RunHead.Status("done"),
        CompareRuns.RunHead.DryRun(true),
        Some(CompareRuns.RunHead.Model("anthropic:x")),
        List(
          CompareRuns.ModelChoice(CompareRuns.ModelChoice.Role("writer"), CompareRuns.ModelChoice.Model("anthropic:y"))
        )
      )
    )
    assertEquals(
      r.steps,
      List(
        CompareRuns.Step(
          CompareRuns.Step.Name("classify"),
          CompareRuns.Step.Status("failed"),
          Some(CompareRuns.Step.Model("anthropic:x")),
          Some(CompareRuns.Step.ActualModel("x-2026")),
          CompareRuns.Tokens(
            CompareRuns.Tokens.Input(20),
            CompareRuns.Tokens.Output(4),
            CompareRuns.Tokens.CacheRead(5),
            CompareRuns.Tokens.CacheWrite(1)
          ),
          CompareRuns.Step.CostUsd(0.01),
          CompareRuns.Step.StartedAt("2026-10-08T10:00:00Z"),
          CompareRuns.Step.FinishedAt("2026-10-08T10:00:03Z"),
          Some(CompareRuns.Step.FirstCallTokens(6200L)),
          Some(CompareRuns.Step.Turns(7)),
          Some(CompareRuns.Step.Output("{}")),
          Some(Problem.Unclassified(Legacy.Text("bad")))
        )
      )
    )

  test("the comparison on the wire: every side, row and total, the models a run was told to use"):
    val a = run(
      RunInfo.Id("a"),
      EnrichSource,
      X2020,
      List(
        step(StepInfo.Seq(1), StepInfo.Name("load")),
        step(
          StepInfo.Seq(2),
          StepInfo.Name("classify"),
          model = Some(StepInfo.Model("anthropic:x")),
          usage = Usage(Usage.Input(10), Usage.Output(2))
        )
      ),
      overrides = List(ModelOverride(ModelOverride.Role("writer"), ModelOverride.Model("anthropic:y")))
    )
    val b = run(
      RunInfo.Id("b"),
      EnrichSource,
      X2020,
      List(step(StepInfo.Seq(1), StepInfo.Name("load"))),
      status = RunInfo.Status("paused")
    )
    val cmp = compare(a, b)
    assertEquals(
      cmp.runs.map(r => (r.id, r.status, r.modelFor, r.usage, r.seconds, r.attempts)),
      List(
        (
          "a",
          "done",
          List(ModelOverride(Agents.ModelOverride.Role("writer"), Agents.ModelOverride.Model("anthropic:y"))),
          Usage(Agents.Usage.Input(10), Agents.Usage.Output(2)),
          6L,
          2
        ),
        ("b", "paused", Nil, Usage.Zero, 3L, 1)
      )
    )
    assertEquals(
      cmp.steps,
      List(
        RunCompare.StepRow(
          RunCompare.StepRow.Name("load"),
          List(
            Some(
              RunCompare.StepSide(
                Nil,
                Nil,
                Nil,
                Usage.Zero,
                RunCompare.StepSide.CostUsd(0),
                RunCompare.StepSide.Seconds(3),
                RunCompare.StepSide.Attempts(1),
                RunCompare.StepSide.Failures(0)
              )
            ),
            Some(
              RunCompare.StepSide(
                Nil,
                Nil,
                Nil,
                Usage.Zero,
                RunCompare.StepSide.CostUsd(0),
                RunCompare.StepSide.Seconds(3),
                RunCompare.StepSide.Attempts(1),
                RunCompare.StepSide.Failures(0)
              )
            )
          )
        ),
        RunCompare.StepRow(
          RunCompare.StepRow.Name("classify"),
          List(
            Some(
              RunCompare.StepSide(
                List(RunCompare.StepSide.Models("anthropic:x")),
                Nil,
                Nil,
                Usage(Agents.Usage.Input(10), Agents.Usage.Output(2)),
                RunCompare.StepSide.CostUsd(0),
                RunCompare.StepSide.Seconds(3),
                RunCompare.StepSide.Attempts(1),
                RunCompare.StepSide.Failures(0)
              )
            ),
            None
          )
        )
      )
    )
    assertEquals(cmp.notes, List("run b is paused, not done: its outputs are what it had so far"))
    assertEquals((cmp.enrich.size, cmp.review, cmp.overlaps), (2, Nil, Nil))

  test("V072: the first call and the turns a step recorded are shown; a run before them says nothing"):
    val before = step(
      StepInfo.Seq(3),
      StepInfo.Name("classify"),
      StepInfo.Status("failed"),
      model = Some(StepInfo.Model("claude-code:haiku")),
      error = Some(Problem.Unclassified(Legacy.Text("bad")))
    )
    val a2 = step(
      StepInfo.Seq(3),
      StepInfo.Name("classify"),
      model = Some(StepInfo.Model("claude-code:haiku")),
      attempt = StepInfo.Attempt(3)
    )
      .modify(_.firstCallTokens)
      .setTo(Some(StepInfo.FirstCallTokens(6900L)))
      .modify(_.turns)
      .setTo(Some(StepInfo.Turns(5)))
    val md = AgentsMarkdown.compare(
      compare(
        run(RunInfo.Id("a"), EnrichSource, X2020, List(a2)),
        run(
          RunInfo.Id("b"),
          EnrichSource,
          X2020,
          List(before.modify(_.status).setTo(StepInfo.Status("done")).modify(_.error).setTo(None))
        )
      )
    )
    val row = md.linesIterator.find(_.startsWith("| classify")).get
    val cells = row.split(" \\| ").toList
    assert(cells(1).contains("first call 6900 tok · 5 turns"), row)
    assert(!cells(2).contains("first call"), "the old run's cell has no reading")
    assertEquals(AgentsMarkdown.callShape(Some(4100), Some(1)), "first call 4100 tok · 1 turn")
    assertEquals(AgentsMarkdown.callShape(None, Some(3)), "3 turns")
    assertEquals(AgentsMarkdown.callShape(None, None), "")

  test("review-article in Markdown: last verdict and rounds, round 1 by kind, the majors two runs share"):
    def f(
        id: Finding.Id,
        sev: Severity,
        where: Finding.Where,
        problem: Finding.Problem,
        kind: Finding.Kind = Finding.Kind("term")
    ) =
      Finding(
        id,
        if id.value.startsWith("R") then Reviewer.Rules else Reviewer.Skeptic,
        sev,
        kind,
        where,
        Finding.Quote(""),
        problem,
        Finding.Fix("")
      )
    def review(id: RunInfo.Id, rounds: List[Merged], reports: List[Report]) = run(
      id,
      ReviewArticlePipeline,
      RunInfo.Target("tin"),
      rounds.map(m =>
        step(
          StepInfo.Seq(m.round.value * 10),
          StepInfo.Name(s"merge-${m.round}"),
          output = Some(StepInfo.Output(m.asJson.noSpaces))
        )
      ) ++
        reports.map(r =>
          step(
            StepInfo.Seq(5),
            StepInfo.Name(s"${r.reviewer.wire}-1"),
            model = Some(StepInfo.Model("anthropic:x")),
            output = Some(StepInfo.Output(r.asJson.noSpaces))
          )
        )
    )
    val r1 = f(Finding.Id("R1"), Severity.Major, Finding.Where("Intro"), Finding.Problem("tin is not defined"))
    val s1 = f(
      Finding.Id("S1"),
      Severity.Major,
      Finding.Where("Dates"),
      Finding.Problem("the date is wrong"),
      kind = Finding.Kind("date")
    )
    val a = review(
      RunInfo.Id("a"),
      List(
        Merged(Merged.Round(1), Verdict.Major, List(r1, s1), None, Nil, Nil),
        Merged(Merged.Round(2), Verdict.Pass, Nil, None, Nil, Nil)
      ),
      List(Report(Reviewer.Rules, Verdict.Major, List(r1)), Report(Reviewer.Skeptic, Verdict.Major, List(s1)))
    )
    val r1b = f(
      Finding.Id("R1"),
      Severity.Major,
      Finding.Where("intro"),
      Finding.Problem("Tin is never defined, tin is not defined")
    )
    val b = review(
      RunInfo.Id("b"),
      List(Merged(Merged.Round(1), Verdict.Major, List(r1b), None, Nil, Nil)),
      List(Report(Reviewer.Rules, Verdict.Major, List(r1b)))
    )
    val cmp = compare(a, b)
    assertEquals(
      cmp.overlaps,
      List(
        RunCompare.MajorOverlap(
          RunCompare.MajorOverlap.A("a"),
          RunCompare.MajorOverlap.B("b"),
          RunCompare.MajorOverlap.AMajors(2),
          RunCompare.MajorOverlap.BMajors(1),
          RunCompare.MajorOverlap.Shared(1),
          List(RunCompare.MajorOverlap.Pairs("R1 ↔ R1"))
        )
      )
    )
    assertEquals(
      cmp.review.head.kinds,
      List(
        RunCompare.KindCount(RunCompare.KindCount.Kind("date"), RunCompare.KindCount.Count(1)),
        RunCompare.KindCount(RunCompare.KindCount.Kind("term"), RunCompare.KindCount.Count(1))
      )
    )
    val md = AgentsMarkdown.compare(cmp)
    assert(
      md.contains("| verdict (last round) | PASS | MAJOR |") && md.contains("| rounds | 2 | 1 |") &&
        md.contains("| kind: date (round 1) | 1 | 0 |") && md.contains("- a × b: 1 shared of 2 and 1 (R1 ↔ R1)"),
      md
    )
    assert(md.contains("| merge-1 | code · 0/0 · 3 s | code · 0/0 · 3 s |"), md)
