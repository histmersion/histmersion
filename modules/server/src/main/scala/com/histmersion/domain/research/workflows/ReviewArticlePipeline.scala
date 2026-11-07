package com.histmersion.domain.research.workflows

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.library.workflows.{AgentUpstream, SourceProposals}

import com.histmersion.domain.automation.agents.{
  AgentDefinitions,
  AnswerSchemas,
  Checked,
  Halt,
  Prompt,
  RunCtx,
  StepAgent,
  Writes
}
import com.histmersion.domain.automation.workflows.{PipelineDef, Pipelines}

import com.histmersion.domain.automation.worklists.{QueueItemId, QueueScope}

import com.histmersion.api.{Plural, Write}
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{Pipeline, Role}
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.ReviewArticle.*
import com.histmersion.domain.ReviewArticle.Finding
import com.histmersion.api.automation.Queue.QueuePatch
import com.histmersion.domain.ReviewLedger.Ledger
import io.circe.syntax.*
import com.histmersion.api.automation.Queue
import com.histmersion.api.research.{
  CritiquePair,
  CritiquePairing,
  CritiqueReason,
  CritiqueRef,
  Critiques,
  CritiqueState
}
import com.histmersion.api.research.Article

/** `review-article <slug> [fix] [rounds]` (docs/research-embedded-agents.md §4.3, docs/research-review-convergence.md
  * §6): the adversarial review panel of the `review-article` skill, run by the server, around the article's REVIEW
  * LEDGER ([[ReviewLedger]]) — every finding ever raised on it, with what became of it, and the lessons learned. The
  * run loads it first (`ledger`), and every pass reads and extends it.
  *
  * Pass 1 is a FULL panel: `snapshot-1` (the body and its update token, held by the engine; with the article's coverage
  * when it records a filter, #958) · `mechanical-1` (what code can check with certainty, as `M` findings — the coverage
  * drift among them) · `rules-1`, `cold-reader-1`, `skeptic-1` IN PARALLEL, each given the ledger and answering a check
  * for each open finding of its own · `ledger-1` (the pass absorbed: checks applied, findings matched to the ledger's
  * or added under new ids, written) · `merge-1` (the open findings: the fixer's input and the pass's verdict) ·
  * `proposals-1` (the skeptic's database corrections, through [[Approvals]]).
  *
  * While a MAJOR is open, fixing was asked for and passes remain: `fixer-r` (edits anchored on the body's own text,
  * applied by code and the result verified — [[ReviewArticle.checkBody]], the same check its `check_edits` tool runs —
  * every major decided, every rejection quoted from the record) · `patch-r` (with the snapshot's `ifUpdatedAt`; a 412
  * drops the fix and the next pass is a full one on the other writer's text). Then a TARGETED pass r+1: `snapshot-r` ·
  * `mechanical-r` (the lint again, plus the formatting errors the edits introduced) · `rules-check-r`,
  * `cold-reader-check-r`, `skeptic-check-r` in parallel — only the reviewers that own a finding the fixer claims to
  * have fixed, each verifying those findings against the edited passages and looking for regressions there (a new MAJOR
  * outside them only as a factual error with evidence) · `ledger-r` · `merge-r`.
  *
  * When the passes end and the fixer ran, a final FULL panel confirms the result (numbered as the next pass) — by
  * default only when no MAJOR is left open (`agents.review.final-panel`). Then `record`: the log line, the article's
  * ONE backlog item listing its open MAJORs (created, rewritten or closed, #934), and the ledger.
  *
  * The CRITIQUE CHECKLIST (docs/research-review-convergence.md §6.8) rides on every snapshot: the full pass's skeptic
  * rules on its open pairs (a missing-critique MAJOR naming one, or not material — a reason code stores at `ledger-r`),
  * a missing-critique MAJOR is held to an open pair per pass, and the fixer may set one aside with a reason its patch
  * stores.
  *
  * Tools: the rules reviewer and the skeptic read the database (the skeptic also the citation graph forward); the cold
  * reader gets NOTHING but the article; the fixer reads, dry-runs its edits, and writes only through code. A reviewer's
  * finding carries the records it rests on (citekey, offset, quote — checked against the record by code), and the fixer
  * reads them under the finding.
  *
  * Writes (the patch, the ledger, the proposals, the log and tasks lines) go through the run's gate. A DRY run records
  * them on their steps, carries the ledger in memory, and hands the fixer's unwritten revision to the next pass IN
  * MEMORY, so a `fix` dry run exercises every pass a real one would.
  */
final class ReviewArticlePipeline(
    site: ResearchSite,
    ledgers: => ReviewLedgerService,
    cfg: => ResearchAgentsConfig,
    lib: => LibraryAgentsConfig,
    approvals: SourceProposals,
    definitions: AgentDefinitions
) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.ReviewArticle

  /** The running totals; `fixed` = the fixer has run at least once; `reasons` = the critique reasons this run stored (a
    * dry run's snapshots count them, since it never wrote them).
    */
  final private case class Carry(
      ledger: Ledger,
      applied: Carry.Applied,
      rejectedCount: Carry.RejectedCount,
      fixed: Carry.Fixed,
      reasons: List[CritiqueReason] = Nil
  )

  object Carry:
    type Applied = Applied.T
    object Applied extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type RejectedCount = RejectedCount.T
    object RejectedCount extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Fixed = Fixed.T
    object Fixed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** Where a pass leaves the run: its merged result (the open findings), the snapshot it reviewed, the totals, a dry
    * run's unwritten body (what the next snapshot reads instead of the stored one), and whether it was a targeted pass
    * (what a confirming full panel would confirm).
    */
  final private case class State(
      merged: Merged,
      snap: Snapshot,
      carry: Carry,
      unwritten: Option[State.Unwritten],
      targeted: State.Targeted = false
  )

  object State:
    type Unwritten = Unwritten.T
    object Unwritten extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Targeted = Targeted.T
    object Targeted extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  /** The last pass: its number, whether it was the confirming full panel, and the MAJORs that panel found new and
    * deferred to the next run (§6.9).
    */
  final private case class End(state: State, round: End.Round, confirmed: End.Confirmed, deferred: List[End.Deferred] = Nil)

  object End:
    type Round = Round.T
    object Round extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Confirmed = Confirmed.T
    object Confirmed extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
    type Deferred = Deferred.T
    object Deferred extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val Reviewers = List(Reviewer.Rules, Reviewer.ColdReader, Reviewer.Skeptic)

  def run(ctx: RunCtx): Either[Halt, com.histmersion.domain.research.workflows.ReviewArticlePipeline.Outcome] =
    val slug = ctx.run.target
    val fix = ctx.run.params.fixes
    val rounds = ReviewArticle.rounds(ctx.run.params.rounds, cfg.review.maxRounds, cfg.review.roundsCap, fix)
    for
      ledger <- ctx.code[Ledger](com.histmersion.domain.automation.agents.AgentRepo.StepRecord.Name("ledger"))(load(slug))
      first <- full(ctx, slug, 1, Carry(ledger, 0, 0, fixed = false), None, fixable = fix)
      end <- loop(ctx, slug, 1, rounds, fix, first, first.snap)
      line <- ctx.code[String]("record")(record(ctx, slug, end))
    yield line

  /** After pass `r`: fix and verify while a MAJOR is open and passes remain, then (maybe) confirm. `start`: the run's
    * first snapshot — what the confirming panel's regressions are measured from.
    */
  private def loop(
      ctx: RunCtx,
      slug: com.histmersion.domain.research.Slug,
      r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round,
      rounds: com.histmersion.domain.research.workflows.ReviewArticlePipeline.LoopRounds,
      fix: com.histmersion.domain.research.workflows.ReviewArticlePipeline.LoopFix,
      st: State,
      start: Snapshot
  ): Either[Halt, End] =
    if done(st.merged, r, rounds, fix) then finish(ctx, slug, r, st, start)
    else if st.snap.truncated then
      ctx.warn(
        s"round $r: the article is longer than agents.review.article-chars; it was reviewed cut and is not revised"
      )
      finish(ctx, slug, r, st, start)
    else
      val c = st.carry
      for
        fixed <- fixer(ctx, st.snap, st.merged, c.ledger, r, c.reasons)
        after = ReviewLedger.applyFix(c.ledger, fixed, ctx.run.id, r)
        patched <- ctx.code[ReviewArticle.Patched](s"patch-$r")(write(ctx, st.snap, fixed, r, after))
        carry = Carry(
          if patched.conflict then c.ledger else after,
          c.applied + fixed.applied.size,
          c.rejectedCount + fixed.rejected.size,
          fixed = true,
          if patched.conflict then c.reasons else c.reasons ++ setAsideReasons(ctx, fixed)
        )
        unwritten = if patched.conflict then None else Option.when(ctx.dryRun)(fixed.body)
        next <-
          if patched.conflict then full(ctx, slug, r + 1, carry, None, fixable = r + 1 < rounds)
          else targeted(ctx, slug, r + 1, st.snap, carry, unwritten)
        end <- loop(ctx, slug, r + 1, rounds, fix, next, if patched.conflict then next.snap else start)
      yield end

  /** The passes are over: a run whose last pass verified a fix ends with a full panel when `agents.review.final-panel`
    * asks for one (by default: only when no MAJOR is open). A run whose last pass was a full one (no fix, or a fix lost
    * to a concurrent writer) has nothing to confirm.
    */
  private def finish(ctx: RunCtx, slug: com.histmersion.domain.research.Slug, r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round, st: State, start: Snapshot): Either[Halt, End] =
    val confirm = st.targeted && (cfg.review.finalPanelMode match
      case FinalPanel.Always => true
      case FinalPanel.Clear  => st.merged.majors.isEmpty
      case FinalPanel.Never  => false)
    if !confirm then Right(End(st, r, confirmed = false))
    else
      full(ctx, slug, r + 1, st.carry, st.unwritten, fixable = com.histmersion.domain.research.workflows.ReviewArticlePipeline.FullFixable(false), confirming = Some(start))
        .map(f => End(f, r + 1, confirmed = true, f.merged.deferred))

  /** A full panel: every reviewer over the whole article, with the ledger. `fixable`: the fixer still works in this run
    * after it (pass 1 of a fixing run). `confirming`: this is the run's confirming panel, and the run's first snapshot
    * — the final pass VERIFIES (§6.9): it owes no rulings on the checklist, and a MAJOR it finds new that is no
    * regression of the run is deferred to the next run. The skeptic's not-material rulings are stored as critique
    * reasons with the ledger.
    */
  private def full(
      ctx: RunCtx,
      slug: com.histmersion.domain.research.Slug,
      r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round,
      carry: Carry,
      unwritten: Option[com.histmersion.domain.research.workflows.ReviewArticlePipeline.Unwritten],
      fixable: com.histmersion.domain.research.workflows.ReviewArticlePipeline.FullFixable,
      confirming: Option[Snapshot] = None
  ): Either[Halt, State] =
    for
      snap <- ctx.code[Snapshot](s"snapshot-$r")(snapshot(ctx, slug, unwritten, carry.reasons))
      mech <- ctx.code[List[Finding]](s"mechanical-$r")(Right(mechanicalOf(ctx, snap, r, None)))
      reports <- panel(ctx, snap, r, carry.ledger, fixable, confirming.isDefined)
      stored = notMaterial(ctx, reports)
      ledger <- ctx.code[Ledger](s"ledger-$r") {
        storeReasons(ctx, slug, stored)
        val absorbed = absorbFull(ctx, carry.ledger, r, mech, reports, snap)
        val led = confirming.fold(absorbed) { start =>
          val edited = ReviewLedger.editedPassages(start.body, snap.body).text
          val (d, ids) = ReviewLedger.deferFinal(
            carry.ledger,
            absorbed,
            ctx.run.id,
            f => finalRegression(f, start.critiques, snap.critiques, edited, lib.verify.quoteMinRatio)
          )
          if ids.nonEmpty then
            ctx.warn(
              s"round $r (the final pass): ${Plural.count(ids.size, "new MAJOR")} deferred to the next run — " +
                ids.mkString(", ") + " (logged in the ledger and the backlog item; not this run's verdict)"
            )
          d
        }
        Right(persist(ctx, led))
      }
      cold = reports.find(_.reviewer == Reviewer.ColdReader)
      merged <- ctx.code[Merged](s"merge-$r")(
        Right(
          ReviewLedger.merged(
            ledger,
            r,
            cold.flatMap(_.explainBack),
            cold.toList.flatMap(_.emptySlots),
            reports.flatMap(_.proposals).distinct,
            Some(ctx.run.id)
          )
        )
      )
      _ <- ctx.code[List[String]](s"proposals-$r")(Right(propose(ctx, merged)))
    yield State(merged, snap, carry.modify(_.ledger).setTo(ledger).modify(_.reasons).setTo(carry.reasons ++ stored), unwritten)

  /** A verification pass after a fix: the lint again, then each reviewer that owns a finding the fixer claims to have
    * fixed re-checks those, and the edited passages, only.
    */
  private def targeted(
      ctx: RunCtx,
      slug: com.histmersion.domain.research.Slug,
      r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round,
      prev: Snapshot,
      carry: Carry,
      unwritten: Option[com.histmersion.domain.research.workflows.ReviewArticlePipeline.Unwritten]
  ): Either[Halt, State] =
    for
      snap <- ctx.code[Snapshot](s"snapshot-$r")(snapshot(ctx, slug, unwritten, carry.reasons))
      mech <- ctx.code[List[Finding]](s"mechanical-$r")(Right(mechanicalOf(ctx, snap, r, Some(prev.body))))
      edited = ReviewLedger.editedPassages(prev.body, snap.body)
      checks <- recheck(ctx, snap, r, carry.ledger, edited)
      ledger <- ctx.code[Ledger](s"ledger-$r")(
        Right(persist(ctx, absorbTargeted(ctx, carry.ledger, r, mech, checks, snap)))
      )
      merged <- ctx.code[Merged](s"merge-$r")(Right(ReviewLedger.merged(ledger, r, run = Some(ctx.run.id))))
    yield State(merged, snap, carry.modify(_.ledger).setTo(ledger), unwritten, targeted = true)

  // ── the ledger ─────────────────────────────────────────────────────────────────────────────────

  private def load(slug: com.histmersion.domain.research.Slug): Either[com.histmersion.domain.research.workflows.ReviewArticlePipeline.Problem, Ledger] =
    ledgers.get(slug).map(_.getOrElse(Ledger(slug)))

  /** Write the ledger through the run's gate (a dry run keeps it in memory only). */
  private def persist(ctx: RunCtx, l: Ledger): Ledger =
    ctx.write(
      s"review ledger of [[${l.slug}]]: ${l.open.size} open (${l.openMajors.size} major), " +
        s"${l.entries.size - l.open.size} closed, ${l.lessons.size} lesson(s)",
      l.asJson
    )(ledgers.put(l))(())
    l

  /** A full pass into the ledger: the mechanical findings re-checked by code, each reviewer's checks of its open
    * findings (a "fixed" missing critique whose pair is still open recorded not fixed, §6.9), then the pass's findings
    * — one per critique pair, deduplicated across reviewers by [[merge]] — matched to the ledger or added.
    */
  private def absorbFull(
      ctx: RunCtx,
      l: Ledger,
      r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round,
      mech: List[Finding],
      reports: List[Report],
      s: Snapshot
  ): Ledger =
    val run = ctx.run.id
    val pass = merge(r, mech, reports, Nil)
    val (m, _) = ReviewLedger.recheckMechanical(l, pass.findings.filter(_.reviewer == Reviewer.Mechanical), run, r)
    val checked = reports.foldLeft(m)((led, rep) =>
      ReviewLedger.applyChecks(led, rep.reviewer, held(ctx, l, rep.checks, s), run, r)
    )
    val counted = reports
      .find(_.reviewer == Reviewer.ColdReader)
      .fold(checked)(c => ReviewLedger.recheckTermCount(checked, c.findings, run, r))
    val skeptics = l.open.filter(_.reviewer == Reviewer.Skeptic).flatMap(_.finding.pair)
    val (fs, dropped) = onePerPair(pass.findings.filterNot(_.reviewer == Reviewer.Mechanical), skeptics)
    dropped.foreach(ctx.warn)
    ReviewLedger.absorb(counted, fs, run, r)._1

  private def absorbTargeted(
      ctx: RunCtx,
      l: Ledger,
      r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round,
      mech: List[Finding],
      checks: List[Recheck],
      s: Snapshot
  ): Ledger =
    val (m, _) = ReviewLedger.recheckMechanical(l, mech, ctx.run.id, r)
    checks.foldLeft(m)((led, rc) =>
      ReviewLedger.applyRecheck(led, rc.copy(checks = held(ctx, l, rc.checks, s)), ctx.run.id, r)
    )

  /** The checks of a pass, held to the checklist of the body it reviewed ([[holdPairChecks]]). */
  private def held(ctx: RunCtx, l: Ledger, cs: List[Check], s: Snapshot): List[Check] =
    val (out, ws) = holdPairChecks(cs, l.entries.map(_.finding), s.critiques)
    ws.foreach(ctx.warn)
    out

  /** The ledger as a reviewer of a full pass reads it, after the article (empty when it has none). `lookups`: the
    * reviewer reads the database — told the ledger does not replace that (a sonnet rules reviewer given a ledger
    * answered from it alone, with no tool call, #1840); the cold reader is not.
    */
  private def ledgerText(l: Ledger, lookups: com.histmersion.domain.research.workflows.ReviewArticlePipeline.LedgerTextLookups, only: Option[Reviewer] = None): com.histmersion.domain.research.workflows.ReviewArticlePipeline.LedgerText =
    val t = ReviewLedger.promptText(l, ReviewLedger.OpenStyle.Full, cfg.review.ledgerClosedLines, only)
    val full = if lookups then "every check of your instructions, the database look-ups included"
    else "every check of your instructions"
    if t.isEmpty then com.histmersion.domain.research.workflows.ReviewArticlePipeline.LedgerText("")
    else
      "\n\nThis article has been reviewed before. Its review ledger follows: give a check for each OPEN finding " +
        "of yours, and do not raise again what it shows as closed unless the text has regressed. The ledger adds to " +
        s"your full review of the article — $full — and never replaces it.\n\n" +
        Prompt.data("review-ledger", t)

  // ── the critique checklist (docs/research-review-convergence.md §6.8) ─────────────────────────────

  /** Who stored a reason, as the checklist shows it. */
  private def by(ctx: RunCtx, role: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Reviewer): com.histmersion.domain.research.workflows.ReviewArticlePipeline.Reviewer = s"$role (agent run ${ctx.run.id.take(8)})"

  /** The skeptic's verified not-material rulings, as reasons to store. */
  private def notMaterial(ctx: RunCtx, reports: List[Report]): List[CritiqueReason] =
    reports
      .filter(_.reviewer == Reviewer.Skeptic)
      .flatMap(_.notMaterial)
      .map(_.modify(_.by).setTo(Some(by(ctx, com.histmersion.domain.research.workflows.ReviewArticlePipeline.Reviewer("skeptic")))))

  /** The fixer's set-asides (of a finding, or of a pair alone), as the reasons its patch stores. */
  private def setAsideReasons(ctx: RunCtx, f: Fix): List[CritiqueReason] =
    (f.setAside.map(a => CritiqueReason(a.pair.src, a.pair.dst, a.reason)) ++ f.pairsAside)
      .distinctBy(r => (r.src, r.dst))
      .map(_.modify(_.by).setTo(Some(by(ctx, com.histmersion.domain.research.workflows.ReviewArticlePipeline.Reviewer("fixer")))))

  /** Store reasons through the run's gate (a dry run records them; its snapshots count them from the carry). A refusal
    * — the edge went meanwhile — is a warning, never a failed run.
    */
  private def storeReasons(ctx: RunCtx, slug: com.histmersion.domain.research.Slug, rs: List[CritiqueReason]): Unit =
    if rs.nonEmpty then
      val p = Article.Patch(critiqueReasons = rs)
      ctx.write(s"critique reasons of [[$slug]]: ${rs.map(_.ref.wire).mkString(", ")}", p.asJson)(
        site.articles.patch(slug, p).left.map(_.message).map(_ => ())
      )(Right(())) match
        case Left(m)  => ctx.warn(s"critique reasons not stored: $m")
        case Right(_) => ()

  /** The skeptic's part of the brief: the checklist (§6.8), the open pairs it rules on shown in full — in the
    * confirming panel only to verify (§6.9).
    */
  private def checklistText(s: Snapshot, confirming: com.histmersion.domain.research.workflows.ReviewArticlePipeline.ChecklistTextConfirming): (com.histmersion.domain.research.workflows.ReviewArticlePipeline.Checklist, List[CritiquePair]) =
    val (t, shown) = Critiques.promptText(s.critiques, cfg.review.critiquePairs, cfg.review.critiqueReasoned)
    val task =
      if confirming then
        "This is the run's FINAL pass: it verifies. No ruling is owed; raise a missing-critique MAJOR only " +
          "for a pair the revision left open or broke, and only when the article states the criticised claim unqualified."
      else "Rule on every open pair shown in full."
    if t.isEmpty then (com.histmersion.domain.research.workflows.ReviewArticlePipeline.Checklist(""), Nil)
    else
      (
        "\n\nThe article's critique checklist, built by code from the typed edges (a pair is addressed when both sources " +
          s"are cited in one paragraph; reasoned when a reason is stored; else open). $task\n\n" +
          Prompt.data("critique-checklist", t),
        shown
      )

  /** The rules reviewer's part: the stored critique reasons, so a wrong one can be challenged. */
  private def reasonsText(s: Snapshot): com.histmersion.domain.research.workflows.ReviewArticlePipeline.ReasonsText =
    val reasoned = s.critiques.filter(_.state == CritiqueState.Reasoned).take(cfg.review.critiqueReasoned)
    if reasoned.isEmpty then com.histmersion.domain.research.workflows.ReviewArticlePipeline.ReasonsText("")
    else
      "\n\nCritiques the article sets aside with a stored reason (the generated Method note counts them; check a " +
        "reason against the records like a Not used reason):\n\n" +
        Prompt.data("critique-reasons", reasoned.map(p => s"- ${p.ref.wire}: ${p.reason.getOrElse("")}").mkString("\n"))

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def snapshot(
      ctx: RunCtx,
      slug: com.histmersion.domain.research.Slug,
      unwritten: Option[com.histmersion.domain.research.workflows.ReviewArticlePipeline.Unwritten],
      reasons: List[CritiqueReason]
  ): Either[com.histmersion.domain.research.workflows.ReviewArticlePipeline.Problem, Snapshot] =
    site.articles.get(slug).left.map(_.message).flatMap { v =>
      val a = v.article
      val body = unwritten.getOrElse(a.body)
      if body.trim.isEmpty then Left(s"article '$slug' has no body to review")
      else
        val cut = body.length > cfg.review.articleChars
        val text = if cut then body.take(cfg.review.articleChars) else body
        // the stored body's cited sources; a dry run's unwritten body keeps those it still links
        val linked = AgentVerify.wikilinks(text).toSet
        val cited = if unwritten.isEmpty then v.sourcesCited else v.sourcesCited.filter(c => linked(c.citekey))
        // a dry run's reasons were never written: its checklist counts them all the same
        val critiques = site.articles.critiques(a.slug, text, if ctx.dryRun then reasons else Nil)
        Right(Snapshot(a.slug, a.title, a.kind, text, a.updatedAt, cut, v.coverage, cited, critiques))
    }

  /** What code checks with certainty: [[mechanical]] on the body (with the generated sections in mind when the article
    * records a filter) and the coverage drift (#958). An article with no filter keeps its hand-written sections; the
    * run says so once. After a fix (`before`: the body the fixer revised) also the formatting errors `check_edits`
    * finds in the new body and not in the old one — what the edits broke.
    */
  private def mechanicalOf(ctx: RunCtx, s: Snapshot, r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round, before: Option[com.histmersion.domain.research.Article.Body]): List[Finding] =
    val body = mechanical(s.slug, s.kind, s.body, isTarget(s.body), generated = s.coverage.isDefined)
    if s.coverage.isEmpty && r == 1 && !s.kind.trim.equalsIgnoreCase("index") then
      ctx.warn(
        s"the article records no filter: its Not used list and Method note are hand-written and unchecked — " +
          s"set the filter (histmersion article patch ${s.slug} --filter-…) to have them generated"
      )
    val drift = s.coverage.toList.flatMap(coverageDrift(_, body.size + 1))
    val formatting = before.toList
      .flatMap { b =>
        val corpus = site.articles.bodyCorpus(List(b, s.body), generated = s.coverage.isDefined)
        val old = corpus.formatErrors(b).toSet
        corpus.formatErrors(s.body).filterNot(old)
      }
      .zipWithIndex
      .map { (e, i) =>
        Finding(
          s"M${body.size + drift.size + i + 1}",
          Reviewer.Mechanical,
          Severity.Minor,
          com.histmersion.domain.ReviewArticle.Finding.Kind("formatting"),
          com.histmersion.domain.ReviewArticle.Finding.Where("body"),
          com.histmersion.domain.ReviewArticle.Finding.Quote(""),
          s"a formatting error the last edits introduced: $e",
          com.histmersion.domain.ReviewArticle.Finding.Fix("repair the markup")
        )
      }
    body ++ drift ++ formatting

  /** Does `[[t]]` resolve — a citekey (or its unique short form) or an article slug? Resolved once per body, in three
    * queries, as the page renderer does.
    */
  private def isTarget(body: com.histmersion.domain.research.Article.Body): com.histmersion.domain.research.Article.Body => Boolean =
    val resolve = site.articles.pageLinks(AgentVerify.wikilinks(body))
    t => resolve(t).isDefined

  private def isCitekey(ck: com.histmersion.domain.library.Citekey): Boolean = site.sources.get(ck, body = com.histmersion.domain.library.sources.SourcesControllers.GetBody(false)).isRight

  /** What a quote may be checked against: a source's summary, findings, notes and body — what `get_source` shows.
    */
  private def recordText(ck: com.histmersion.domain.library.Citekey): Option[com.histmersion.domain.library.Citekey] =
    site.sources.get(ck, body = com.histmersion.domain.library.sources.SourcesControllers.GetBody(true)).toOption.map { s =>
      (s.source.summary.toList ++ s.findings.map(_.claim) ++ s.source.notes.toList ++ s.source.ocrMarkdown.toList)
        .mkString("\n\n")
    }

  /** [[recordText]] read once per source for one answer's checks (a reviewer cites a few records many times over).
    */
  private def records(): com.histmersion.domain.library.Citekey => Option[com.histmersion.domain.library.Citekey] =
    val seen = java.util.concurrent.ConcurrentHashMap[String, Option[String]]()
    ck => seen.computeIfAbsent(ck, recordText)

  private def reviewInput(s: Snapshot): com.histmersion.domain.research.workflows.ReviewArticlePipeline.ReviewInput =
    val cut = if s.truncated then s"\n(The body is cut at ${cfg.review.articleChars} characters.)" else ""
    s"Review the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind}.$cut\n\n${Prompt.data("article", s.body)}" +
      generatedText(s)

  /** What the confirming panel is told (§6.9): it verifies; a new MAJOR is the next run's. */
  private val FinalNote =
    "\n\nThis is the run's FINAL pass: it confirms what the run fixed. Check your open findings and the text the run " +
      "changed; a MAJOR you raise for the first time that the run's edits did not cause is recorded for the next run, " +
      "not this one."

  /** The sources the article cites with each one's recorded status — what check 2 of the rules reviewer would otherwise
    * query for (the cold reader, who reads the article alone, never gets it).
    */
  private def citedText(s: Snapshot): com.histmersion.domain.research.workflows.ReviewArticlePipeline.CitedText =
    if s.cited.isEmpty then com.histmersion.domain.research.workflows.ReviewArticlePipeline.CitedText("")
    else
      "\n\nThe sources the article cites, each with the status the database records for it (code read them):\n\n" +
        Prompt.data("cited-sources", citedLines(s.cited))

  /** The sections code generates from the article's filter (#958), shown after the body as the page shows them — data,
    * like the article, and not part of the body an edit can change.
    */
  private def generatedText(s: Snapshot): com.histmersion.domain.research.workflows.ReviewArticlePipeline.GeneratedText =
    s.generated.fold("")(g =>
      "\n\nAfter the body the page shows these sections, which CODE generates from the article's filter and its " +
        "stored reasons (the filter's sources it does not cite, each with its reason; the counts; the statuses; the " +
        s"disagreement edges). Check the reasons against the records; the text itself is not the writer's.\n\n${Prompt.data("generated-sections", g)}"
    )

  /** The three reviewers, in parallel, each with the ledger and asked to check its own open findings. The step numbers
    * are taken first, in a fixed order.
    */
  private def panel(
      ctx: RunCtx,
      s: Snapshot,
      r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round,
      l: Ledger,
      fixable: com.histmersion.domain.research.workflows.ReviewArticlePipeline.PanelFixable,
      confirming: com.histmersion.domain.research.workflows.ReviewArticlePipeline.PanelConfirming
  ): Either[Halt, List[Report]] =
    val user = reviewInput(s) + (if confirming then FinalNote else "")
    def ledger(lookups: com.histmersion.domain.research.workflows.ReviewArticlePipeline.PanelLedgerLookups, only: Option[Reviewer] = None) = ledgerText(l, lookups, only)
    val (checklist, shown) = checklistText(s, confirming)
    // the final pass owes no rulings: it verifies the checklist, it does not work through it (§6.9)
    val critique =
      CritiqueRule(s.critiques, if confirming then Nil else shown, full = com.histmersion.domain.ReviewArticle.CritiqueRule.Full(true), s.body, lib.verify.quoteMinRatio)
    val seqs = ctx.reserve(3)
    def prior(rv: Reviewer) = Some(
      Prior(l.open.filter(_.reviewer == rv).map(_.finding), s.body, lib.verify.quoteMinRatio)
    )
    ctx.parallel(
      List(
        () =>
          ctx.llmAtSeen[RulesAnswer, Report](
            seqs(0),
            s"rules-$r",
            StepAgent.RulesReviewer,
            user + ledger(true) + citedText(s) + reasonsText(s),
            ResearchAnswers.rules
          )((a, last, calls) =>
            checkRules(
              a,
              last,
              evidenceCheck(isCitekey, records(), lib.verify.quoteMinRatio),
              prior(Reviewer.Rules),
              spotRule(s, calls),
              Some(critique)
            ).map(Checked(_, _))
          ),
        // the cold reader reads only its own ledger findings: it judges the text, not the sources
        () =>
          ctx.llmAt[ColdReaderAnswer, Report](
            seqs(1),
            s"cold-reader-$r",
            StepAgent.ColdReader,
            user + ledger(false, Some(Reviewer.ColdReader)),
            ResearchAnswers.coldReader
          )((a, last) => checkColdReader(a, last, prior(Reviewer.ColdReader)).map(Checked(_, _))),
        () =>
          ctx.llmAt[SkepticAnswer, Report](
            seqs(2),
            s"skeptic-$r",
            StepAgent.Skeptic,
            user + ledger(true) + citedText(s) + checklist + ctx.notes,
            ResearchAnswers.skeptic
          )((a, last) =>
            checkSkeptic(
              a,
              last,
              isCitekey,
              records(),
              lib.verify.quoteMinRatio,
              prior(Reviewer.Skeptic),
              Some(critique)
            )
              .map(Checked(_, _))
          )
      )
    )

  /** What the rules reviewer's spot-checks are held to on a full pass (#1840): `agents.review.spot-checks` of the cited
    * claims (fewer when the article cites fewer sources in the corpus), each read by a tool call of the step. None when
    * nothing is required.
    */
  private def spotRule(s: Snapshot, calls: List[AgentVerify.ToolUse]): Option[SpotRule] =
    val inCorpus = s.cited.filter(_.title.isDefined).map(_.citekey).distinct
    val required = spotChecksRequired(cfg.review.spotChecks, inCorpus.size)
    Option.when(required > 0)(SpotRule(required, s.cited.map(_.citekey).toSet, s.body, calls, lib.verify.quoteMinRatio))

  private def agentOf(rv: Reviewer): StepAgent = rv match
    case Reviewer.ColdReader => StepAgent.ColdReader
    case Reviewer.Skeptic    => StepAgent.Skeptic
    case _                   => StepAgent.RulesReviewer

  /** A verification pass's re-checks, in parallel: one call per reviewer that owns a finding the fixer claims to have
    * fixed (none for the others), each given those findings, the edited passages, its part of the ledger and the
    * article. Its turns: `agents.review.recheck-turns` plus one per finding, never more than its agent's `max-turns`.
    */
  private def recheck(
      ctx: RunCtx,
      s: Snapshot,
      r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round,
      l: Ledger,
      edited: ReviewLedger.Edited
  ): Either[Halt, List[Recheck]] =
    val who = Reviewers.filter(rv => l.claimed(rv).nonEmpty)
    val seqs = ctx.reserve(who.size)
    ctx.parallel(who.zip(seqs).map { (rv, seq) =>
      val asked = l.claimed(rv)
      val agent = agentOf(rv)
      val turns = definitions
        .agent(agent)
        .toOption
        .map(d => math.max(1, math.min(d.maxTurns, cfg.review.recheckTurns + asked.size)))
      val ev =
        if rv == Reviewer.ColdReader then NoEvidence else evidenceCheck(isCitekey, records(), lib.verify.quoteMinRatio)
      val prior = Prior(asked.map(_.finding), s.body, lib.verify.quoteMinRatio)
      // the person's notes reach the skeptic (and the fixer), never the other reviewers
      val notes = if rv == Reviewer.Skeptic then ctx.notes else ""
      () =>
        ctx.llmAt[RecheckAnswer, Recheck](
          seq,
          s"${rv.wire}-check-$r",
          agent,
          recheckInput(s, rv, l, asked, edited) + notes,
          ResearchAnswers.recheck,
          None,
          turns
        )((a, last) => checkRecheck(rv, a, prior, edited.text, ev, last, s.critiques).map(Checked(_, _)))
    })

  /** What a re-check reads: the findings to verify (in full, with their evidence and the fixer's claim), the edited
    * passages, its own closed findings and the lessons, then the article.
    */
  private def recheckInput(
      s: Snapshot,
      rv: Reviewer,
      l: Ledger,
      asked: List[ReviewLedger.Entry],
      edited: ReviewLedger.Edited
  ): com.histmersion.domain.research.workflows.ReviewArticlePipeline.ReviewInput =
    val ids = asked.map(_.id).mkString(", ")
    val toCheck = findingsText(ReviewLedger.merged(l.modify(_.entries).setTo(asked), com.histmersion.domain.ReviewLedger.Closure.Round(0)), ReviewLedger.notes(l))
    val rest = ReviewLedger.promptText(
      l.modify(_.entries).setTo(l.entries.filterNot(e => asked.exists(_.id == e.id))),
      ReviewLedger.OpenStyle.Lines,
      cfg.review.ledgerClosedLines,
      Some(rv)
    )
    val passages = if edited.isEmpty then "(no passage changed)" else edited.render
    s"VERIFICATION PASS on the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind}. The fixer revised it and claims " +
      s"to have fixed these findings of yours: $ids. Give one check per finding against the CURRENT article, then look " +
      "for regressions in the edited passages only (outside them, report only a factual error, with evidence).\n\n" +
      Prompt.data("findings-to-check", toCheck) + "\n\n" + Prompt.data("edited-passages", passages) +
      (if rest.isEmpty then "" else "\n\n" + Prompt.data("review-ledger", rest)) + "\n\n" +
      Prompt.data("article", s.body) + (if rv == Reviewer.ColdReader then "" else generatedText(s) + citedText(s))

  /** The skeptic's verified corrections, routed through the approval gate. */
  private def propose(ctx: RunCtx, m: Merged): List[com.histmersion.domain.research.workflows.ReviewArticlePipeline.Proposal] =
    m.proposals.map { p =>
      val o = approvals.propose(ctx, p)
      o match
        case SourceProposals.Outcome.Recorded(id, w) => ctx.warn(s"approval $id waits for a person: $w")
        case SourceProposals.Outcome.Failed(w)       => ctx.warn(s"proposal not applied: $w")
        case _                                       => ()
      o.line
    }

  /** The fixer: every open finding to answer, with the ledger's history, and the critique work code worked out (§6.9) —
    * where each missing critique goes, the paragraphs that name a partner they do not cite. Its revision is checked
    * with the guards: the pairing repair, the pairs its edits leave unanswered or bring in, the closed findings they
    * would reopen. `reasons`: the critique reasons this run stored (a dry run's count as stored).
    */
  private def fixer(
      ctx: RunCtx,
      s: Snapshot,
      m: Merged,
      l: Ledger,
      r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round,
      reasons: List[CritiqueReason]
  ): Either[Halt, Fix] =
    val explain = m.explainBack.fold("")(e => s"\n\nThe cold reader's explain-back: $e")
    val history = ReviewLedger.promptText(l, ReviewLedger.OpenStyle.Omit, cfg.review.ledgerClosedLines)
    val ledger = if history.isEmpty then ""
    else
      "\n\nWhat earlier passes closed, and the lessons — do not undo a fix, " +
        "and follow every lesson:\n\n" + Prompt.data("review-ledger", history)
    val user = s"Answer every finding on the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind} — with edits to " +
      "the text below (anchors copied from it exactly)" +
      (if s.coverage.isDefined then ", and with `reasons` for the sources of its filter it leaves out" else "") +
      ".\n\n" + Prompt.data("article", s.body) + generatedText(s) + citedText(s) +
      s"\n\nThe open findings after pass $r:\n\n${findingsText(m, ReviewLedger.notes(l))}$explain" +
      critiqueWork(s, m, l) + ledger + ctx.notes
    // more findings, more lookups: the fixer's turns grow with them (agents.review.fixer-turns-*)
    val turns = definitions
      .agent(StepAgent.Fixer)
      .toOption
      .map(d => fixerTurns(d.maxTurns, m, cfg.review.fixerTurnsPerFinding, cfg.review.fixerTurnsCap))
    // the checklist and the citations of a body, read once per body (a revision is checked several times)
    val extra = if ctx.dryRun then reasons else Nil
    val pairsMemo = java.util.concurrent.ConcurrentHashMap[String, List[CritiquePair]]()
    val citedMemo = java.util.concurrent.ConcurrentHashMap[String, Set[String]]()
    def pairsOf(b: com.histmersion.domain.research.workflows.ReviewArticlePipeline.FixerReply) =
      if b == s.body then s.critiques else pairsMemo.computeIfAbsent(b, x => site.articles.critiques(s.slug, x, extra))
    def cites(b: com.histmersion.domain.research.workflows.ReviewArticlePipeline.FixerReply, ck: com.histmersion.domain.library.Citekey) = citedMemo.computeIfAbsent(b, x => site.articles.citedSources(x).toSet)(ck)
    def answered(b: com.histmersion.domain.research.workflows.ReviewArticlePipeline.FixerReply, ref: CritiqueRef) = Critiques.find(pairsOf(b), ref).exists(!_.open)
    val guards = FixGuards(
      pairsOf,
      (b0, b1) => ReviewLedger.guard(l, b0, b1, answered, cites, lib.verify.quoteMinRatio),
      b => site.articles.repairPairing(b)
    )
    ctx.llm[FixerAnswer, Fix](
      s"fixer-$r",
      StepAgent.Fixer,
      user,
      ResearchAnswers.fixer,
      Some(cfg.review.fixerMaxTokens),
      turns
    ) { (a, last) =>
      val corpus =
        site.articles.bodyCorpus(
          s.body :: Option(a.edits).getOrElse(Nil).flatMap(_.text),
          generated = s.coverage.isDefined
        )
      checkFix(
        a,
        m,
        s.slug,
        s.body,
        s.kind,
        corpus,
        isCitekey,
        records(),
        lib.verify.quoteMinRatio,
        last,
        s.coverage,
        s.critiques,
        Some(guards)
      ).map(Checked(_, _))
    }

  /** The critique work of a fix (§6.9), as the fixer reads it (`<critique-work>`): for each open missing-critique
    * finding, the paragraph its critique goes in (the one its quote is in, when that cites the criticised work) — the
    * ones found not fixed twice or more first, flagged; then the paragraphs that name a disagreement partner they do
    * not cite where code cannot tell which work the name means. Code appends a citekey itself where the name is
    * unambiguous, after the fix.
    */
  private def critiqueWork(s: Snapshot, m: Merged, l: Ledger): com.histmersion.domain.research.workflows.ReviewArticlePipeline.CritiqueWork =
    val (hits, resolve) = site.articles.pairing(s.body)
    val units = Critiques.units(s.body)
    def near(f: Finding) =
      Option(
        units.indexWhere(u =>
          f.quote.trim.nonEmpty && AgentVerify.findQuote(f.quote, u.text, lib.verify.quoteMinRatio).accepted
        )
      )
        .filter(_ >= 0)
    val stubborn = ReviewLedger.stubborn(l).map(_.id).toSet
    val critiques = m.findings
      .filter(f => isMissingCritique(f) && f.pair.isDefined)
      .sortBy(f => (!stubborn(f.id), !f.major))
      .flatMap { f =>
        f.pair
          .flatMap(Critiques.find(s.critiques, _))
          .filter(_.open)
          .flatMap(p =>
            CritiquePairing.instruction(s.body, p, resolve, near(f)).map { line =>
              val flag = l
                .entry(f.id)
                .filter(e => stubborn(e.id))
                .fold("")(e =>
                  s" [not fixed ${e.notFixedCount} times — the critique must sit in the same paragraph as the work it answers, both cited]"
                )
              s"- ${f.id}$flag: $line"
            }
          )
      }
    val named = hits.filterNot(_.mechanical).map(x => s"- ${x.line}")
    if critiques.isEmpty && named.isEmpty then com.histmersion.domain.research.workflows.ReviewArticlePipeline.CritiqueWork("")
    else
      "\n\nThe critique work, worked out by code from the checklist (a pair is answered when both sources are cited in one " +
        "paragraph, or set aside with a reason; code checks it after your edits, and appends a citekey itself where a paragraph " +
        "already names that work's author unambiguously):\n\n" +
        Prompt.data("critique-work", (critiques ++ named).mkString("\n"))

  /** The one write of a round: the revised body, guarded by the snapshot's token. A 412 (someone else wrote the article
    * meanwhile) drops this fix — the next round reviews their text — and never overwrites it.
    */
  private def write(ctx: RunCtx, s: Snapshot, f: Fix, r: com.histmersion.domain.research.workflows.ReviewArticlePipeline.Round, after: Ledger): Either[com.histmersion.domain.research.workflows.ReviewArticlePipeline.Problem, ReviewArticle.Patched] =
    val bodyChanged = f.body.trim != s.body.trim
    if !bodyChanged && f.reasons.isEmpty && f.setAside.isEmpty && f.pairsAside.isEmpty then
      persist(ctx, after)
      Right(ReviewArticle.Patched(written = com.histmersion.domain.ReviewArticle.Patched.Written(false), None, conflict = com.histmersion.domain.ReviewArticle.Patched.Conflict(false)))
    else
      val p = Article.Patch(
        body = Option.when(bodyChanged)(f.body),
        reasons = f.reasons,
        ifUpdatedAt = s.updatedAt,
        critiqueReasons = setAsideReasons(ctx, f)
      )
      // a dry run: not written — the next round reviews the revision in memory
      ctx.write(
        s"patch article [[${s.slug}]] (round $r): ${f.applied.size} finding(s) answered, ${f.edits.size} edit(s)" +
          (if f.setAside.isEmpty && f.pairsAside.isEmpty then ""
           else s", ${f.setAside.size + f.pairsAside.size} critique(s) set aside") +
          (if f.repairs.isEmpty then "" else s", ${f.repairs.size} citation(s) added by the pairing repair"),
        p.asJson
      )(site.articles.patch(s.slug, p).map(_ => true))(Right(false)) match
        case Right(false) =>
          persist(ctx, after)
          Right(ReviewArticle.Patched(written = com.histmersion.domain.ReviewArticle.Patched.Written(false), None, conflict = com.histmersion.domain.ReviewArticle.Patched.Conflict(false)))
        case Right(_) =>
          persist(ctx, after)
          Right(
            ReviewArticle.Patched(written = com.histmersion.domain.ReviewArticle.Patched.Written(true), site.articleStore.latestVersion(s.slug), conflict = com.histmersion.domain.ReviewArticle.Patched.Conflict(false))
          )
        case Left(e) if e.code.contains(Write.CodePrecondition) =>
          ctx.warn(s"round $r: the article changed while the fixer worked; the fix was not written")
          Right(ReviewArticle.Patched(written = com.histmersion.domain.ReviewArticle.Patched.Written(false), None, conflict = com.histmersion.domain.ReviewArticle.Patched.Conflict(true)))
        case Left(e) => Left(e.message)

  /** Bookkeeping: one `log` line, the article's ONE backlog item listing its open MAJORs (#934) — created, rewritten,
    * or closed when none is left — and the ledger, which keeps the item's id. An open item of the article the ledger
    * lost track of is rewritten rather than duplicated; the per-finding items earlier runs left for it, and any second
    * item, are closed as superseded.
    */
  private def record(ctx: RunCtx, slug: com.histmersion.domain.research.Slug, end: End): Either[com.histmersion.domain.research.workflows.ReviewArticlePipeline.Problem, com.histmersion.domain.research.workflows.ReviewArticlePipeline.Outcome] =
    val st = end.state
    val c = st.carry
    val last = st.merged
    val outcome =
      Outcome(slug, end.round, last.verdict, last.majors, c.applied, c.rejectedCount, c.fixed, last.proposals.size)
    val ledger = persist(ctx, backlog(ctx, slug, c.ledger))
    // the checklist as the run leaves it: the stored body (a dry run's revision held in memory)
    val body =
      st.unwritten.orElse(site.articles.get(slug, brief = com.histmersion.domain.research.articles.ArticlesControllers.GetBrief(true)).toOption.map(_.article.body)).getOrElse(st.snap.body)
    val pairs = site.articles.critiques(slug, body, if ctx.dryRun then c.reasons else Nil)
    val line = logLine(outcome, last, ctx.run.id) +
      (if end.confirmed then ", confirmed by a full panel" else "") +
      (if end.deferred.isEmpty then ""
       else s"; ${Plural.count(end.deferred.size, "new MAJOR")} of the final pass deferred to the next run") +
      s"; ledger ${ledger.entries.count(_.status == ReviewLedger.Status.Fixed)} fixed, ${ledger.open.size} open" +
      (if pairs.isEmpty then "" else s"; critiques ${Critiques.summary(pairs)}")
    Pipelines.log(site.workLists, ctx, line)

  private def backlog(ctx: RunCtx, slug: com.histmersion.domain.research.Slug, l: Ledger): Ledger =
    val tasks = Queue.Kind.Tasks.label
    val open = site.queues.list(Queue.Kind.Tasks, QueueScope.Open)
    // the ledger's item, else the article's collapsed item it lost track of (#934: never a second one)
    val current = l.task
      .flatMap(i => site.queues.get(QueueItemId(i)))
      .filter(i => i.list == tasks && i.status == Queue.Open)
      .orElse(open.filter(i => ReviewLedger.articleTask(slug, i.text)).sortBy(_.id).headOption)
    val kept = ReviewLedger.taskText(l, ctx.run.id) match
      case Some(text) =>
        ctx.warn(text)
        current match
          case Some(item) =>
            val p = QueuePatch(text = Some(text))
            ctx.write(s"tasks item #${item.id} rewritten", p.asJson)(
              site.workLists.patch(item.id, p).toOption.map(_.id)
            )(
              Some(item.id)
            )
          case None =>
            val add = Pipelines.entry(ctx, text, Some(com.histmersion.domain.automation.workflows.Pipelines.EntryTopic("agent review")))
            ctx.write("tasks item", add.asJson)(site.workLists.add(tasks, add).toOption.map(_.id))(None)
      case None =>
        current.foreach { item =>
          val p =
            QueuePatch(status = Some(Queue.Done), reason = Some(s"no MAJOR open after agent run ${ctx.run.id.take(8)}"))
          val _ =
            ctx.write(s"tasks item #${item.id} done", p.asJson)(site.workLists.patch(item.id, p).map(_ => ()))(
              Right(())
            )
        }
        None
    // the items runs before #934 left, one per finding, and any second collapsed item of the article:
    // superseded by the one item (or by none open)
    open
      .filter(i =>
        !current.exists(_.id == i.id) && !kept.contains(i.id) &&
          (ReviewLedger.legacyTask(slug, i.text) || ReviewLedger.articleTask(slug, i.text))
      )
      .foreach { item =>
        val why = kept.fold(s"no MAJOR open after agent run ${ctx.run.id.take(8)}")(id =>
          s"superseded by #$id (one item per article)"
        )
        val p = QueuePatch(status = Some(Queue.Done), reason = Some(why))
        val _ =
          ctx.write(s"tasks item #${item.id} done", p.asJson)(site.workLists.patch(item.id, p).map(_ => ()))(Right(()))
      }
    l.modify(_.task).setTo(kept)

object ReviewArticlePipeline:
  type Checklist = Checklist.T
  object Checklist extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedText = CitedText.T
  object CitedText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CritiqueWork = CritiqueWork.T
  object CritiqueWork extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixerReply = FixerReply.T
  object FixerReply extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GeneratedText = GeneratedText.T
  object GeneratedText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LedgerText = LedgerText.T
  object LedgerText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Outcome = Outcome.T
  object Outcome extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Problem = Problem.T
  object Problem extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Proposal = Proposal.T
  object Proposal extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReasonsText = ReasonsText.T
  object ReasonsText extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReviewInput = ReviewInput.T
  object ReviewInput extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Reviewer = Reviewer.T
  object Reviewer extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Round = Round.T
  object Round extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type Unwritten = Unwritten.T
  object Unwritten extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanelLedgerLookups = PanelLedgerLookups.T
  object PanelLedgerLookups extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LoopRounds = LoopRounds.T
  object LoopRounds extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LoopFix = LoopFix.T
  object LoopFix extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FullFixable = FullFixable.T
  object FullFixable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LedgerTextLookups = LedgerTextLookups.T
  object LedgerTextLookups extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ChecklistTextConfirming = ChecklistTextConfirming.T
  object ChecklistTextConfirming extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanelFixable = PanelFixable.T
  object PanelFixable extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PanelConfirming = PanelConfirming.T
  object PanelConfirming extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
