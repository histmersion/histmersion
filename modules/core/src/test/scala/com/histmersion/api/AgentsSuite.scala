package com.histmersion
package api
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Legacy, Problem}
import com.histmersion.util.problem.Text.{sentence, sentences}

import com.histmersion.testsupport.ComparedByValue.given

import hearth.kindlings.optics.syntax.*

import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.config.given
import com.histmersion.domain.given

import com.histmersion.api.automation.Agents.*
import com.histmersion.api.automation.{AgentsMarkdown, Goal, PlanQuota}
import com.histmersion.api.automation.Agents
import com.histmersion.api.automation.AgentRole
import com.histmersion.api.automation.Approval
import com.histmersion.api.automation.Run
import com.histmersion.api.library.Doi
import com.histmersion.api.catalogue.Year
import com.histmersion.api.library.Citekey
import com.histmersion.api.research.Article

class AgentsSuite extends munit.FunSuite:
  private def unknownProvider(name: Problem.Raw)(p: Problem): Boolean = p match
    case Problem.Invalid.Unknown(t, raw, _, _) =>
      t == Problem.TypeName.of(Agents.ProviderKind.Wire) && raw == name
    case _ => false

  test("ModelRef: only the first colon separates the provider (Ollama ids carry colons)"):
    assertEquals(
      ModelRef.parse(Agents.ModelRef.Wire("local:qwen2.5-coder:32b")),
      Right(ModelRef(ProviderKind.Local, Agents.ModelRef.Model("qwen2.5-coder:32b")))
    )
    assertEquals(
      ModelRef.parse(Agents.ModelRef.Wire(" anthropic:claude-x ")),
      Right(ModelRef(ProviderKind.Anthropic, Agents.ModelRef.Model("claude-x")))
    )
    assertEquals(ModelRef.parse(Agents.ModelRef.Wire("openai:gpt")).map(_.wire), Right("openai:gpt"))

  test("ModelRef: unknown provider, no model, no colon are errors"):
    assert(ModelRef.parse(Agents.ModelRef.Wire("ollama:qwen")).left.exists(unknownProvider(Problem.Raw("ollama"))))
    assertEquals(
      ModelRef.parse(Agents.ModelRef.Wire("local:")),
      Left(Problem.Invalid.Missing(Problem.Field("model"), Vector(Problem.Field("local:"))))
    )
    assert(ModelRef.parse(Agents.ModelRef.Wire("qwen")).left.exists {
      case Problem.Invalid.Unreadable(t, raw, _, _) =>
        t == Problem.TypeName.of(Agents.ModelRef.Wire) && raw == Problem.Raw("qwen")
      case _ => false
    })

  test("ModelRef: a plugin provider's name parses only while it is registered (known), never a host name"):
    assert(
      ModelRef
        .parse(Agents.ModelRef.Wire("gemini:pro"))
        .left
        .exists(e =>
          unknownProvider(Problem.Raw("gemini"))(e) && (e match
            case Problem.Invalid.Unknown(_, _, known, _) => !known.contains(Problem.Raw("gemini"))
            case _                                       => false)
        )
    )
    assertEquals(
      ModelRef.parse(
        Agents.ModelRef.Wire("gemini:pro"),
        Set(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini"))
      ),
      Right(
        ModelRef(
          ProviderKind.Plugin(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")),
          Agents.ModelRef.Model("pro")
        )
      )
    )
    assertEquals(
      ModelRef
        .parse(
          Agents.ModelRef.Wire(" Gemini:pro:v2 "),
          Set(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini"))
        )
        .map(_.wire),
      Right("gemini:pro:v2")
    )
    // a host name stays the host's, whatever a plugin claims
    assertEquals(
      ModelRef
        .parse(Agents.ModelRef.Wire("local:x"), Set(com.histmersion.api.automation.ProviderKind.Plugin.Name("local"))),
      Right(ModelRef(ProviderKind.Local, Agents.ModelRef.Model("x")))
    )
    assert(
      ModelRef
        .parse(Agents.ModelRef.Wire("ollama:x"), Set(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")))
        .left
        .exists(_.sentence.contains("one of: anthropic, openai, local, claude-code, codex, opencode, gemini"))
    )
    assertEquals(
      ProviderKind.values.map(_.wire),
      List("anthropic", "openai", "local", "claude-code", "codex", "opencode")
    )
    assert(!ProviderKind.Plugin(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")).isCli)

  test("a plugin provider's model is metered: no plan pool, paid, needs a price"):
    val m = ModelRef(
      ProviderKind.Plugin(com.histmersion.api.automation.ProviderKind.Plugin.Name("gemini")),
      Agents.ModelRef.Model("pro")
    )
    assertEquals(PlanQuota.poolOf(m, PlanQuota.Logins()), None)
    assert(PlanQuota.paid(m, PlanQuota.Logins()))
    assert(Prices.billing(Map.empty, m, None).left.exists(_.sentence.contains("model 'pro' has no price")))
    assertEquals(
      Prices.billing(Map(Agents.ModelRef.Model("pro") -> Price(BigDecimal(1), BigDecimal(2))), m, None),
      Right(Billing.Metered(Price(BigDecimal(1), BigDecimal(2))))
    )

  test("Prices: two or four numbers per model, ; separated"):
    val p = Prices.parse(Agents.Prices.Wire("qwen2.5-coder:32b=0/0; claude-x=3/15/0.3/3.75")).toOption.get
    assertEquals(p(Agents.ModelRef.Model("qwen2.5-coder:32b")), Price(BigDecimal(0), BigDecimal(0)))
    assertEquals(
      p(Agents.ModelRef.Model("claude-x")),
      Price(BigDecimal(3), BigDecimal(15), Some(BigDecimal("0.3")), Some(BigDecimal("3.75")))
    )
    assertEquals(Prices.parse(Agents.Prices.Wire("  ")), Right(Map.empty))

  test("Prices: every malformed entry is reported, none silently skipped"):
    val e = Prices.parse(Agents.Prices.Wire("a=1; b=x/2; c=1/2/3; =1/1")).left.toOption.get
    List("a=1", "b=x/2", "c=1/2/3", "=1/1").foreach(m => assert(e.sentences.mkString("; ").contains(m), e))
    assert(Prices.parse(Agents.Prices.Wire("a=-1/2")).isLeft)

  test("cost: per million tokens, cache reads and writes at their own rates"):
    val ps = Map(
      Agents.ModelRef
        .Model("m") -> Price(BigDecimal(3), BigDecimal(15), Some(BigDecimal("0.3")), Some(BigDecimal("3.75")))
    )
    // 1M input of which 200k cache-read and 100k cache-write; 100k output
    val c = Prices.cost(
      ps,
      Agents.Prices.CostModel("m"),
      Usage(
        input = Agents.Usage.Input(1000000),
        output = Agents.Usage.Output(100000),
        cacheRead = Agents.Usage.CacheRead(200000),
        cacheWrite = Agents.Usage.CacheWrite(100000)
      )
    )
    assertEquals(c, Right(BigDecimal("2.1") + BigDecimal("0.06") + BigDecimal("0.375") + BigDecimal("1.5")))

  test("cost: an UNPRICED model is refused, never free (the inverse of sttp-ai's PriceTable)"):
    val c =
      Prices.cost(Map.empty, Agents.Prices.CostModel("gpt-x"), Usage(Agents.Usage.Input(10), Agents.Usage.Output(10)))
    assert(c.left.exists(_.sentence.contains("has no price")), c)

  test("billing: a plan model needs no price and costs 0 USD; a metered one is priced"):
    val plan = ModelRef(ProviderKind.ClaudeCode, Agents.ModelRef.Model("sonnet"))
    val b = Prices.billing(Map.empty, plan, Some(PlanQuota.PoolId.ClaudePlan))
    assertEquals(b, Right(Billing.Plan(PlanQuota.PoolId.ClaudePlan)))
    assertEquals(b.map(_.cost(Usage(Agents.Usage.Input(1000000), Agents.Usage.Output(1000000)))), Right(BigDecimal(0)))
    assertEquals(b.map(_.describe), Right("plan pool: claude-plan"))
    // a price in the table does not make a plan model metered
    assert(
      Prices
        .billing(
          Map(Agents.ModelRef.Model("sonnet") -> Price(BigDecimal(3), BigDecimal(15))),
          plan,
          Some(PlanQuota.PoolId.ClaudePlan)
        )
        .exists(_.isInstanceOf[Billing.Plan])
    )
    val m =
      Prices.billing(
        Map(Agents.ModelRef.Model("m") -> Price(BigDecimal(3), BigDecimal(15))),
        ModelRef(ProviderKind.Anthropic, Agents.ModelRef.Model("m")),
        None
      )
    assertEquals(m.map(_.cost(Usage(Agents.Usage.Input(1000000), Agents.Usage.Output(0)))), Right(BigDecimal(3)))
    assertEquals(m.map(_.describe), Right("price: 3/15 USD per million tokens"))

  test("billing: an unpriced METERED model is refused — claude-code on an API key included"):
    val local = Prices.billing(Map.empty, ModelRef(ProviderKind.Local, Agents.ModelRef.Model("qwen")), None)
    assert(local.left.exists(_.sentence.contains("has no price")), local)
    val keyed = Prices.billing(Map.empty, ModelRef(ProviderKind.ClaudeCode, Agents.ModelRef.Model("sonnet")), None)
    assert(keyed.left.exists(e => e.sentence.contains("has no price") && e.sentence.contains("use an API key")), keyed)

  test("price text: two or four numbers, trailing zeros dropped"):
    assertEquals(Price(BigDecimal("3.00"), BigDecimal(15)).text, "3/15")
    assertEquals(
      Price(BigDecimal(3), BigDecimal(15), Some(BigDecimal("0.3")), Some(BigDecimal("3.75"))).text,
      "3/15/0.3/3.75"
    )

  test("RunState: legal transitions only; resume is paused → queued"):
    import RunState.*
    assertEquals(next(Queued, Running), Some(Running))
    assertEquals(next(Running, Paused), Some(Paused))
    assertEquals(next(Paused, Queued), Some(Queued))
    assertEquals(next(Paused, Running), None)
    assertEquals(next(Done, Queued), None)
    assertEquals(next(Cancelled, Queued), None)
    assert(Done.isTerminal && Failed.isTerminal && Cancelled.isTerminal && !Paused.isTerminal)

  test("Role and Pipeline parse their wire names"):
    assertEquals(Role.parse(AgentRole.Wire("cold-reader")), Some(Role.ColdReader))
    assertEquals(
      Pipeline.parse(com.histmersion.api.automation.Pipeline.Wire("enrich-source")),
      Some(Pipeline.EnrichSource)
    )
    assertEquals(
      Pipeline.parse(com.histmersion.api.automation.Pipeline.Wire("review-article")),
      Some(Pipeline.ReviewArticle)
    )
    assertEquals(Pipeline.parse(com.histmersion.api.automation.Pipeline.Wire("enrich_source")), None)

  test("each pipeline names its target kind and every role it needs"):
    assertEquals(Pipeline.EnrichSource.targetKind, TargetKind.Source)
    assertEquals(Pipeline.ReviewArticle.targetKind, TargetKind.Article)
    assertEquals(Pipeline.ReviewArticle.roles, List(Role.Reviewer, Role.ColdReader, Role.Skeptic, Role.Fixer))
    assertEquals(TargetKind.parse(com.histmersion.api.automation.TargetKind.Wire("article")), Some(TargetKind.Article))

  test("approval vocabularies parse their wire names"):
    assertEquals(ApprovalKind.parse(Approval.Kind.Wire("status-change")), Some(ApprovalKind.StatusChange))
    assertEquals(ApprovalState.parse(Approval.State.Wire("open")), Some(ApprovalState.Open))
    assertEquals(Decision.parse(Agents.Decision.Wire(" Approve ")), Some(Decision.Approve))
    assertEquals(Decision.parse(Agents.Decision.Wire("accept")), None)

  test("RunParams: missing options decode as their defaults"):
    assertEquals(RawJson.decode[RunParams]("{}"), Right(RunParams()))
    assert(!RunParams().fixes)
    assert(RunParams(fix = Some(Agents.RunRequest.Fix(true))).fixes)

  test("RunState: a run waits for an approval only from running, and leaves it only to the queue or cancelled"):
    import RunState.*
    assertEquals(next(Running, WaitingApproval), Some(WaitingApproval))
    assertEquals(next(WaitingApproval, Queued), Some(Queued))
    assertEquals(next(WaitingApproval, Cancelled), Some(Cancelled))
    assertEquals(next(WaitingApproval, Running), None)
    assertEquals(next(WaitingApproval, Paused), None)
    assertEquals(next(Queued, WaitingApproval), None)
    assert(!WaitingApproval.isTerminal)
    assertEquals(RunState.parse(Run.State.Wire("waiting")), Some(WaitingApproval))
    assert(
      RunInfo(
        Agents.RunInfo.Id("r"),
        Agents.RunInfo.Pipeline("plan-goal"),
        Agents.RunInfo.Target("g"),
        Agents.RunInfo.Status("waiting")
      ).isWaiting
    )

  test("P3 pipelines, targets, roles and blocking approval kinds"):
    assertEquals(
      Pipeline.parse(com.histmersion.api.automation.Pipeline.Wire("plan-goal")).map(_.targetKind),
      Some(TargetKind.Goal)
    )
    assertEquals(Pipeline.CompileArticle.roles, List(Role.Extractor, Role.Writer))
    assertEquals(Pipeline.CurrencyCheck.roles, Nil)
    assertEquals(Pipeline.AcquisitionSweep.roles, List(Role.Planner))
    assert(ApprovalKind.GoalPlan.blocking.value && ApprovalKind.ArticlePublish.blocking.value)
    assert(!ApprovalKind.StatusChange.blocking.value && !ApprovalKind.Edge.blocking.value)
    assertEquals(
      Goal.Plans.ScheduleKind.parse(com.histmersion.api.automation.ScheduleKind.Wire("acquisition")).map(_.pipeline),
      Some(Pipeline.AcquisitionSweep)
    )
    assertEquals(Origin.parse(Agents.Origin.Wire("scheduler")), Some(Origin.Scheduler))
    // a run stored before P3 reads its new options as absent
    assertEquals(
      RawJson.decode[RunParams]("""{"fix":true}"""),
      Right(RunParams(fix = Some(Agents.RunRequest.Fix(true))))
    )

  test("Markdown: a plan and a draft as a person reads them before approving; a goal with its links and schedules"):
    import com.histmersion.api.automation.Goal.Plans.*
    val plan = Plan(
      Goal.Slug("g"),
      Plan.Rationale("covers it"),
      List(PlannedGoal(Goal.Slug("g-sub"), Goal.Title("Sub"))),
      List(Plan.Queries("tin isotopes")),
      List(
        PlannedWork(
          Doi("10.1000/x"),
          Some(com.histmersion.api.library.Title("X")),
          Some(Year(2020)),
          citekey = Some(Citekey("x2020_y")),
          inCorpus = PlannedWork.InCorpus(true),
          why = Goal.Rationale("core")
        )
      ),
      List(Plan.Dropped("10.1000/z: not verified upstream"))
    )
    val a =
      ApprovalInfo(
        Agents.ApprovalInfo.Id(7),
        Some(Agents.RunInfo.Id("run12345")),
        Agents.ApprovalInfo.Kind("goal-plan"),
        Citekey(""),
        evidence = Agents.ApprovalInfo.Evidence("covers it"),
        subject = Some(Agents.ApprovalInfo.Subject("g")),
        plan = Some(plan)
      )
    val md = AgentsMarkdown.approvals(List(a))
    assert(md.contains("plan for goal **g**: 1 sub-goal(s), 1 query(ies), 1 work(s)"), md)
    assert(md.contains("in the corpus as [[x2020_y]]: core") && md.contains("dropped: 10.1000/z"), md)
    assertEquals(md.split("covers it", -1).length - 1, 1, "the rationale is shown once")
    val d = ArticleDraft(
      Article.Slug("tin"),
      DraftMode.Extend,
      Article.Title("Tin"),
      Article.Kind.Wire("concept"),
      ArticleDraft.Text("New text [[x2020_y]]."),
      section = Some(ArticleDraft.Section("## Disagreements")),
      problems = List(ArticleDraft.Problems("a paragraph with no [[citekey]]"))
    )
    val pub = ApprovalInfo(
      Agents.ApprovalInfo.Id(8),
      None,
      Agents.ApprovalInfo.Kind("article-publish"),
      Citekey(""),
      subject = Some(Agents.ApprovalInfo.Subject("tin")),
      article = Some(d)
    )
    val md2 = AgentsMarkdown.approvals(List(pub))
    assert(
      md2.contains("publish a section appended to [[tin]]") && md2.contains("under '## Disagreements'") &&
        md2.contains("- self-check: a paragraph with no [[citekey]]") && md2.contains("New text [[x2020_y]]."),
      md2
    )
    val s = GoalSchedule(
      Goal.Slug("g"),
      ScheduleKind.Currency,
      GoalSchedule.Cron("@daily"),
      enabled = GoalSchedule.Enabled(false),
      Some(GoalSchedule.BudgetUsd(0.5)),
      None,
      None,
      Some(GoalSchedule.LastRun("abcdef123")),
      Some(GoalSchedule.LastNote("skipped: x"))
    )
    assertEquals(
      AgentsMarkdown.scheduleLine(s),
      "g · currency · `@daily` · off · cap 0.5000 USD · last run abcdef12 · skipped: x"
    )
    val v = GoalView(
      Goal(Goal.Slug("g"), Goal.Title("G"), Goal.StatusValue("planned")),
      links = List(GoalLink(LinkKind.Source, GoalLink.Ref("gone2020_x")), GoalLink(LinkKind.Query, GoalLink.Ref("q"))),
      schedules = List(s)
    )
    val md3 = AgentsMarkdown.goalView(v)
    assert(
      md3.contains("- source: [[gone2020_x]] (missing)") && md3.contains("- query: \"q\"") && md3.contains(
        "## Schedules"
      ),
      md3
    )

  // ── #992 ───────────────────────────────────────────────────────────────────────────────────────

  test("ModelOverride: role=provider:model, only the first = separates; a missing role or model is an error"):
    assertEquals(
      ModelOverride.parse(Agents.ModelOverride.Wire(" Skeptic = claude-code:haiku ")),
      Right(ModelOverride(Agents.ModelOverride.Role("skeptic"), Agents.ModelOverride.Model("claude-code:haiku")))
    )
    assertEquals(
      ModelOverride.parse(Agents.ModelOverride.Wire("fixer=local:a > anthropic:b")),
      Right(ModelOverride(Agents.ModelOverride.Role("fixer"), Agents.ModelOverride.Model("local:a > anthropic:b")))
    )
    assert(
      ModelOverride
        .parse(Agents.ModelOverride.Wire("skeptic"))
        .left
        .exists {
          case Problem.Invalid.Unreadable(_, raw, _, _) => raw == Problem.Raw("skeptic")
          case _                                        => false
        }
    )
    assertEquals(
      ModelOverride.parse(Agents.ModelOverride.Wire("=local:x")),
      Left(Problem.Invalid.Missing(Problem.Field("role"), Vector(Problem.Field("=local:x"))))
    )
    assert(
      ModelOverride
        .parse(Agents.ModelOverride.Wire("skeptic="))
        .left
        .toOption
        .contains(
          Problem.Invalid.Missing(Problem.Field("model"), Vector(Problem.Field("skeptic=")))
        )
    )

  test("a run's model for a role: its --model-for, else its --model, else none (the role's chain)"):
    val r = RunInfo(
      Agents.RunInfo.Id("r"),
      Agents.RunInfo.Pipeline("review-article"),
      Agents.RunInfo.Target("x"),
      Agents.RunInfo.Status("queued"),
      model = Some(Agents.RunInfo.Model("anthropic:all")),
      params = RunParams(modelFor =
        Some(List(ModelOverride(Agents.ModelOverride.Role("skeptic"), Agents.ModelOverride.Model("claude-code:haiku"))))
      )
    )
    assertEquals(r.modelFor(Role.Skeptic), Some("claude-code:haiku"))
    assertEquals(r.modelFor(Role.Reviewer), Some("anthropic:all"))
    assertEquals(
      RunInfo(
        Agents.RunInfo.Id("r"),
        Agents.RunInfo.Pipeline("p"),
        Agents.RunInfo.Target("x"),
        Agents.RunInfo.Status("queued")
      ).modelFor(Role.Fixer),
      None
    )

  test("a step's model: the configured ref, and the provider's id when it said another"):
    val s = StepInfo(
      Agents.StepInfo.Seq(1),
      Agents.StepInfo.Name("skeptic-1"),
      Agents.StepInfo.Kind("llm"),
      Agents.StepInfo.Attempt(1),
      Agents.StepInfo.Status("done"),
      model = Some(Agents.StepInfo.Model("claude-code:haiku")),
      actualModel = Some(Agents.StepInfo.ActualModel("claude-haiku-5-5"))
    )
    assertEquals(s.modelText, "claude-code:haiku → claude-haiku-5-5")
    assertEquals(
      s.modify(_.actualModel).setTo(Some(Agents.StepInfo.ActualModel("haiku"))).modelText,
      "claude-code:haiku"
    )
    assertEquals(s.modify(_.actualModel).setTo(None).modelText, "claude-code:haiku")
    assertEquals(s.modify(_.model).setTo(None).modelText, "claude-haiku-5-5")
    assertEquals(
      StepInfo(
        Agents.StepInfo.Seq(1),
        Agents.StepInfo.Name("load"),
        Agents.StepInfo.Kind("code"),
        Agents.StepInfo.Attempt(1),
        Agents.StepInfo.Status("done")
      ).modelText,
      ""
    )

  test("agent show: an LLM step's tokens carry its first call's input and its turns (V072); older steps show neither"):
    val s = StepInfo(
      Agents.StepInfo.Seq(2),
      Agents.StepInfo.Name("notes"),
      Agents.StepInfo.Kind("llm"),
      Agents.StepInfo.Attempt(1),
      Agents.StepInfo.Status("done"),
      model = Some(Agents.StepInfo.Model("claude-code:sonnet")),
      usage = Usage(Agents.Usage.Input(41000), Agents.Usage.Output(900)),
      firstCallTokens = Some(Agents.StepInfo.FirstCallTokens(6963)),
      turns = Some(Agents.StepInfo.Turns(9))
    )
    val d = RunDetail(
      RunInfo(
        Agents.RunInfo.Id("r1"),
        Agents.RunInfo.Pipeline("enrich-source"),
        Agents.RunInfo.Target("x2020"),
        Agents.RunInfo.Status("done")
      ),
      List(
        s,
        s.modify(_.seq).setTo(Agents.StepInfo.Seq(3)).modify(_.firstCallTokens).setTo(None).modify(_.turns).setTo(None)
      ),
      Nil
    )
    val md = AgentsMarkdown.run(d, withMessages = false)
    assert(
      md.contains("| 2 | notes | llm | 1 | done | claude-code:sonnet | 41000/900 · first call 6963 tok · 9 turns | "),
      md
    )
    assert(md.contains("| 3 | notes | llm | 1 | done | claude-code:sonnet | 41000/900 | "), md)

  test("a dry run is marked in the run line and the run, and each skipped write is shown with its payload"):
    val r = RunInfo(
      Agents.RunInfo.Id("r1"),
      Agents.RunInfo.Pipeline("enrich-source"),
      Agents.RunInfo.Target("x2020"),
      Agents.RunInfo.Status("done"),
      dryRun = Agents.RunInfo.DryRun(true),
      params = RunParams(modelFor =
        Some(List(ModelOverride(Agents.ModelOverride.Role("extractor"), Agents.ModelOverride.Model("local:h"))))
      )
    )
    assert(
      AgentsMarkdown.runLine(r).contains("(dry run, writes nothing, extractor=local:h)"),
      AgentsMarkdown.runLine(r)
    )
    val d = RunDetail(
      r,
      List(
        StepInfo(
          Agents.StepInfo.Seq(8),
          Agents.StepInfo.Name("patch"),
          Agents.StepInfo.Kind("code"),
          Agents.StepInfo.Attempt(1),
          Agents.StepInfo.Status("done"),
          wouldWrite = List(
            WouldWrite(
              Agents.WouldWrite.What("patch [[x2020]]: summary"),
              Agents.WouldWrite.Payload("""{"summary":"S"}""")
            )
          )
        )
      ),
      Nil
    )
    val md = AgentsMarkdown.run(d, withMessages = false)
    assert(md.startsWith("# Agent run r1 (dry run)"), md)
    assert(
      md.contains("| 8 | patch | code | 1 | done |  | 0/0 | 0.0000 | dry run: not written: patch [[x2020]]: summary |"),
      md
    )
    assert(
      md.contains("### patch: patch [[x2020]]: summary — dry run: not written\n\n```json\n{\"summary\":\"S\"}\n```"),
      md
    )
    assert(
      !AgentsMarkdown
        .run(
          d.modify(_.run).setTo(r.modify(_.dryRun).setTo(Agents.RunInfo.DryRun(false))).modify(_.steps).setTo(Nil),
          withMessages = false
        )
        .contains("dry run")
    )
