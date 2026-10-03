package com.histmersion.core

/** How a merge plan reads (docs/research-merge-import.md §6.2) — the CLI, the REST answer's Markdown
  * and the MCP `merge_plan` tool print this, so the plan says the same thing everywhere. Pure. */
object MergeMarkdown:

  private def who(i: BundleInstance): String = i.label.fold(i.id.toString.take(8) + "…")(l => s"$l (${i.id.toString.take(8)}…)")

  /** `all` lists every default and info line, not only the first few. */
  def plan(p: MergePlan, all: Boolean = false): String =
    val m = p.matches
    val open        = p.issues.filter(i => i.severity == IssueSeverity.Blocking && i.condition.isEmpty && i.decision.isEmpty)
    val decided     = p.issues.filter(i => i.severity == IssueSeverity.Blocking && i.condition.isEmpty && i.decision.isDefined)
    val conditional = p.issues.filter(_.condition.isDefined)
    val defaults    = p.issues.filter(i => i.severity == IssueSeverity.Default && i.condition.isEmpty)
    val head = List(
      s"**Bundle** ${who(p.from)} → ${who(p.into)} · format ${p.formatVersion} · schema V${p.schemaVersion} · " +
        s"as of ${p.asOf.getOrElse("—")}",
      s"**Matched** ${Plural.count(m.sources, "source")}: ${m.byUid} by uid · ${m.byLedger} by ledger · " +
        s"${m.byStrongKey} by DOI/file/ISBN · ${m.byScore} by score · ${m.undecided} to decide · ${m.unmatched} new" +
        (if m.deletedHere > 0 then s" · ${m.deletedHere} deleted here" else ""),
      s"**Summary** ${open.size} blocking · ${p.resolutionErrors.size} stale or invalid · ${conditional.size} conditional · " +
        s"${defaults.size} ${Plural.noun(defaults.size, "default")} · ${p.info.size} info · " +
        s"${Plural.count(p.problems.size, "problem")} · plan ${p.planHash}" +
        (if p.resolved > 0 then s"+r(${p.resolved})" else "")
    ).mkString("  \n")
    val sections = List(
      section("Problems (no decision fixes these)", p.problems.map(x => s"- $x")),
      section("Resolutions not used (errors)", p.resolutionErrors.map(x => s"- $x")),
      section("Blocking", open.map(issue)),
      section("Decided", decided.map(issue)),
      section("Conditional (listed now, so no new issue appears after a decision)", conditional.map(issue)),
      section("Defaults (applied unless overridden with --resolve)",
        (if all then defaults else defaults.take(5)).map(issue) ++
          Option.when(!all && defaults.size > 5)(s"- … ${defaults.size - 5} more (--all)").toList),
      section("Info", (if all then p.info else p.info.take(8)).map(x => s"- $x") ++
        Option.when(!all && p.info.size > 8)(s"- … ${p.info.size - 8} more (--all)").toList),
      section("Matches", p.sources.filter(_.outcome != MatchOutcome.New).take(if all then Int.MaxValue else 20).map(sourceMatch))
    ).flatten
    val next =
      if p.problems.nonEmpty then "_Next: fix the problems, export the bundle again._"
      else if open.nonEmpty || p.resolutionErrors.nonEmpty then
        "_Next: `histmersion merge plan <bundle> --write-template resolutions.yaml`, decide, then plan again with `--resolutions resolutions.yaml`._"
      else "_Nothing is open: `histmersion bundle import <bundle> --resolutions …` imports what an import can carry out._"
    (head :: sections ++ List(next)).mkString("\n\n")

  private def section(title: String, lines: List[String]): Option[String] =
    Option.when(lines.nonEmpty)(s"**$title**\n" + lines.mkString("\n"))

  def issue(i: MergeIssue): String =
    val cond = i.condition.fold("")(c => s" _(if ${c.issue} = ${DecisionKind.wireOf(c.when)})_")
    val lines = List(
      i.left.map(s => s"here `${s.label}`" + s.lines.map(l => s" · $l").mkString),
      i.right.map(s => s"bundle `${s.label}`" + s.lines.map(l => s" · $l").mkString),
      Option.when(i.why.nonEmpty)("why " + i.why.mkString(" · ")),
      Option.when(i.options.nonEmpty)("decide " + i.options.map(k => s"`${DecisionKind.wireOf(k)}${argHint(i, k)}`").mkString(" | ") +
        i.suggested.filter(_ => i.decision.isEmpty).fold("")(s => s" (suggested `${s.wire}`)")),
      i.decision.map(d => s"decided `${d.wire}`" + i.decidedBy.fold("")(b => s" by ${b.toString.toLowerCase}"))
    ).flatten
    (s"- `${i.id}` ${i.entity.wire} — ${i.title}$cond" :: lines.map(l => s"  - $l")).mkString("\n")

  private def argHint(i: MergeIssue, k: DecisionKind): String = k match
    case DecisionKind.Map         => ":" + List(i.left, i.right).flatten.map(_.label).mkString("|")
    case DecisionKind.RenameRight | DecisionKind.RenameLeft | DecisionKind.File =>
      i.suggested.filter(_.kind == k).flatMap(_.argument).fold(":<value>")(a => s":$a")
    case _ => ""

  private def sourceMatch(s: SourceMatch): String =
    val target = s.leftCitekey.fold("")(c => s" → `$c`")
    val score  = s.score.fold("")(x => s" · score ${math.round(x * 10) / 10.0}")
    s"- `${s.rightCitekey}`$target · ${MatchOutcome.wireOf(s.outcome)}$score" + s.issue.fold("")(i => s" · $i")
