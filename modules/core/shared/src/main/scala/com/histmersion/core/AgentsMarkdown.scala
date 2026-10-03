package com.histmersion.core

/** Markdown for the embedded-agent surface — its own object, like [[IngestMarkdown]], so this stream
  * does not append to [[Markdown]]. Every surface answers Markdown. */
object AgentsMarkdown:
  import Agents.*

  /** Four decimals, locale-free (the wire is never localised; see AGENTS.md on `en_PL`). */
  def usd(d: Double): String =
    val scale = if d != 0 && math.abs(d) < 0.0001 then 6 else 4 // a micro-budget must not read as 0
    BigDecimal(d).setScale(scale, BigDecimal.RoundingMode.HALF_UP).toString

  private def tokens(u: Usage): String =
    val cache = if u.cacheRead + u.cacheWrite > 0 then s", cache ${u.cacheRead} read / ${u.cacheWrite} written" else ""
    s"${u.input} in / ${u.output} out$cache"

  def models(m: ModelsInfo): String =
    val head = s"# Embedded agents — ${if m.enabled then "enabled" else "OFF"}\n\n" +
      s"Pipelines: ${m.pipelines.mkString(", ")} · at most ${m.maxConcurrentRuns} run(s) at once\n\n"
    val rows = m.roles.map { r =>
      s"| ${r.role} | ${r.model.getOrElse("—")} | ${if r.ready then "ready" else r.problem.getOrElse("not ready")} |"
    }
    val problems = if m.problems.isEmpty then "" else "\n\n" + m.problems.map(p => s"- $p").mkString("\n")
    head + "| role | model | state |\n|---|---|---|\n" + rows.mkString("\n") + problems

  def probe(p: ProbeResult): String =
    val verdict =
      if p.answer.isEmpty then "**no typed answer**"
      else if p.matchesRecord then "answer **matches** the record"
      else "answer does **not** match the record"
    s"# Probe — ${p.model}\n\n$verdict · ${p.iterations} iteration(s) · ${p.finish} · ${p.millis} ms · " +
      s"${tokens(p.usage)}${p.costUsd.fold(" · unpriced")(c => s" · ${usd(c)} USD")}\n\n" +
      (if p.toolCalls.isEmpty then "No tool was called.\n\n" else p.toolCalls.map(c => s"- tool: `$c`").mkString("", "\n", "\n\n")) +
      s"```json\n${p.rawAnswer}\n```"

  /** A run's options, as words (`fix, 3 rounds`, `for acquisition item 12`); empty when it has none. */
  def options(r: RunInfo): String =
    List(
      Option.when(r.params.fixes)("fix"),
      r.params.rounds.map(n => s"$n round${if n == 1 then "" else "s"}"),
      r.acquireItem.map(i => s"for acquisition item $i")
    ).flatten.mkString(", ")

  def runLine(r: RunInfo): String =
    val budget = s"${usd(r.costUsd)}/${usd(r.budgetUsd)} USD"
    val opts   = Option(options(r)).filter(_.nonEmpty).fold("")(o => s" ($o)")
    s"${r.id} · ${r.status} · ${r.pipeline} ${r.target}$opts · ${tokens(r.usage)} · $budget" +
      r.progress.filter(_.nonEmpty).fold("")(p => s" · $p") + r.error.filter(_.nonEmpty).fold("")(e => s" · $e")

  def runs(rs: List[RunInfo]): String =
    if rs.isEmpty then "_No agent runs._"
    else "# Agent runs\n\n" + rs.map(r => s"- ${runLine(r)}").mkString("\n")

  def run(d: RunDetail, withMessages: Boolean): String =
    val r = d.run
    val head = s"# Agent run ${r.id}\n\n" +
      s"- pipeline: ${r.pipeline} · target: ${r.target}${r.model.fold("")(m => s" · model: $m")}" +
      Option(options(r)).filter(_.nonEmpty).fold("")(o => s" · $o") + "\n" +
      s"- status: **${r.status}**${r.error.filter(_.nonEmpty).fold("")(e => s" — $e")}\n" +
      r.progress.filter(_.nonEmpty).fold("")(p => s"- last: $p\n") +
      s"- spent: ${tokens(r.usage)} · ${usd(r.costUsd)} of ${usd(r.budgetUsd)} USD · budget ${r.budgetTokens} tokens\n" +
      s"- started ${r.createdAt}, updated ${r.updatedAt}\n"
    val warnings = if r.warnings.isEmpty then "" else "\n## Warnings\n\n" + r.warnings.map(w => s"- $w").mkString("\n") + "\n"
    val steps = "\n## Steps\n\n| # | step | kind | try | status | model | tokens | USD | note |\n|---|---|---|---|---|---|---|---|---|\n" +
      d.steps.map { s =>
        val note = s.error.map(_.replace("|", "\\|").replace("\n", " ").take(160)).getOrElse("")
        s"| ${s.seq} | ${s.name} | ${s.kind} | ${s.attempt} | ${s.status} | ${s.model.getOrElse("")} | " +
          s"${s.usage.input}/${s.usage.output} | ${usd(s.costUsd)} | $note |"
      }.mkString("\n")
    val msgs =
      if !withMessages then ""
      else "\n\n## Messages\n\n" + d.messages.map { m =>
        val body = m.content.orElse(m.toolResult).orElse(m.toolInput).getOrElse("")
        val tool = m.toolName.fold("")(t => s" `$t`")
        val ms   = m.latencyMs.fold("")(l => s" ($l ms)")
        s"### step ${m.seq} · ${m.n} · ${m.role}$tool$ms\n\n```\n$body\n```"
      }.mkString("\n\n")
    head + warnings + steps + msgs

  // ── approvals ──────────────────────────────────────────────────────────────────────────────────

  /** What an approval would change, in one line. */
  def change(a: ApprovalInfo): String =
    ApprovalKind.parse(a.kind) match
      case Some(ApprovalKind.StatusChange) =>
        s"[[${a.citekey}]] status ${a.current.getOrElse("?")} → **${a.status.getOrElse("?")}**"
      case Some(ApprovalKind.Edge) =>
        s"edge [[${a.citekey}]] **${a.edgeKind.getOrElse("?")}** [[${a.target.getOrElse("?")}]]"
      case None => s"${a.kind} on [[${a.citekey}]]"

  def approvalLine(a: ApprovalInfo): String =
    val run = a.runId.fold("")(r => s" · run ${r.take(8)}")
    val res = a.result.filter(_.nonEmpty).fold("")(r => s" · $r")
    s"#${a.id} · ${a.state} · ${change(a)}$run$res"

  def approvals(as: List[ApprovalInfo]): String =
    if as.isEmpty then "_No approvals._"
    else "# Approvals\n\n" + as.map { a =>
      val quote = a.quote.filter(_.nonEmpty).fold("")(q => s"\n  > ${q.replace("\n", " ").take(400)}")
      val note  = a.note.filter(_.nonEmpty).fold("")(n => s"\n  note: $n")
      s"- ${approvalLine(a)}\n  ${a.evidence.replace("\n", " ").take(400)}$quote$note"
    }.mkString("\n")
