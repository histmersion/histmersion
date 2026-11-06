package com.histmersion.api.automation
import com.histmersion.api.{Markdown, Paging, Plural}
import com.histmersion.api.given
import com.histmersion.api.research.Article

object AgentsMarkdown:

  type CallShapeFirstCallTokens = _root_.com.histmersion.api.automation.Agents.StepInfo.FirstCallTokens
  val CallShapeFirstCallTokens: _root_.com.histmersion.api.automation.Agents.StepInfo.FirstCallTokens.type = _root_.com.histmersion.api.automation.Agents.StepInfo.FirstCallTokens
  type CallShapeTurns = _root_.com.histmersion.api.automation.Agents.StepInfo.Turns
  val CallShapeTurns: _root_.com.histmersion.api.automation.Agents.StepInfo.Turns.type = _root_.com.histmersion.api.automation.Agents.StepInfo.Turns
  type ModelsActual = _root_.com.histmersion.api.automation.RunCompare.StepSide.Actual
  val ModelsActual: _root_.com.histmersion.api.automation.RunCompare.StepSide.Actual.type = _root_.com.histmersion.api.automation.RunCompare.StepSide.Actual
  type PaidCostUsd = _root_.com.histmersion.api.automation.RunCompare.RunSide.CostUsd
  val PaidCostUsd: _root_.com.histmersion.api.automation.RunCompare.RunSide.CostUsd.type = _root_.com.histmersion.api.automation.RunCompare.RunSide.CostUsd
  type PaidBilling = _root_.com.histmersion.api.automation.RunCompare.StepSide.Billing
  val PaidBilling: _root_.com.histmersion.api.automation.RunCompare.StepSide.Billing.type = _root_.com.histmersion.api.automation.RunCompare.StepSide.Billing

  import Agents.*

  def usd(d: Double): String =
    val scale = if d != 0 && math.abs((d)) < 0.0001 then 6 else 4
    (BigDecimal((d)).setScale(scale, BigDecimal.RoundingMode.HALF_UP).toString)

  def callShape(firstCallTokens: Option[com.histmersion.api.automation.AgentsMarkdown.CallShapeFirstCallTokens], turns: Option[com.histmersion.api.automation.AgentsMarkdown.CallShapeTurns]): String =
    (firstCallTokens.map(n => s"first call $n tok") ++ turns.map(t => Plural.count(t, _root_.com.histmersion.api.Plural.Noun.Singular("turn")))).mkString(" · ")

  private def tokens(u: Usage): String =
    val cache = if (u.cacheRead).value + u.cacheWrite > 0 then s", cache ${u.cacheRead} read / ${u.cacheWrite} written" else ""
    (s"${u.input} in / ${u.output} out$cache")

  def models(m: ModelsInfo): String =
    val head = s"# Embedded agents — ${if ((m.enabled) == _root_.com.histmersion.api.automation.Agents.ModelsInfo.Enabled(true)) then "enabled" else "OFF"}\n\n" +
      s"Pipelines: ${m.pipelines.mkString(", ")} · at most ${m.maxConcurrentRuns} run(s) at once\n\n"
    val problems = if m.problems.isEmpty then "" else "\n\n" + m.problems.map(p => s"- $p").mkString("\n")
    if m.chains.isEmpty then
      val rows = m.roles.map { r =>
        s"| ${r.role} | ${r.model.getOrElse("—")} | ${if ((r.ready) == _root_.com.histmersion.api.automation.Agents.RoleModel.Ready(true)) then "ready" else r.problem.getOrElse("not ready")} |"
      }
      (head + "| role | model | state |\n|---|---|---|\n" + rows.mkString("\n") + problems)
    else
      val rows = m.chains.map { c =>
        val chain = if c.models.isEmpty then "—" else c.models.map(chainEntry).mkString(" → ")
        val now =
          c.active.getOrElse(if c.models.isEmpty then c.problem.getOrElse("no model") else "**none: would pause**")
        s"| ${c.role} | $chain | $now | ${c.source} |"
      }
      val allowances =
        if m.allowances.isEmpty then
          "\n\n_No allowance: every model may be used until the run's and the day's budgets._"
        else "\n\n## Allowances\n\n" + m.allowances.map(allowanceBlock).mkString("\n")
      (head + "| role | chain (tried in order) | next call uses | from |\n|---|---|---|---|\n" + rows.mkString("\n") +
        allowances + problems)

  private def chainEntry(e: ModelChains.ChainEntry): String =
    val mark =
      if ((e.usable) == _root_.com.histmersion.api.automation.ModelChains.ChainEntry.Usable(true)) then ""
      else if !((e.ready) == _root_.com.histmersion.api.automation.ModelChains.ChainEntry.Ready(true)) then " (not ready)"
      else if ((e.cooling) == _root_.com.histmersion.api.automation.ModelChains.ChainEntry.Cooling(true)) then
        " (cooling down" + e.freesAt.fold("")(t => s" until $t") + ": its plan reported its usage limit)"
      else " (paused" + e.freesAt.fold(")")(t => s" until $t)")
    (s"`${e.model}`$mark${e.billing.fold("")(b => s" ($b)")}")

  def roleChain(c: ModelChains.RoleChain): String =
    val lines = c.models.zipWithIndex.map { (e, i) =>
      val state = if ((e.usable) == _root_.com.histmersion.api.automation.ModelChains.ChainEntry.Usable(true)) then "usable"
      else if !((e.ready) == _root_.com.histmersion.api.automation.ModelChains.ChainEntry.Ready(true)) then "not ready"
      else if ((e.cooling) == _root_.com.histmersion.api.automation.ModelChains.ChainEntry.Cooling(true)) then "cooling down"
      else "below its allowance"
      s"${i + 1}. `${e.model}` — $state${e.problem.fold("")(p => s": $p")}${e.freesAt.fold("")(t => s" (frees $t)")}" +
        e.billing.fold("")(b => s" · $b")
    }
    (s"**${c.role}** (${c.source}) — next call uses ${c.active.fold("none: a run would pause")(m => s"`$m`")}\n\n" +
      (if lines.isEmpty then c.problem.getOrElse("no model") else lines.mkString("\n")))

  private def amount(v: Double, m: ModelChains.Measure): String = m match
    case ModelChains.Measure.Tokens =>
      ModelChains.Limit.compact(BigDecimal((v)).setScale(0, BigDecimal.RoundingMode.HALF_UP))
    case ModelChains.Measure.Usd => ((usd(((v)))))

  private def allowanceBlock(a: ModelChains.AllowanceStatus): String =
    val state = if ((a.usable) == _root_.com.histmersion.api.automation.ModelChains.AllowanceStatus.Usable(true)) then "has headroom"
    else s"**below its reserve**${a.freesAt.fold(" (does not free by itself)")(t => s" until $t")}"
    val limits = a.limits.map { l =>
      s"  - ${l.limit}: ${amount(l.used, l.measure)} used, ${amount(l.remaining, l.measure)} left" +
        (if l.reserve > 0 then s", reserve ${amount(l.reserve, l.measure)}" else "") +
        l.freesAt.fold("")(t => s" — frees $t")
    }
    ((s"- `${a.target}` (${a.source}${if (a.reservePct).value > 0 then s", reserve ${a.reservePct}%" else ""}) — $state" +
      a.note.fold("")(n => s" · $n") :: limits).mkString("\n"))

  def allowance(a: ModelChains.AllowanceStatus): String = ((allowanceBlock(a)))

  private def pct(d: Double): String =
    (java.math.BigDecimal((d)).setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros.toPlainString)

  private def age(s: Long): String =
    if (s) < 120 then (s"$s s")
    else if (s) < 7200 then (s"${(s) / 60} min")
    else if (s) < 172800 then (s"${(s) / 3600} h")
    else (s"${(s) / 86400} d")

  def pool(p: PlanQuota.PoolStatus): String =
    val state =
      if !((p.available) == _root_.com.histmersion.api.automation.PlanQuota.PoolStatus.Available(true)) then s"**${p.reason.getOrElse("unavailable: no login")}**"
      else if ((p.held) == _root_.com.histmersion.api.automation.PlanQuota.PoolStatus.Held(true)) then "**held** — no agent use until released"
      else if ((p.usable) == _root_.com.histmersion.api.automation.PlanQuota.PoolStatus.Usable(true)) then "usable" + p.reason.fold("")(r => s" ($r)")
      else s"**paused** — ${p.reason.getOrElse("below its floor")}" + p.until.fold("")(u => s"; frees at $u")
    val windows =
      if p.windows.isEmpty then "\n\n_No reading yet: unknown until a run or a fresh reading reads it._"
      else
        "\n\n| window | used | left | floor | resets | read | from |\n|---|---|---|---|---|---|---|\n" +
          p.windows
            .map { w =>
              val name = if w.limit == _root_.com.histmersion.api.automation.PlanQuota.Reading.Limit("claude") || w.limit == _root_.com.histmersion.api.automation.PlanQuota.Reading.Limit("codex") then w.kind else s"${w.limit}/${w.kind}"
              val left = if ((w.usable) == _root_.com.histmersion.api.automation.PlanQuota.WindowStatus.Usable(true)) then s"${pct(w.leftPct)}%" else s"**${pct(w.leftPct)}%**"
              s"| $name | ${pct(w.usedPct)}% | $left | ${w.floor}% | ${w.resetsAt.getOrElse("—")} | " +
                s"${age(w.ageSeconds)} ago${if w.stale then " (stale)" else ""} | ${w.source} |"
            }
            .mkString("\n")
    s"### ${p.title} (`${p.pool}`) — $state\n\nCovers ${p.covers.map(c => s"`$c`").mkString(", ")} · floors " +
      s"${if p.floors.isEmpty then "none" else p.floors} (${p.floorsSource})" +
      s" · ${p.maxCallsInFlight} call(s) at once${p.planType.fold("")(t => s" · plan $t")}" +
      p.note.fold("")(n => s"\n\n_${n}_") + windows + refreshLine(p)

  private def refreshLine(p: PlanQuota.PoolStatus): String =
    val how = p.refresh.fold("no fresh reading is taken (refresh off)")(m => s"fresh reading by $m")
    val times = if p.refreshesToday == _root_.com.histmersion.api.automation.PlanQuota.PoolStatus.RefreshesToday(1) then "once" else s"${p.refreshesToday} times"
    val last = p.lastRefresh.fold("")(r =>
      s"; last ${r.at} (${r.trigger}, ${r.mode}): ${if ((r.ok) == _root_.com.histmersion.api.automation.PlanQuota.RefreshInfo.Ok(true)) then "" else "failed — "}${r.detail}"
    )
    (s"\n\nLogin: ${p.login.getOrElse("—")} · $how · refreshed $times today$last")

  def refreshed(q: PlanQuota.QuotaRefreshed): String =
    (q.pools
      .map(r => s"**${r.pool}**: ${if ((r.refreshed) == _root_.com.histmersion.api.automation.PlanQuota.PoolRefreshed.Refreshed(true)) then "read" else "no reading"} — ${r.detail}\n\n${pool(r.status)}")
      .mkString("\n\n") +
      (if q.lifted.isEmpty then ""
       else
         s"\n\nBack in use (the fresh reading lifted the cool-down after a reported limit): ${q.lifted.map(m => s"`$m`").mkString(", ")}"))

  def quota(q: PlanQuota.QuotaInfo): String =
    val metered =
      if q.metered.isEmpty then "_No metered allowance: API keys are capped only by the run's and the day's budgets._"
      else q.metered.map(allowanceBlock).mkString("\n")
    val fall =
      if q.fallthrough.isEmpty then
        "No chain falls from a plan below its floor to a paid key: the run pauses until the reset."
      else s"May fall from a plan below its floor to a paid key: ${q.fallthrough.mkString(", ")}."
    val cooling =
      if q.cooldowns.isEmpty then ""
      else
        "\n\n## Cooling down\n\n" + q.cooldowns
          .map(c =>
            s"- `${c.model}` — ${c.reason}; skipped until ${c.until} " +
              "unless a reading of its plan taken since says it is usable (`histmersion agent quota refresh <pool>`)"
          )
          .mkString("\n")
    (s"# Agent quota (${q.now})\n\n## Plans\n\n" + q.plans.map(pool).mkString("\n\n") + cooling +
      s"\n\n## Metered\n\n$metered\n\n$fall")

  def generated(r: GenerateResult): String =
    s"Generated ${r.runtimes.mkString(", ")} in ${r.dir}: ${r.files} file(s), ${r.written} written, ${r.removed} removed " +
      s"(definitions ${r.definitions.take(12)})."

  def doctor(d: DoctorReport): String =
    def mark(l: CheckLevel) = l match
      case CheckLevel.Ok   => "ok"
      case CheckLevel.Warn => "**warn**"
      case CheckLevel.Fail => "**FAIL**"
    def checks(cs: List[DoctorCheck]) = cs.map(c => s"- ${mark(c.level)} · ${c.name}: ${c.detail}").mkString("\n")
    val provs = d.providers.map { p =>
      val use = if ((p.used) == _root_.com.histmersion.api.automation.Agents.ProviderDoctor.Used(true)) then s"used by: ${p.models.mkString(", ")}" else "no role uses it"
      s"## ${p.provider} — ${mark(p.level)} ($use)\n\n${checks(p.checks)}" +
        (if p.upstreamModels.isEmpty then ""
         else
           s"\n\nUpstream lists: ${p.upstreamModels.take(30).mkString(", ")}" +
             (if p.upstreamModels.size > 30 then s" … (${p.upstreamModels.size})" else ""))
    }
    (s"# Agent doctor${if ((d.online) == _root_.com.histmersion.api.automation.Agents.DoctorReport.Online(true)) then "" else " (offline)"}\n\n${checks(d.checks)}\n\n" + provs.mkString("\n\n"))

  def probe(p: ProbeResult): String =
    val verdict =
      if p.answer.isEmpty then
        "**no typed answer**" + (if p.problems.isEmpty then "" else s": ${p.problems.mkString("; ")}")
      else if p.problems.nonEmpty then s"answer is **incomplete**: ${p.problems.mkString("; ")}"
      else if p.matchesRecord then "answer **matches** the record"
      else "answer does **not** match the record"
    val tries = if p.attempts > 1 then s" · ${p.attempts} attempts" else ""
    s"# Probe — ${p.model}\n\n$verdict$tries · ${p.iterations} iteration(s) · ${p.finish} · ${p.millis} ms · " +
      s"${tokens(p.usage)}${p.costUsd.fold(" · unpriced")(c => s" · ${usd(c)} USD")}\n\n" +
      (if p.toolCalls.isEmpty then "No tool was called.\n\n"
       else p.toolCalls.map(c => s"- tool: `$c`").mkString("", "\n", "\n\n")) +
      s"```json\n${p.rawAnswer}\n```"

  def options(r: RunInfo): String =
    List(
      Option.when(((r.dryRun) == _root_.com.histmersion.api.automation.Agents.RunInfo.DryRun(true)))("dry run, writes nothing"),
      Option.when(r.params.overrides.nonEmpty)(r.params.overrides.map(_.text).mkString(", ")),
      Option.when(((r.params.fixes) == (true)))("fix"),
      r.params.rounds.map(n => Plural.count(n, _root_.com.histmersion.api.Plural.Noun.Singular("round"))),
      r.acquireItem.map(i => s"for acquisition item $i"),
      r.params.query.map(q => s"query \"$q\""),
      r.params.topics.filter(_.nonEmpty).map(ts => s"topics ${ts.mkString(", ")}"),
      r.params.tags.filter(_.nonEmpty).map(ts => s"tags ${ts.mkString(", ")}"),
      r.parent.map(p => s"started by run ${p.take(8)}"),
      r.origin.filter(_ == Origin.Scheduler.wire).map(_ => "scheduled")
    ).flatten.mkString(", ")

  val DryRunNote = "dry run: not written"

  def runLine(r: RunInfo): String =
    val budget = s"${usd(r.costUsd)}/${usd(r.budgetUsd)} USD"
    val opts = Option(options(r)).filter((_).nonEmpty).fold("")(o => s" ($o)")
    val goal = r.goal.fold("")(g => s" · goal $g")
    s"${r.id} · ${r.status} · ${r.pipeline} ${r.target}$opts$goal · ${tokens(r.usage)} · $budget" +
      r.progress.filter(_.nonEmpty).fold("")(p => s" · $p") + r.error.filter(_.nonEmpty).fold("")(e => s" · $e")

  def runs(
      rs: List[RunInfo],
      page: Paging.Page = Paging.Page.empty,
      status: Option[com.histmersion.api.automation.Agents.RunInfo.Status] = None,
      older: Option[com.histmersion.api.Paging.Cursor] = None
  ): String =
    if rs.isEmpty then (status.fold("_No agent runs._")(s => s"_No $s agent runs._"))
    else
      ("# Agent runs\n\n" + pageLine(_root_.com.histmersion.api.Paging.Shown(rs.size), page, ("run"), (status).map(refactorValue15 => ((refactorValue15).value))) + facetLines(page, (RunFacets).map(refactorValue17 => { val refactorTuple14 = refactorValue17; (((refactorTuple14._1)), ((refactorTuple14._2))) })) +
        rs.map(r => s"- ${runLine(r)}").mkString("\n") + older.fold("")(o => s"\n\n$o"))

  private def pageLine(shown: com.histmersion.api.Paging.Shown, page: Paging.Page, singular: String, qualifier: Option[String]): String =
    if page.total <= shown then ("") else s"${Paging.heading(shown, page.total, singular, qualifier = qualifier)}.\n\n"

  val RunFacets: List[(String, String)] =
    List(
      { val refactorTuple15 = "pipeline" -> "By pipeline"; ((refactorTuple15._1), (refactorTuple15._2)) },
      { val refactorTuple2 = "origin" -> "By origin"; ((refactorTuple2._1), (refactorTuple2._2)) },
      { val refactorTuple3 = "manager" -> "By manager"; ((refactorTuple3._1), (refactorTuple3._2)) },
      { val refactorTuple4 = "provider" -> "By provider"; ((refactorTuple4._1), (refactorTuple4._2)) },
      { val refactorTuple5 = "model" -> "By model"; ((refactorTuple5._1), (refactorTuple5._2)) }
    )

  val ApprovalFacets: List[(String, String)] = List({ val refactorTuple6 = "kind" -> "By kind"; ((refactorTuple6._1), (refactorTuple6._2)) }, { val refactorTuple21 = "decidedBy" -> "Decided by"; ((refactorTuple21._1), (refactorTuple21._2)) })

  def facetLines(page: Paging.Page, facets: List[(String, String)]): String =
    val lines = facets.map((name, label) => ListFilters.facetLine(label, page.facet(name))).filter(_.nonEmpty)
    if lines.isEmpty then ("") else (lines.mkString("  \n") + "\n\n")

  def run(d: RunDetail, withMessages: Boolean): String =
    val r = d.run
    val head = s"# Agent run ${r.id}${if ((r.dryRun) == _root_.com.histmersion.api.automation.Agents.RunInfo.DryRun(true)) then " (dry run)" else ""}\n\n" +
      s"- pipeline: ${r.pipeline} · target: ${r.target}${r.model.fold("")(m => s" · model: $m")}" +
      Option(options(r)).filter((_).nonEmpty).fold("")(o => s" · $o") + "\n" +
      s"- status: **${r.status}**${r.error.filter(_.nonEmpty).fold("")(e => s" — $e")}\n" +
      r.progress.filter(_.nonEmpty).fold("")(p => s"- last: $p\n") +
      s"- spent: ${tokens(r.usage)} · ${usd(r.costUsd)} of ${usd(r.budgetUsd)} USD · budget ${r.budgetTokens} tokens\n" +
      s"- started ${r.createdAt}, updated ${r.updatedAt}\n"
    val warnings =
      if r.warnings.isEmpty then "" else "\n## Warnings\n\n" + r.warnings.map(w => s"- $w").mkString("\n") + "\n"
    val steps =
      "\n## Steps\n\n| # | step | kind | try | status | model | tokens | USD | note |\n|---|---|---|---|---|---|---|---|---|\n" +
        d.steps
          .map { s =>
            val note = (s.error.toList ++ s.wouldWrite.map(w => s"$DryRunNote: ${w.what}"))
              .map(_.replace("|", "\\|").replace("\n", " ").take(160))
              .mkString("; ")
            val shape = callShape(s.firstCallTokens, s.turns)
            s"| ${s.seq} | ${s.name} | ${s.kind} | ${s.attempt} | ${s.status} | ${s.modelText} | " +
              s"${s.usage.input}/${s.usage.output}${if (shape).isEmpty then "" else s" · $shape"} | ${usd(s.costUsd)} | $note |"
          }
          .mkString("\n")
    val skipped = d.steps.filter(_.wouldWrite.nonEmpty)
    val writes =
      if skipped.isEmpty then ""
      else
        "\n\n## Not written (dry run)\n\n" + skipped
          .flatMap(s =>
            s.wouldWrite.map { w =>
              s"### ${s.name}: ${w.what} — $DryRunNote\n\n```json\n${w.payload}\n```"
            }
          )
          .mkString("\n\n")
    val msgs =
      if !(withMessages) then ""
      else
        "\n\n## Messages\n\n" + d.messages
          .map { m =>
            val body = m.content.orElse(m.toolResult).orElse(m.toolInput).getOrElse("")
            val tool = m.toolName.fold("")(t => s" `$t`")
            val ms = m.latencyMs.fold("")(l => s" ($l ms)")
            s"### step ${m.seq} · ${m.n} · ${m.role}$tool$ms\n\n```\n$body\n```"
          }
          .mkString("\n\n")
    head + warnings + steps + writes + msgs

  def change(a: ApprovalInfo): String =
    ApprovalKind.parse(a.kind) match
      case Some(ApprovalKind.StatusChange) =>
        (s"[[${a.citekey}]] status ${a.current.getOrElse("?")} → **${a.status.getOrElse("?")}**")
      case Some(ApprovalKind.Edge) =>
        (s"edge [[${a.citekey}]] **${a.edgeKind.getOrElse("?")}** [[${a.target.getOrElse("?")}]]")
      case Some(ApprovalKind.GoalPlan) =>
        val p = a.plan
        (s"plan for goal **${a.subject.getOrElse("?")}**: ${p.fold(0)(_.subGoals.size)} sub-goal(s), " +
          s"${p.fold(0)(_.queries.size)} query(ies), ${p.fold(0)(_.works.size)} work(s)")
      case Some(ApprovalKind.ArticlePublish) =>
        val what = a.article.fold("an article")(d =>
          if d.mode == Goal.Plans.DraftMode.Create then s"new article \"${d.title}\"" else "a section appended to"
        )
        (s"publish $what [[${a.subject.getOrElse("?")}]]")
      case Some(ApprovalKind.ArticleFilter) =>
        (s"record the coverage filter of [[${a.subject.getOrElse("?")}]]: ${a.filter.fold("?")(_.filter.line)}")
      case Some(ApprovalKind.ArticlePlan) =>
        (s"compile ${Plural.count(_root_.com.histmersion.api.Plural.CountN((a.articles.fold(0)(_.articles.size)).toLong), _root_.com.histmersion.api.Plural.CountSingular("new article"))} for goal **${a.subject.getOrElse("?")}**")
      case Some(ApprovalKind.HintEffect) =>
        s"manager **${a.subject.getOrElse(
            "?"
          )}**: ${a.hintEffect.fold("an action")(p => ManagersMarkdown.effect(p.effect))}" +
          a.hintEffect.fold("")(p =>
            s" (from hint #${p.hint}: \"${p.text.take(160)}\"${Option(p.by).filter(_.nonEmpty).fold("")(b => s", proposed by $b")})"
          )
      case None => (s"${a.kind} on ${a.subject.getOrElse(a.citekey)}")

  def approvalLine(a: ApprovalInfo): String =
    val run = a.runId.fold("")(r => s" · run ${r.take(8)}")
    val res = a.result.filter(_.nonEmpty).fold("")(r => s" · $r")
    (s"#${a.id} · ${a.state} · ${change(a)}$run$res")

  def approvals(
      as: List[ApprovalInfo],
      page: Paging.Page = Paging.Page.empty,
      state: Option[com.histmersion.api.automation.Approval.State.Wire] = None,
      older: Option[com.histmersion.api.Paging.Cursor] = None
  ): String =
    if as.isEmpty then ("_No approvals._")
    else
      "# Approvals\n\n" + pageLine(_root_.com.histmersion.api.Paging.Shown(as.size), page, ("approval"), (state).map(refactorValue16 => ((refactorValue16).value))) + facetLines(page, (ApprovalFacets).map(refactorValue2 => { val refactorTuple7 = refactorValue2; (((refactorTuple7._1)), ((refactorTuple7._2))) })) + as
        .map { a =>
          val quote = a.quote.filter(_.nonEmpty).fold("")(q => s"\n  > ${q.replace("\n", " ").take(400)}")
          val note = a.note.filter(_.nonEmpty).fold("")(n => s"\n  note: $n")
          val detail = a.plan
            .map(planBlock)
            .orElse(a.article.map(draftBlock(_, _root_.com.histmersion.api.Markdown.CharLimit(1500))))
            .orElse(a.filter.map(Article.Plans.filterBlock))
            .orElse(a.articles.map(Article.Plans.planBlock))
            .fold("")(b => "\n\n" + indent(b) + "\n")
          val evidence =
            if a.plan.isDefined || a.articles.isDefined then "" else s"\n  ${a.evidence.replace("\n", " ").take(400)}"
          s"- ${approvalLine(a)}$evidence$quote$note$detail"
        }
        .mkString("\n") + older.fold("")(o => s"\n\n$o")

  private def indent(b: String): String = ((b).linesIterator.map(l => if l.isEmpty then l else s"  $l").mkString("\n"))

  def planBlock(p: Goal.Plans.Plan): String =
    val goals = p.subGoals.map(g => s"- sub-goal `${g.slug}` — ${g.title}${g.notes.fold("")(n => s": $n")}")
    val queries = p.queries.map(q => s"- query: $q")
    val works = p.works.map { w =>
      val where = if ((w.inCorpus) == _root_.com.histmersion.api.automation.Goal.Plans.PlannedWork.InCorpus(true)) then s"in the corpus as [[${w.citekey.getOrElse("?")}]]"
      else if ((w.isOa) == _root_.com.histmersion.api.automation.Goal.Plans.PlannedWork.IsOa(true)) then "to acquire (open access)"
      else "to acquire"
      s"- ${w.title.getOrElse(w.doi)} (${w.year.fold("n.d.")(_.toString)}) doi:${w.doi} — $where${
          if w.why.isEmpty then "" else s": ${w.why}"
        }"
    }
    val dropped = p.dropped.map(d => s"- dropped: $d")
    (Option(p.rationale).filter(_.nonEmpty).toList ++ List((goals ++ queries ++ works ++ dropped).mkString("\n")))
      .filter(_.nonEmpty)
      .mkString("\n\n")

  def draftBlock(d: Goal.Plans.ArticleDraft, chars: com.histmersion.api.Markdown.CharLimit): String =
    val head = s"${d.mode.wire} · ${d.kind} · \"${d.title}\"${d.section.fold("")(s => s" · under '$s'")} · " +
      s"cites ${d.cited.size} source(s) · ${d.filter}"
    val problems =
      if d.problems.isEmpty then "self-check: clean" else d.problems.map(p => s"- self-check: $p").mkString("\n")
    val text = if (chars).value > 0 && d.text.length > chars then
      d.text.take(chars) + s"\n… (${d.text.length - chars} more characters)"
    else d.text
    (s"$head\n\n$problems\n\n```markdown\n$text\n```")

  def scheduleLine(s: Goal.Plans.GoalSchedule): String =
    val state = if ((s.enabled) == _root_.com.histmersion.api.automation.Goal.Plans.GoalSchedule.Enabled(true)) then "on" else "off"
    val budget = s.budgetUsd.fold("")(b => s" · cap ${usd(b)} USD")
    s"${s.goal} · ${s.kind.wire} · `${s.cron}` · $state$budget" +
      s.nextRunAt.fold("")(n => s" · next $n") + s.lastRun.fold("")(r => s" · last run ${r.take(8)}") +
      s.lastNote.filter(_.nonEmpty).fold("")(n => s" · $n")

  def schedules(ss: List[Goal.Plans.GoalSchedule]): String =
    if ss.isEmpty then ("_No schedules._") else ("# Schedules\n\n" + ss.map(s => s"- ${scheduleLine(s)}").mkString("\n"))

  def linkLine(l: Goal.Plans.GoalLink): String =
    val ref = l.kind match
      case Goal.Plans.LinkKind.Source | Goal.Plans.LinkKind.Article => s"[[${l.ref}]]"
      case Goal.Plans.LinkKind.QueueItem                            => s"queue item ${l.ref}"
      case Goal.Plans.LinkKind.Query                                => s"\"${l.ref}\""
    val title = l.title.fold(if l.kind == Goal.Plans.LinkKind.Query then "" else " (missing)")(t => s" — $t")
    s"${l.kind.wire}: $ref$title${l.note.filter(_.nonEmpty).fold("")(n => s" · $n")}"

  def goalView(v: Goal.Plans.GoalView): String =
    val children = if v.children.isEmpty then ""
    else
      "\n\n## Sub-goals\n\n" +
        v.children.map(c => s"- `${c.slug}` — ${c.title} _(${c.status})_").mkString("\n")
    val links = if v.links.isEmpty then "\n\n## Links\n\n_None._"
    else
      "\n\n## Links\n\n" +
        v.links.map(l => s"- ${linkLine(l)}").mkString("\n")
    val sch = if v.schedules.isEmpty then ""
    else
      "\n\n## Schedules\n\n" +
        v.schedules.map(s => s"- ${scheduleLine(s)}").mkString("\n")
    val budget = v.budgetUsd.fold("")(b => s"\n\nRun cap: ${usd(b)} USD")
    val plan = v.plan.fold("")(p => s"\n\n## Approved plan\n\n${planBlock(p)}")
    GoalsMarkdown.goal(v.goal) + budget + children + links + sch + plan

  private def cell(s: String): String = ((s).replace("|", "\\|").replace("\n", " "))

  private def secs(n: Long): String = if (n) < 120 then (s"$n s") else (s"${(n) / 60} min ${(n) % 60} s")

  private def models(configured: List[com.histmersion.api.automation.Agents.ModelRef.Wire], actual: List[com.histmersion.api.automation.AgentsMarkdown.ModelsActual]): String =
    val conf = configured.mkString(", ")
    val shown = actual.filterNot(a => configured.exists(c => _root_.com.histmersion.api.automation.AgentsMarkdown.ModelsActual((c).value.drop((c).value.indexOf(':') + 1)) == a))
    if conf.isEmpty && actual.isEmpty then ("—")
    else if shown.isEmpty then (conf)
    else (s"$conf → ${shown.mkString(", ")}")

  private def paid(costUsd: com.histmersion.api.automation.AgentsMarkdown.PaidCostUsd, billing: List[com.histmersion.api.automation.AgentsMarkdown.PaidBilling]): String =
    val plan = billing.filter((_).value.startsWith("plan pool"))
    if (costUsd).value > 0 || plan.isEmpty then
      s"${usd(costUsd)} USD" + (if plan.nonEmpty then s" + ${plan.mkString(", ")}" else "")
    else (plan.mkString(", "))

  private def ratio(n: Int, of: Int): String =
    if of == (0) then ("—") else s"$n/$of (${BigDecimal(((100.0 * (n) / (of)).value).value).setScale(0, BigDecimal.RoundingMode.HALF_UP)}%)"

  def compare(c: RunCompare.Comparison): String =
    import RunCompare.*
    val ids = c.runs.map(_.id.take(8))
    def table(rows: List[(String, List[String])]): String =
      s"| | ${ids.mkString(" | ")} |\n|---|${ids.map(_ => "---").mkString("|")}|\n" +
        rows.map((k, vs) => s"| $k | ${vs.map(cell).mkString(" | ")} |").mkString("\n")
    val runs = table(
      List(
        "status" -> c.runs.map(r => r.status + (if ((r.dryRun) == _root_.com.histmersion.api.automation.RunCompare.RunSide.DryRun(true)) then " (dry run)" else "")),
        { val refactorTuple22 = "model" -> c.runs.map(r =>
          (r.model.toList ++ r.modelFor.map(_.text)).mkString(", ") match
            case "" => "the configured roles"
            case m  => m
        ); ((refactorTuple22._1), (refactorTuple22._2).map(refactorValue19 => (refactorValue19))) },
        "tokens" -> c.runs.map(r => tokens(r.usage)),
        "USD" -> c.runs.map(r => usd(r.costUsd)),
        "time" -> c.runs.map(r => secs(r.seconds)),
        { val refactorTuple8 = "attempts" -> c.runs.map(r => s"${r.attempts}${if (r.failures).value > 0 then s" (${r.failures} failed)" else ""}"); ((refactorTuple8._1), (refactorTuple8._2).map(refactorValue4 => (refactorValue4))) }
      )
    )
    val steps = c.steps.map { row =>
      row.name -> row.runs.map(_.fold("—") { s =>
        val tries = if (s.attempts).value > 1 || (s.failures).value > 0 then
          s" · ${s.attempts} tries${if (s.failures).value > 0 then s", ${s.failures} failed" else ""}"
        else ""
        val m = models(s.models, s.actual)
        (if m == "—" then "code" else m) + s" · ${s.usage.input}/${s.usage.output}" +
          (if (s.usage.cacheRead).value + s.usage.cacheWrite > 0 then s" (cache ${s.usage.cacheRead}/${s.usage.cacheWrite})"
           else "") +
          (if s.models.nonEmpty then s" · ${paid(s.costUsd, s.billing)}" else "") + s" · ${secs(s.seconds)}$tries" +
          Option(callShape(s.firstCallTokens, s.turns)).filter(_.nonEmpty).fold("")(c => s" · $c")
      })
    }
    val enrich =
      if c.enrich.isEmpty then ""
      else
        val e = c.enrich
        "\n\n## Outputs\n\n" + table(
          List(
            { val refactorTuple10 = "findings" -> e.map(_.findings.toString); ((refactorTuple10._1), (refactorTuple10._2).map(refactorValue8 => (refactorValue8))) },
            "quote check passed" -> e.map(x => ratio(x.quotesPassed, x.quotesChecked)),
            "places resolved" -> e.map(x => ratio(x.placesResolved, x.placesAsked)),
            "dated findings" -> e.map(x => ratio(x.datedFindings, x.findings)),
            "period" -> e.map(x =>
              Markdown.periodLabel(x.periodStart, x.periodEnd) match
                case "" => "—"
                case p  => p
            ),
            { val refactorTuple11 = "summary" -> e.map(x => s"${x.summaryChars} chars"); ((refactorTuple11._1), (refactorTuple11._2).map(refactorValue11 => (refactorValue11))) },
            { val refactorTuple12 = "notes" -> e.map(x => s"${x.notesChars} chars"); ((refactorTuple12._1), (refactorTuple12._2).map(refactorValue12 => (refactorValue12))) }
          )
        ) +
          ids
            .zip(e)
            .map { (id, x) =>
              s"\n\n### Claims only in $id (${x.uniqueClaims.size})\n\n" +
                (if x.uniqueClaims.isEmpty then "_None._" else x.uniqueClaims.map(cl => s"- $cl").mkString("\n"))
            }
            .mkString
    val review =
      if c.review.isEmpty then ""
      else
        val rv = c.review
        val reviewers = rv.flatMap(_.reviewers.map(_.reviewer)).distinct
        val kinds = rv.flatMap(_.kinds.map(_.kind)).distinct
        "\n\n## Outputs\n\n" + table(
          List("verdict (last round)" -> rv.map(_.verdict.getOrElse("—")), "rounds" -> rv.map(_.rounds.toString)) ++
            reviewers.map(w =>
              s"$w (round 1)" -> rv.map(
                _.reviewers
                  .find(_.reviewer == w)
                  .fold("—")(x => x.verdict.fold("")(v => s"$v · ") + s"${x.majors} major, ${x.minors} minor")
              )
            ) ++
            kinds.map(k => s"kind: $k (round 1)" -> rv.map(_.kinds.find(_.kind == k).fold("0")(_.count.toString)))
        ) +
          (if c.overlaps.isEmpty then ""
           else
             "\n\n### Major findings in common (round 1)\n\n" + c.overlaps
               .map { o =>
                 s"- ${o.a.take(8)} × ${o.b.take(8)}: ${o.shared} shared of ${o.aMajors} and ${o.bMajors}" +
                   (if o.pairs.isEmpty then "" else s" (${o.pairs.mkString(", ")})")
               }
               .mkString("\n")) +
          ids
            .zip(rv)
            .map { (id, x) =>
              s"\n\n### Major findings of $id (round 1)\n\n" + (if x.majors.isEmpty then "_None._"
                                                                else x.majors.map(m => s"- $m").mkString("\n"))
            }
            .mkString
    val notes = if c.notes.isEmpty then "" else "\n\n" + c.notes.map(n => s"- $n").mkString("\n")
    s"# ${c.pipeline} on ${c.target}: ${c.runs.size} runs\n\n" + runs + "\n\n## Steps\n\n" + table(
      steps
    ) + enrich + review + notes
