package com.histmersion.server.agents

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.workflows.ReviewComparison

import com.histmersion.domain.automation.agents.{CompareRunsViews, OutputComparison}
import com.histmersion.domain.library.workflows.EnrichComparison
import com.histmersion.domain.automation.managers.Zero

import com.histmersion.api.automation.Agents.*
import com.histmersion.domain.CompareRuns
import com.histmersion.domain.ReviewArticle.{Finding, Merged, Report, Reviewer, Severity, Verdict}
import io.circe.syntax.*
import com.histmersion.api.automation.{AgentsMarkdown, RunCompare}

/** `agent compare` at the server's edge: recorded runs as the store reads them, compared by the domain, shown on the
  * wire and in Markdown as before the domain had records of its own.
  */
class CompareRunsViewsSuite extends munit.FunSuite:

  private def step(
      seq: Int,
      name: String,
      status: String = "done",
      output: Option[String] = None,
      model: Option[String] = None,
      actual: Option[String] = None,
      error: Option[String] = None,
      usage: Usage = Usage.Zero,
      cost: Double = 0,
      started: String = "2026-10-08T10:00:00Z",
      finished: String = "2026-10-08T10:00:03Z",
      attempt: Int = 1
  ): StepInfo =
    StepInfo(
      seq,
      name,
      if model.isDefined then com.histmersion.api.automation.Agents.StepInfo.Kind("llm") else com.histmersion.api.automation.Agents.StepInfo.Kind("code"),
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
      id: String,
      pipeline: String,
      target: String,
      steps: List[StepInfo],
      status: String = "done",
      dry: Boolean = false,
      overrides: List[ModelOverride] = Nil
  ): RunDetail =
    RunDetail(
      RunInfo(
        id,
        pipeline,
        target,
        status,
        model = Some("anthropic:x"),
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
          case Some("enrich-source")  => EnrichComparison
          case Some("review-article") => ReviewComparison
          case _                      => OutputComparison.none
      )
      .map(CompareRunsViews.comparison)
      .fold(fail(_), identity)

  test("a recorded run reaches the domain whole: its head, the models it was told to use, every step's record"):
    val s = step(
      3,
      "classify",
      "failed",
      model = Some("anthropic:x"),
      actual = Some("x-2026"),
      error = Some("bad"),
      usage = Usage(com.histmersion.api.automation.Agents.Usage.Input(20), com.histmersion.api.automation.Agents.Usage.Output(4), com.histmersion.api.automation.Agents.Usage.CacheRead(5), com.histmersion.api.automation.Agents.Usage.CacheWrite(1)),
      cost = 0.01,
      attempt = 2
    ).modify(_.firstCallTokens).setTo((Some(6200): _root_.scala.Option[_root_.scala.Long])).modify(_.turns).setTo(Some(7)).modify(_.output).setTo(Some("{}"))
    val r = CompareRunsViews.run(
      run("a", "enrich-source", "x2020", List(s), dry = true, overrides = List(ModelOverride(com.histmersion.api.automation.Agents.ModelOverride.Role("writer"), com.histmersion.api.automation.Agents.ModelOverride.Model("anthropic:y"))))
    )
    assertEquals(
      r.run,
      CompareRuns.RunHead(
        com.histmersion.domain.CompareRuns.RunHead.Id("a"),
        com.histmersion.domain.CompareRuns.RunHead.Pipeline("enrich-source"),
        com.histmersion.domain.CompareRuns.RunHead.Target("x2020"),
        com.histmersion.domain.CompareRuns.RunHead.Status("done"),
        com.histmersion.domain.CompareRuns.RunHead.DryRun(true),
        Some(com.histmersion.domain.CompareRuns.RunHead.Model("anthropic:x")),
        List(CompareRuns.ModelChoice(com.histmersion.domain.CompareRuns.ModelChoice.Role("writer"), com.histmersion.domain.CompareRuns.ModelChoice.Model("anthropic:y")))
      )
    )
    assertEquals(
      r.steps,
      List(
        CompareRuns.Step(
          com.histmersion.domain.CompareRuns.Step.Name("classify"),
          com.histmersion.domain.CompareRuns.Step.Status("failed"),
          Some(com.histmersion.domain.CompareRuns.Step.Model("anthropic:x")),
          Some(com.histmersion.domain.CompareRuns.Step.ActualModel("x-2026")),
          CompareRuns.Tokens(com.histmersion.domain.CompareRuns.Tokens.Input(20), com.histmersion.domain.CompareRuns.Tokens.Output(4), com.histmersion.domain.CompareRuns.Tokens.CacheRead(5), com.histmersion.domain.CompareRuns.Tokens.CacheWrite(1)),
          0.01,
          com.histmersion.domain.CompareRuns.Step.StartedAt("2026-10-08T10:00:00Z"),
          com.histmersion.domain.CompareRuns.Step.FinishedAt("2026-10-08T10:00:03Z"),
          Some(com.histmersion.domain.CompareRuns.Step.FirstCallTokens(6200L)),
          Some(com.histmersion.domain.CompareRuns.Step.Turns(7)),
          Some(com.histmersion.domain.CompareRuns.Step.Output("{}")),
          Some(com.histmersion.domain.CompareRuns.Step.Error("bad"))
        )
      )
    )

  test("the comparison on the wire: every side, row and total, the models a run was told to use"):
    val a = run(
      "a",
      "enrich-source",
      "x2020",
      List(step(1, "load"), step(2, "classify", model = Some("anthropic:x"), usage = Usage(com.histmersion.api.automation.Agents.Usage.Input(10), com.histmersion.api.automation.Agents.Usage.Output(2)))),
      overrides = List(ModelOverride(com.histmersion.api.automation.Agents.ModelOverride.Role("writer"), com.histmersion.api.automation.Agents.ModelOverride.Model("anthropic:y")))
    )
    val b = run("b", "enrich-source", "x2020", List(step(1, "load")), status = "paused")
    val cmp = compare(a, b)
    assertEquals(
      cmp.runs.map(r => (r.id, r.status, r.modelFor, r.usage, r.seconds, r.attempts)),
      List(
        ("a", "done", List(ModelOverride(com.histmersion.api.automation.Agents.ModelOverride.Role("writer"), com.histmersion.api.automation.Agents.ModelOverride.Model("anthropic:y"))), Usage(com.histmersion.api.automation.Agents.Usage.Input(10), com.histmersion.api.automation.Agents.Usage.Output(2)), 6L, 2),
        ("b", "paused", Nil, Usage.Zero, 3L, 1)
      )
    )
    assertEquals(
      cmp.steps,
      List(
        RunCompare.StepRow(
          com.histmersion.api.automation.RunCompare.StepRow.Name("load"),
          List(
            Some(RunCompare.StepSide(Nil, Nil, Nil, Usage.Zero, com.histmersion.api.automation.RunCompare.StepSide.CostUsd(0), com.histmersion.api.automation.RunCompare.StepSide.Seconds(3), com.histmersion.api.automation.RunCompare.StepSide.Attempts(1), com.histmersion.api.automation.RunCompare.StepSide.Failures(0))),
            Some(RunCompare.StepSide(Nil, Nil, Nil, Usage.Zero, com.histmersion.api.automation.RunCompare.StepSide.CostUsd(0), com.histmersion.api.automation.RunCompare.StepSide.Seconds(3), com.histmersion.api.automation.RunCompare.StepSide.Attempts(1), com.histmersion.api.automation.RunCompare.StepSide.Failures(0)))
          )
        ),
        RunCompare.StepRow(
          com.histmersion.api.automation.RunCompare.StepRow.Name("classify"),
          List(Some(RunCompare.StepSide(List(com.histmersion.api.automation.RunCompare.StepSide.Models("anthropic:x")), Nil, Nil, Usage(com.histmersion.api.automation.Agents.Usage.Input(10), com.histmersion.api.automation.Agents.Usage.Output(2)), com.histmersion.api.automation.RunCompare.StepSide.CostUsd(0), com.histmersion.api.automation.RunCompare.StepSide.Seconds(3), com.histmersion.api.automation.RunCompare.StepSide.Attempts(1), com.histmersion.api.automation.RunCompare.StepSide.Failures(0))), None)
        )
      )
    )
    assertEquals(cmp.notes, List("run b is paused, not done: its outputs are what it had so far"))
    assertEquals((cmp.enrich.size, cmp.review, cmp.overlaps), (2, Nil, Nil))

  test("V072: the first call and the turns a step recorded are shown; a run before them says nothing"):
    val before = step(3, "classify", "failed", model = Some("claude-code:haiku"), error = Some("bad"))
    val a2 = step(3, "classify", model = Some("claude-code:haiku"), attempt = 3)
      .modify(_.firstCallTokens).setTo((Some(6900): _root_.scala.Option[_root_.scala.Long])).modify(_.turns).setTo(Some(5))
    val md = AgentsMarkdown.compare(
      compare(
        run("a", "enrich-source", "x2020", List(a2)),
        run("b", "enrich-source", "x2020", List(before.modify(_.status).setTo("done").modify(_.error).setTo(None)))
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
    def f(id: String, sev: Severity, where: String, problem: String, kind: String = "term") =
      Finding(id, if id.startsWith("R") then Reviewer.Rules else Reviewer.Skeptic, sev, kind, where, com.histmersion.domain.ReviewArticle.Finding.Quote(""), problem, com.histmersion.domain.ReviewArticle.Finding.Fix(""))
    def review(id: String, rounds: List[Merged], reports: List[Report]) = run(
      id,
      "review-article",
      "tin",
      rounds.map(m => step(m.round * 10, s"merge-${m.round}", output = Some(m.asJson.noSpaces))) ++
        reports.map(r =>
          step(5, s"${r.reviewer.wire}-1", model = Some("anthropic:x"), output = Some(r.asJson.noSpaces))
        )
    )
    val r1 = f("R1", Severity.Major, "Intro", "tin is not defined")
    val s1 = f("S1", Severity.Major, "Dates", "the date is wrong", kind = "date")
    val a = review(
      "a",
      List(Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), Verdict.Major, List(r1, s1), None, Nil, Nil), Merged(com.histmersion.domain.ReviewArticle.Merged.Round(2), Verdict.Pass, Nil, None, Nil, Nil)),
      List(Report(Reviewer.Rules, Verdict.Major, List(r1)), Report(Reviewer.Skeptic, Verdict.Major, List(s1)))
    )
    val r1b = f("R1", Severity.Major, "intro", "Tin is never defined, tin is not defined")
    val b = review(
      "b",
      List(Merged(com.histmersion.domain.ReviewArticle.Merged.Round(1), Verdict.Major, List(r1b), None, Nil, Nil)),
      List(Report(Reviewer.Rules, Verdict.Major, List(r1b)))
    )
    val cmp = compare(a, b)
    assertEquals(cmp.overlaps, List(RunCompare.MajorOverlap(com.histmersion.api.automation.RunCompare.MajorOverlap.A("a"), com.histmersion.api.automation.RunCompare.MajorOverlap.B("b"), com.histmersion.api.automation.RunCompare.MajorOverlap.AMajors(2), com.histmersion.api.automation.RunCompare.MajorOverlap.BMajors(1), com.histmersion.api.automation.RunCompare.MajorOverlap.Shared(1), List(com.histmersion.api.automation.RunCompare.MajorOverlap.Pairs("R1 ↔ R1")))))
    assertEquals(cmp.review.head.kinds, List(RunCompare.KindCount(com.histmersion.api.automation.RunCompare.KindCount.Kind("date"), com.histmersion.api.automation.RunCompare.KindCount.Count(1)), RunCompare.KindCount(com.histmersion.api.automation.RunCompare.KindCount.Kind("term"), com.histmersion.api.automation.RunCompare.KindCount.Count(1))))
    val md = AgentsMarkdown.compare(cmp)
    assert(
      md.contains("| verdict (last round) | PASS | MAJOR |") && md.contains("| rounds | 2 | 1 |") &&
        md.contains("| kind: date (round 1) | 1 | 0 |") && md.contains("- a × b: 1 shared of 2 and 1 (R1 ↔ R1)"),
      md
    )
    assert(md.contains("| merge-1 | code · 0/0 · 3 s | code · 0/0 · 3 s |"), md)
