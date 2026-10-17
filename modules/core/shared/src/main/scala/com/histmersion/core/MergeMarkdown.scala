package com.histmersion.core

/** How a merge plan reads (docs/research-merge-import.md §6.2) — the CLI, the REST answer's Markdown
  * and the MCP `merge_plan` tool print this, so the plan says the same thing everywhere. Pure. */
object MergeMarkdown:

  def who(i: BundleInstance): String = i.label.fold(i.id.toString.take(8) + "…")(l => s"$l (${i.id.toString.take(8)}…)")

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
      else if p.changes == 0 then "_Nothing is open, and nothing to change: the bundle is merged already._"
      else s"_Nothing is open: `histmersion merge apply <bundle> --resolutions … --plan ${p.planHash}` carries it out " +
        s"(${Plural.count(p.changes, "change")}; a backup is taken first)._"
    (head :: sections ++ List(next)).mkString("\n\n")

  /** What `merge apply` did (§6.4). */
  def applied(r: MergeApplyResult): String =
    val t = r.tally
    def count(k: BundleKind)(f: BundleKindCounts => Int): Int = r.counts.find(_.kind == k).fold(0)(f)
    val head =
      if r.applied && r.mergeId.isEmpty then s"**Nothing to merge** — the bundle of ${who(r.from)} is merged already (plan ${r.planHash})."
      else if r.applied then
        s"**Merged** the bundle of ${who(r.from)} as merge ${r.mergeId.fold("—")(_.toString)}" +
          r.backup.fold("")(b => s" (backup $b)")
      else s"**Not merged** (plan ${r.planHash}): nothing was written."
    val body = Option.when(r.applied && r.mergeId.isDefined)(List(
      s"**sources** +${count(BundleKind.Source)(_.inserted)} new · ${t.sourcesMerged} merged (${t.sourcesByScore} by score) · " +
        s"${t.sourcesRenamed} renamed · aliases +${t.aliases}",
      s"**findings** +${count(BundleKind.Finding)(_.inserted)} · merged ${t.findingsMerged} · " +
        s"**assets** +${count(BundleKind.Asset)(_.inserted)} · merged ${t.assetsMerged} · " +
        s"**attestations** re-anchored ${t.attestationsReanchored} · dropped ${t.attestationsDropped}",
      s"**articles** +${count(BundleKind.Article)(_.inserted)} new · ${t.articlesThreeWay} three-way · " +
        s"${t.articlesFastForward} fast-forward · ${t.articlesBoth} both · ${t.articlesFromFile} from a file",
      s"**events** merged ${t.eventsMerged} · **goals** merged ${t.goalsMerged} · **queue** merged ${t.queueMerged} · " +
        s"**places** +${count(BundleKind.Place)(_.inserted)} · merged ${t.placesMerged} · " +
        s"**periods** +${count(BundleKind.Period)(_.inserted)} · merged ${t.periodsMerged} · " +
        s"self-loops dropped ${t.selfLoopsDropped} · redirects +${t.redirects}",
      s"**queued** ${Plural.count(r.reviews.size, "/review-article task")}" +
        (if r.reviews.nonEmpty then r.reviews.map(s => s"`$s`").mkString(" (", ", ", ")") else "") +
        r.logEntry.fold("")(id => s" · log entry #$id"),
      r.replanChanges.fold("")(n =>
        if n == 0 then "**re-run** `histmersion merge plan` on this bundle → 0 changes (idempotent)"
        else s"**re-run** `histmersion merge plan` on this bundle → ${Plural.count(n, "change")} left (not idempotent — report it)")
    ).filter(_.nonEmpty).mkString("  \n")).toList
    val sections = List(
      section("Problems (nothing was written)", r.problems.map(x => s"- $x")),
      section("Open", r.issues.map(issue)),
      section("Notes", r.notes.map(x => s"- $x"))).flatten
    (head :: body ++ sections).mkString("\n\n")

  /** Where an apply job is (`POST /api/merge/apply`, `GET /api/merge/apply/{job}`). */
  def applyJob(j: MergeApplyJob): String =
    j.state match
      case MergeJobState.Running =>
        s"**Applying** plan ${j.planHash} — job `${j.job}`, ${j.elapsedMs / 1000} s so far (a backup is taken first)."
      case MergeJobState.Failed  => s"**The merge apply failed** (job `${j.job}`): ${j.error.getOrElse("no reason given")}"
      case MergeJobState.Interrupted =>
        s"**The merge apply was interrupted** (job `${j.job}`): ${j.error.getOrElse(MergeApplyJob.InterruptedText)}"
      case MergeJobState.Done    => j.result.fold(s"**Done** (job `${j.job}`).")(applied)

  /** `merge list`. */
  def merges(ms: List[MergeInfo]): String =
    if ms.isEmpty then "_No merges yet: nothing was imported from another collection._"
    else
      ("| merge | when | from | bundle | plan | ledger | redirects | backup |" ::
        "|---|---|---|---|---|---|---|---|" ::
        ms.map(m => s"| `${m.id.toString.take(8)}` | ${m.appliedAt.take(19).replace('T', ' ')} | ${who(m.from)} | " +
          s"`${m.bundleId.toString.take(8)}…` | ${m.planHash.fold("import")(h => s"`$h`")} | ${m.ledgerRows} | ${m.redirects} | " +
          s"${m.backup.getOrElse("—")} |")).mkString("\n")

  /** `merge show <id>`. */
  def detail(d: MergeDetail): String =
    val m = d.merge
    val head = List(
      s"**Merge** `${m.id}` · ${m.appliedAt} · from ${who(m.from)}",
      s"**bundle** `${m.bundleId}` (sha256 `${m.bundleSha256.take(12)}…`)" +
        m.planHash.fold(" · imported without matching")(h => s" · plan `$h`") + m.backup.fold("")(b => s" · backup $b"),
      s"**left** ${Plural.count(m.ledgerRows, "ledger row")} · ${Plural.count(m.redirects, "redirect")}"
    ).mkString("  \n")
    val report = d.applied.map(applied).orElse(d.imported.map(BundleMarkdown.importResult)).toList
    val res = d.resolutions.filter(r => r.resolve.nonEmpty || r.policy.editions.isDefined).map(r =>
      "**Resolutions**\n" + (r.policy.editions.map(e => s"- editions: ${e.toString.toLowerCase}").toList ++
        r.resolve.map(x => s"- `${x.issue}` = `${x.decision}`" + x.text.fold("")(t => s" (${t.length} characters of merged text)"))).mkString("\n"))
    val rem = Option.when(d.remembered.nonEmpty)("**Remembered decisions**\n" +
      d.remembered.map(x => s"- `${x.issue}` = `${x.decision}`").mkString("\n"))
    (head :: report ++ res.toList ++ rem.toList).mkString("\n\n")

  /** `merge ledger`. */
  def ledger(l: MergeLedger): String =
    val head = l.query.fold("**Merge ledger** (latest)")(q => s"**Merge ledger** of `$q`")
    val rows =
      if l.entries.isEmpty then List("_No ledger rows._")
      else "| kind | from (bundle uid) | here | rev merged | rev left | decision | merge | when |" ::
        "|---|---|---|---|---|---|---|---|" ::
        l.entries.map(e => s"| ${e.kind} | `${e.foreignUid}` | ${e.label.fold(s"`${e.localUid}`")(x => s"`$x`")} | " +
          s"`${e.foreignRev.take(8)}` | ${e.localRevAfter.fold("—")(r => s"`${r.take(8)}`")} | ${e.decision} | " +
          s"`${e.mergeId.toString.take(8)}` | ${e.recordedAt.take(19).replace('T', ' ')} |")
    val redirects = Option.when(l.redirects.nonEmpty)("**Redirects** (merged-away uid → the record here)\n" +
      l.redirects.map(r => s"- `${r.aliasUid}` → `${r.uid}` (${r.kind})").mkString("\n"))
    ((head :: rows.mkString("\n") :: Nil) ++ redirects.toList).mkString("\n\n")

  /** An article, event or goal renamed. */
  def renamed(r: LabelRenamed): String =
    val what = r.entity match
      case BundleKind.Article => "Article"
      case BundleKind.Event   => "Event"
      case BundleKind.Place   => "Place"
      case _                  => "Goal"
    s"**$what renamed** `${r.from}` → `${r.to}`; the old label stays an alias — reads and writes naming it go to `${r.to}`." +
      (if r.rewritten.nonEmpty then s" Links rewritten in ${r.rewritten.map(a => s"`$a`").mkString(", ")}." else "")

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
      i.decision.map(d => s"decided `${d.wire}`" + i.decidedBy.fold("")(by))
    ).flatten
    (s"- `${i.id}` ${i.entity.wire} — ${i.title}$cond" :: lines.map(l => s"  - $l")).mkString("\n")

  private def by(b: DecidedBy): String = b match
    case DecidedBy.Remembered => " as an earlier merge apply did"
    case other                => s" by ${other.toString.toLowerCase(java.util.Locale.ROOT)}"

  private def argHint(i: MergeIssue, k: DecisionKind): String = k match
    case DecisionKind.Map         => ":" + List(i.left, i.right).flatten.map(_.label).mkString("|")
    case DecisionKind.RenameRight | DecisionKind.RenameLeft | DecisionKind.File =>
      i.suggested.filter(_.kind == k).flatMap(_.argument).fold(":<value>")(a => s":$a")
    case _ => ""

  private def sourceMatch(s: SourceMatch): String =
    val target = s.leftCitekey.fold("")(c => s" → `$c`")
    val score  = s.score.fold("")(x => s" · score ${math.round(x * 10) / 10.0}")
    s"- `${s.rightCitekey}`$target · ${MatchOutcome.wireOf(s.outcome)}$score" + s.issue.fold("")(i => s" · $i")
