package com.histmersion.api.exchange
import com.histmersion.api.library.MatchOutcome
import com.histmersion.api.{Plural, RegisteredPlanMatches}
import com.histmersion.api.aliases.LabelRenamed
import com.histmersion.api.given

object MergeMarkdown:
  type AppliedCount = AppliedCount.T
  object AppliedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]



  def who(i: Bundle.Instance): String =
    (i.label.fold(i.id.toString.take(8) + "…")(l => s"$l (${i.id.toString.take(8)}…)"))

  def plan(p: MergePlan, all: Boolean = (false)): String =
    val m = p.matches
    val open =
      p.issues.filter(i => i.severity == MergeIssue.Severity.Blocking && i.condition.isEmpty && i.decision.isEmpty)
    val decided =
      p.issues.filter(i => i.severity == MergeIssue.Severity.Blocking && i.condition.isEmpty && i.decision.isDefined)
    val conditional = p.issues.filter(_.condition.isDefined)
    val defaults = p.issues.filter(i => i.severity == MergeIssue.Severity.Default && i.condition.isEmpty)
    val head = List(
      s"**Bundle** ${who(p.from)} → ${who(p.into)} · format ${p.formatVersion} · schema V${p.schemaVersion} · " +
        s"as of ${p.asOf.getOrElse("—")}",
      s"**Matched** ${Plural.count(m.sources, _root_.com.histmersion.api.Plural.Noun.Singular("source"))}: ${m.byUid} by uid · ${m.byLedger} by ledger · " +
        s"${m.byStrongKey} by DOI/file/ISBN · ${m.byScore} by score · ${m.undecided} to decide · ${m.unmatched} new" +
        (if m.deletedHere > 0 then s" · ${m.deletedHere} deleted here" else ""),
      s"**Summary** ${open.size} blocking · ${p.resolutionErrors.size} stale or invalid · ${conditional.size} conditional · " +
        s"${defaults.size} ${Plural.noun(_root_.com.histmersion.api.Plural.Count((defaults.size).toLong), _root_.com.histmersion.api.Plural.Noun.Singular("default"))} · ${p.info.size} info · " +
        s"${Plural.count(_root_.com.histmersion.api.Plural.Count((p.problems.size).toLong), _root_.com.histmersion.api.Plural.Noun.Singular("problem"))} · plan ${p.planHash}" +
        (if (p.resolved).value > 0 then s"+r(${p.resolved})" else "")
    ).mkString("  \n")
    val sections = List(
      section(("Problems (no decision fixes these)"), p.problems.map(x => (s"- $x"))),
      section(("Resolutions not used (errors)"), p.resolutionErrors.map(x => (s"- $x"))),
      section(("Blocking"), open.map(((issue).value))),
      section(("Decided"), decided.map(((issue).value))),
      section(("Conditional (listed now, so no new issue appears after a decision)"), conditional.map(((issue).value))),
      section(
        ("Defaults (applied unless overridden with --resolve)"),
        (if ((all) == (true)) then defaults else defaults.take(5)).map(issue) ++
          Option.when(!(all).value && defaults.size > 5)(s"- … ${defaults.size - 5} more (--all)").toList
      ),
      section(
        ("Info"),
        ((if ((all) == (true)) then p.info else p.info.take(8)).map(x => s"- $x") ++
          Option.when(!(all).value && p.info.size > 8)(s"- … ${p.info.size - 8} more (--all)").toList).map(refactorValue5 => (refactorValue5))
      ),
      section(
        ("Matches"),
        RegisteredPlanMatches.kind.lines(p.sources, if ((all) == (true)) then _root_.com.histmersion.api.exchange.PlanMatchKind.LineLimit(Int.MaxValue) else _root_.com.histmersion.api.exchange.PlanMatchKind.LineLimit(20))
      )
    ).flatten
    val next =
      if p.problems.nonEmpty then "_Next: fix the problems, export the bundle again._"
      else if open.nonEmpty || p.resolutionErrors.nonEmpty then
        "_Next: `histmersion merge plan <bundle> --write-template resolutions.yaml`, decide, then plan again with `--resolutions resolutions.yaml`._"
      else if p.changes == _root_.com.histmersion.api.exchange.MergePlan.Changes(0) then "_Nothing is open, and nothing to change: the bundle is merged already._"
      else
        s"_Nothing is open: `histmersion merge apply <bundle> --resolutions … --plan ${p.planHash}` carries it out " +
          s"(${Plural.count(p.changes, _root_.com.histmersion.api.Plural.CountSingular("change"))}; a backup is taken first)._"
    (head :: sections ++ List(next)).mkString("\n\n")

  def applied(r: MergeApplyResult): String =
    val t = r.tally
    def count(k: com.histmersion.api.exchange.Bundle.Kind)(f: Bundle.KindCounts => com.histmersion.api.exchange.MergeMarkdown.AppliedCount): com.histmersion.api.exchange.MergeMarkdown.AppliedCount =
      r.counts.find(_.kind == k).fold(0)(f)
    val head =
      if ((r.applied) == _root_.com.histmersion.api.exchange.MergeApplyResult.Applied(true)) && r.mergeId.isEmpty then
        s"**Nothing to merge** — the bundle of ${who(r.from)} is merged already (plan ${r.planHash})."
      else if ((r.applied) == _root_.com.histmersion.api.exchange.MergeApplyResult.Applied(true)) then
        s"**Merged** the bundle of ${who(r.from)} as merge ${r.mergeId.fold("—")(_.toString)}" +
          r.backup.fold("")(b => s" (backup $b)")
      else s"**Not merged** (plan ${r.planHash}): nothing was written."
    val body = Option
      .when(((r.applied) == _root_.com.histmersion.api.exchange.MergeApplyResult.Applied(true)) && r.mergeId.isDefined)(
        List(
          s"**sources** +${count(com.histmersion.api.exchange.Bundle.Kind.Source)(_.inserted)} new · ${t.sourcesMerged} merged (${t.sourcesByScore} by score) · " +
            s"${t.sourcesRenamed} renamed · aliases +${t.aliases}",
          s"**findings** +${count(com.histmersion.api.exchange.Bundle.Kind.Finding)(_.inserted)} · merged ${t.findingsMerged} · " +
            s"**assets** +${count(com.histmersion.api.exchange.Bundle.Kind.Asset)(_.inserted)} · merged ${t.assetsMerged} · " +
            s"**attestations** re-anchored ${t.attestationsReanchored} · dropped ${t.attestationsDropped}",
          s"**articles** +${count(com.histmersion.api.exchange.Bundle.Kind.Article)(_.inserted)} new · ${t.articlesThreeWay} three-way · " +
            s"${t.articlesFastForward} fast-forward · ${t.articlesBoth} both · ${t.articlesFromFile} from a file",
          s"**events** merged ${t.eventsMerged} · **goals** merged ${t.goalsMerged} · **queue** merged ${t.queueMerged} · " +
            s"**places** +${count(com.histmersion.api.exchange.Bundle.Kind.Place)(_.inserted)} · merged ${t.placesMerged} · " +
            s"**periods** +${count(com.histmersion.api.exchange.Bundle.Kind.Period)(_.inserted)} · merged ${t.periodsMerged} · " +
            s"self-loops dropped ${t.selfLoopsDropped} · redirects +${t.redirects}",
          s"**queued** ${Plural.count(_root_.com.histmersion.api.Plural.Count((r.reviews.size).toLong), _root_.com.histmersion.api.Plural.Noun.Singular("/review-article task"))}" +
            (if r.reviews.nonEmpty then r.reviews.map(s => s"`$s`").mkString(" (", ", ", ")") else "") +
            r.logEntry.fold("")(id => s" · log entry #$id"),
          r.replanChanges.fold("")(n =>
            if n == _root_.com.histmersion.api.exchange.MergeApplyResult.ReplanChanges(0) then "**re-run** `histmersion merge plan` on this bundle → 0 changes (idempotent)"
            else
              s"**re-run** `histmersion merge plan` on this bundle → ${Plural.count(n, _root_.com.histmersion.api.Plural.Noun.Singular("change"))} left (not idempotent — report it)"
          )
        ).filter(_.nonEmpty).mkString("  \n")
      )
      .toList
    val sections = List(
      section(("Problems (nothing was written)"), r.problems.map(x => (s"- $x"))),
      section(("Open"), r.issues.map(((issue).value))),
      section(("Notes"), r.notes.map(x => (s"- $x")))
    ).flatten
    ((head :: body ++ sections).mkString("\n\n"))

  def applyJob(j: MergeApplyJob): String =
    j.state match
      case MergeJobState.Running =>
        (s"**Applying** plan ${j.planHash} — job `${j.job}`, ${(j.elapsedMs).value / 1000} s so far (a backup is taken first).")
      case MergeJobState.Failed =>
        (s"**The merge apply failed** (job `${j.job}`): ${j.error.getOrElse("no reason given")}")
      case MergeJobState.Interrupted =>
        (s"**The merge apply was interrupted** (job `${j.job}`): ${j.error.getOrElse(MergeApplyJob.InterruptedText)}")
      case MergeJobState.Done => j.result.fold(s"**Done** (job `${j.job}`).")(applied)

  def merges(ms: List[MergeInfo]): String =
    if ms.isEmpty then ("_No merges yet: nothing was imported from another collection._")
    else
      ("| merge | when | from | bundle | plan | ledger | redirects | backup |" ::
        "|---|---|---|---|---|---|---|---|" ::
        ms.map(m =>
          s"| `${m.id.toString.take(8)}` | ${m.appliedAt.take(19).replace('T', ' ')} | ${who(m.from)} | " +
            s"`${m.bundleId.toString.take(8)}…` | ${m.planHash.fold("import")(h => s"`$h`")} | ${m.ledgerRows} | ${m.redirects} | " +
            s"${m.backup.getOrElse("—")} |"
        )).mkString("\n")

  def detail(d: MergeDetail): String =
    val m = d.merge
    val head = List(
      s"**Merge** `${m.id}` · ${m.appliedAt} · from ${who(m.from)}",
      s"**bundle** `${m.bundleId}` (sha256 `${m.bundleSha256.take(12)}…`)" +
        m.planHash.fold(" · imported without matching")(h => s" · plan `$h`") + m.backup.fold("")(b => s" · backup $b"),
      s"**left** ${Plural.count(m.ledgerRows, _root_.com.histmersion.api.Plural.Noun.Singular("ledger row"))} · ${Plural.count(m.redirects, _root_.com.histmersion.api.Plural.Noun.Singular("redirect"))}"
    ).mkString("  \n")
    val report = d.applied.map(applied).orElse(d.imported.map(BundleMarkdown.importResult)).toList
    val res = d.resolutions
      .filter(r => r.resolve.nonEmpty || r.policy.editions.isDefined)
      .map(r =>
        "**Resolutions**\n" + (r.policy.editions.map(e => s"- editions: ${e.toString.toLowerCase}").toList ++
          r.resolve.map(x =>
            s"- `${x.issue}` = `${x.decision}`" + x.text.fold("")(t => s" (${t.length} characters of merged text)")
          )).mkString("\n")
      )
    val rem = Option.when(d.remembered.nonEmpty)(
      "**Remembered decisions**\n" +
        d.remembered.map(x => s"- `${x.issue}` = `${x.decision}`").mkString("\n")
    )
    ((head :: report ++ res.toList ++ rem.toList).mkString("\n\n"))

  def ledger(l: MergeLedger): String =
    val head = l.query.fold("**Merge ledger** (latest)")(q => s"**Merge ledger** of `$q`")
    val rows =
      if l.entries.isEmpty then List("_No ledger rows._")
      else
        "| kind | from (bundle uid) | here | rev merged | rev left | decision | merge | when |" ::
          "|---|---|---|---|---|---|---|---|" ::
          l.entries.map(e =>
            s"| ${e.kind} | `${e.foreignUid}` | ${e.label.fold(s"`${e.localUid}`")(x => s"`$x`")} | " +
              s"`${e.foreignRev.take(8)}` | ${e.localRevAfter.fold("—")(r => s"`${r.take(8)}`")} | ${e.decision} | " +
              s"`${e.mergeId.toString.take(8)}` | ${e.recordedAt.take(19).replace('T', ' ')} |"
          )
    val redirects = Option.when(l.redirects.nonEmpty)(
      "**Redirects** (merged-away uid → the record here)\n" +
        l.redirects.map(r => s"- `${r.aliasUid}` → `${r.uid}` (${r.kind})").mkString("\n")
    )
    (((head :: rows.mkString("\n") :: Nil) ++ redirects.toList).mkString("\n\n"))

  def renamed(r: LabelRenamed): String =
    val what = r.entity match
      case com.histmersion.api.exchange.Bundle.Kind.Article => "Article"
      case com.histmersion.api.exchange.Bundle.Kind.Event   => "Event"
      case com.histmersion.api.exchange.Bundle.Kind.Place   => "Place"
      case _                                                => "Goal"
    (s"**$what renamed** `${r.from}` → `${r.to}`; the old label stays an alias — reads and writes naming it go to `${r.to}`." +
      (if r.rewritten.nonEmpty then s" Links rewritten in ${r.rewritten.map(a => s"`$a`").mkString(", ")}." else ""))

  private def section(title: String, lines: List[String]): Option[String] =
    Option.when(lines.nonEmpty)((s"**$title**\n" + lines.mkString("\n")))

  def issue(i: MergeIssue): String =
    val cond = i.condition.fold("")(c => s" _(if ${c.issue} = ${MergeDecision.Kind.wireOf(c.when)})_")
    val lines = List(
      i.left.map(s => s"here `${s.label}`" + s.lines.map(l => s" · $l").mkString),
      i.right.map(s => s"bundle `${s.label}`" + s.lines.map(l => s" · $l").mkString),
      Option.when(i.why.nonEmpty)("why " + i.why.mkString(" · ")),
      Option.when(i.options.nonEmpty)(
        "decide " + i.options.map(k => s"`${MergeDecision.Kind.wireOf(k)}${argHint(i, k)}`").mkString(" | ") +
          i.suggested.filter(_ => i.decision.isEmpty).fold("")(s => s" (suggested `${s.wire}`)")
      ),
      i.decision.map(d => s"decided `${d.wire}`" + i.decidedBy.fold("")(by))
    ).flatten
    ((s"- `${i.id}` ${i.entity.wire} — ${i.title}$cond" :: lines.map(l => s"  - $l")).mkString("\n"))

  private def by(b: MergeIssue.DecidedBy): String = b match
    case MergeIssue.DecidedBy.Remembered => (" as an earlier merge apply did")
    case other                           => (s" by ${other.toString.toLowerCase(java.util.Locale.ROOT)}")

  private def argHint(i: MergeIssue, k: MergeDecision.Kind): String = k match
    case MergeDecision.Kind.Map => (":" + List(i.left, i.right).flatten.map(_.label).mkString("|"))
    case MergeDecision.Kind.RenameRight | MergeDecision.Kind.RenameLeft | MergeDecision.Kind.File =>
      (i.suggested.filter(_.kind == k).flatMap(_.argument).fold(":<value>")(a => s":$a"))
    case _ => ("")
