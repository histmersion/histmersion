package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.{Pipeline, Role}
import com.histmersion.core.Queue.QueueAdd
import com.histmersion.core.ReviewArticle.*
import com.histmersion.core.ReviewArticle.Finding
import com.histmersion.core.Queue.QueuePatch
import com.histmersion.core.ReviewLedger.Ledger
import com.histmersion.db.{AgentRepo, QueueRepo, ReviewLedgerRepo}
import com.histmersion.server.Handlers
import io.circe.syntax.*

import javax.sql.DataSource

/** `review-article <slug> [fix] [rounds]` (docs/research-embedded-agents.md §4.3,
  * docs/research-review-convergence.md §6): the adversarial review panel of the `review-article` skill,
  * run by the server, around the article's REVIEW LEDGER ([[ReviewLedger]]) — every finding ever raised
  * on it, with what became of it, and the lessons learned. The run loads it first (`ledger`), and every
  * pass reads and extends it.
  *
  * Pass 1 is a FULL panel: `snapshot-1` (the body and its update token, held by the engine; with the
  * article's coverage when it records a filter, #958) · `mechanical-1` (what code can check with
  * certainty, as `M` findings — the coverage drift among them) · `rules-1`, `cold-reader-1`,
  * `skeptic-1` IN PARALLEL, each given the ledger and answering a check for each open finding of its
  * own · `ledger-1` (the pass absorbed: checks applied, findings matched to the ledger's or added under
  * new ids, written) · `merge-1` (the open findings: the fixer's input and the pass's verdict) ·
  * `proposals-1` (the skeptic's database corrections, through [[Approvals]]).
  *
  * While a MAJOR is open, fixing was asked for and passes remain: `fixer-r` (edits anchored on the
  * body's own text, applied by code and the result verified — [[ReviewArticle.checkBody]], the same
  * check its `check_edits` tool runs — every major decided, every rejection quoted from the record) ·
  * `patch-r` (with the snapshot's `ifUpdatedAt`; a 412 drops the fix and the next pass is a full one on
  * the other writer's text). Then a TARGETED pass r+1: `snapshot-r` · `mechanical-r` (the lint again,
  * plus the formatting errors the edits introduced) · `rules-check-r`, `cold-reader-check-r`,
  * `skeptic-check-r` in parallel — only the reviewers that own a finding the fixer claims to have
  * fixed, each verifying those findings against the edited passages and looking for regressions there
  * (a new MAJOR outside them only as a factual error with evidence) · `ledger-r` · `merge-r`.
  *
  * When the passes end and the fixer ran, a final FULL panel confirms the result (numbered as the next
  * pass) — by default only when no MAJOR is left open (`agents.review.final-panel`). Then `record`: the
  * log line, the article's ONE backlog item listing its open MAJORs (created, rewritten or closed,
  * #934), and the ledger.
  *
  * Tools: the rules reviewer and the skeptic read the database (the skeptic also the citation graph
  * forward); the cold reader gets NOTHING but the article; the fixer reads, dry-runs its edits, and
  * writes only through code. A reviewer's finding carries the records it rests on (citekey, offset,
  * quote — checked against the record by code), and the fixer reads them under the finding.
  *
  * Writes (the patch, the ledger, the proposals, the log and tasks lines) go through the run's gate. A
  * DRY run records them on their steps, carries the ledger in memory, and hands the fixer's unwritten
  * revision to the next pass IN MEMORY, so a `fix` dry run exercises every pass a real one would. */
final class ReviewArticlePipeline(h: Handlers, ds: DataSource, cfg: AgentsConfig,
    approvals: Approvals, definitions: AgentDefinitions) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.ReviewArticle

  /** The running totals; `fixed` = the fixer has run at least once. */
  private final case class Carry(ledger: Ledger, applied: Int, rejectedCount: Int, fixed: Boolean)

  /** Where a pass leaves the run: its merged result (the open findings), the snapshot it reviewed, the
    * totals, a dry run's unwritten body (what the next snapshot reads instead of the stored one), and
    * whether it was a targeted pass (what a confirming full panel would confirm). */
  private final case class State(merged: Merged, snap: Snapshot, carry: Carry, unwritten: Option[String],
      targeted: Boolean = false)

  /** The last pass: its number, and whether it was the confirming full panel. */
  private final case class End(state: State, round: Int, confirmed: Boolean)

  private val Reviewers = List(Reviewer.Rules, Reviewer.ColdReader, Reviewer.Skeptic)

  def run(ctx: RunCtx): Either[Halt, String] =
    val slug   = ctx.run.target
    val fix    = ctx.run.params.fixes
    val rounds = ReviewArticle.rounds(ctx.run.params.rounds, cfg.review.maxRounds, cfg.review.roundsCap, fix)
    for
      ledger <- ctx.code[Ledger]("ledger")(load(slug))
      first  <- full(ctx, slug, 1, Carry(ledger, 0, 0, fixed = false), None)
      end    <- loop(ctx, slug, 1, rounds, fix, first)
      line   <- ctx.code[String]("record")(record(ctx, slug, end))
    yield line

  /** After pass `r`: fix and verify while a MAJOR is open and passes remain, then (maybe) confirm. */
  private def loop(ctx: RunCtx, slug: String, r: Int, rounds: Int, fix: Boolean, st: State): Either[Halt, End] =
    if done(st.merged, r, rounds, fix) then finish(ctx, slug, r, st)
    else if st.snap.truncated then
      ctx.warn(s"round $r: the article is longer than agents.review.article-chars; it was reviewed cut and is not revised")
      finish(ctx, slug, r, st)
    else
      val c = st.carry
      for
        fixed   <- fixer(ctx, st.snap, st.merged, c.ledger, r)
        after    = ReviewLedger.applyFix(c.ledger, fixed, ctx.run.id, r)
        patched <- ctx.code[ReviewArticle.Patched](s"patch-$r")(write(ctx, st.snap, fixed, r, after))
        carry    = Carry(if patched.conflict then c.ledger else after, c.applied + fixed.applied.size,
                     c.rejectedCount + fixed.rejected.size, fixed = true)
        unwritten = if patched.conflict then None else Option.when(ctx.dryRun)(fixed.body)
        next    <- if patched.conflict then full(ctx, slug, r + 1, carry, None)
                   else targeted(ctx, slug, r + 1, st.snap, carry, unwritten)
        end     <- loop(ctx, slug, r + 1, rounds, fix, next)
      yield end

  /** The passes are over: a run whose last pass verified a fix ends with a full panel when
    * `agents.review.final-panel` asks for one (by default: only when no MAJOR is open). A run whose last
    * pass was a full one (no fix, or a fix lost to a concurrent writer) has nothing to confirm. */
  private def finish(ctx: RunCtx, slug: String, r: Int, st: State): Either[Halt, End] =
    val confirm = st.targeted && (cfg.review.finalPanelMode match
      case FinalPanel.Always => true
      case FinalPanel.Clear  => st.merged.majors.isEmpty
      case FinalPanel.Never  => false)
    if !confirm then Right(End(st, r, confirmed = false))
    else full(ctx, slug, r + 1, st.carry, st.unwritten).map(End(_, r + 1, confirmed = true))

  /** A full panel: every reviewer over the whole article, with the ledger. */
  private def full(ctx: RunCtx, slug: String, r: Int, carry: Carry, unwritten: Option[String]): Either[Halt, State] =
    for
      snap    <- ctx.code[Snapshot](s"snapshot-$r")(snapshot(slug, unwritten))
      mech    <- ctx.code[List[Finding]](s"mechanical-$r")(Right(mechanicalOf(ctx, snap, r, None)))
      reports <- panel(ctx, snap, r, carry.ledger)
      ledger  <- ctx.code[Ledger](s"ledger-$r")(Right(persist(ctx, absorbFull(ctx, carry.ledger, r, mech, reports))))
      cold     = reports.find(_.reviewer == Reviewer.ColdReader)
      merged  <- ctx.code[Merged](s"merge-$r")(Right(ReviewLedger.merged(ledger, r, cold.flatMap(_.explainBack),
                   cold.toList.flatMap(_.emptySlots), reports.flatMap(_.proposals).distinct)))
      _       <- ctx.code[List[String]](s"proposals-$r")(Right(propose(ctx, merged)))
    yield State(merged, snap, carry.copy(ledger = ledger), unwritten)

  /** A verification pass after a fix: the lint again, then each reviewer that owns a finding the fixer
    * claims to have fixed re-checks those, and the edited passages, only. */
  private def targeted(ctx: RunCtx, slug: String, r: Int, prev: Snapshot, carry: Carry,
      unwritten: Option[String]): Either[Halt, State] =
    for
      snap    <- ctx.code[Snapshot](s"snapshot-$r")(snapshot(slug, unwritten))
      mech    <- ctx.code[List[Finding]](s"mechanical-$r")(Right(mechanicalOf(ctx, snap, r, Some(prev.body))))
      edited   = ReviewLedger.editedPassages(prev.body, snap.body)
      checks  <- recheck(ctx, snap, r, carry.ledger, edited)
      ledger  <- ctx.code[Ledger](s"ledger-$r")(Right(persist(ctx, absorbTargeted(ctx, carry.ledger, r, mech, checks))))
      merged  <- ctx.code[Merged](s"merge-$r")(Right(ReviewLedger.merged(ledger, r)))
    yield State(merged, snap, carry.copy(ledger = ledger), unwritten, targeted = true)

  // ── the ledger ─────────────────────────────────────────────────────────────────────────────────

  private def load(slug: String): Either[String, Ledger] =
    ReviewLedgerRepo.get(ds, slug).map(_.getOrElse(Ledger(slug)))

  /** Write the ledger through the run's gate (a dry run keeps it in memory only). */
  private def persist(ctx: RunCtx, l: Ledger): Ledger =
    ctx.write(s"review ledger of [[${l.slug}]]: ${l.open.size} open (${l.openMajors.size} major), " +
      s"${l.entries.size - l.open.size} closed, ${l.lessons.size} lesson(s)", l.asJson)(ReviewLedgerRepo.put(ds, l))(())
    l

  /** A full pass into the ledger: the mechanical findings re-checked by code, each reviewer's checks of
    * its open findings, then the pass's findings (deduplicated across reviewers by [[merge]]) matched
    * to the ledger or added. */
  private def absorbFull(ctx: RunCtx, l: Ledger, r: Int, mech: List[Finding], reports: List[Report]): Ledger =
    val run    = ctx.run.id
    val pass   = merge(r, mech, reports, Nil)
    val (m, _) = ReviewLedger.recheckMechanical(l, pass.findings.filter(_.reviewer == Reviewer.Mechanical), run, r)
    val checked = reports.foldLeft(m)((led, rep) => ReviewLedger.applyChecks(led, rep.reviewer, rep.checks, run, r))
    val counted = reports.find(_.reviewer == Reviewer.ColdReader)
      .fold(checked)(c => ReviewLedger.recheckTermCount(checked, c.findings, run, r))
    ReviewLedger.absorb(counted, pass.findings.filterNot(_.reviewer == Reviewer.Mechanical), run, r)._1

  private def absorbTargeted(ctx: RunCtx, l: Ledger, r: Int, mech: List[Finding], checks: List[Recheck]): Ledger =
    val (m, _) = ReviewLedger.recheckMechanical(l, mech, ctx.run.id, r)
    checks.foldLeft(m)((led, rc) => ReviewLedger.applyRecheck(led, rc, ctx.run.id, r))

  /** The ledger as a reviewer of a full pass reads it, after the article (empty when it has none). */
  private def ledgerText(l: Ledger): String =
    val t = ReviewLedger.promptText(l, ReviewLedger.OpenStyle.Full, cfg.review.ledgerClosedLines)
    if t.isEmpty then ""
    else "\n\nThis article has been reviewed before. Its review ledger follows: give a check for each OPEN finding " +
      "of yours, and do not raise again what it shows as closed unless the text has regressed.\n\n" +
      Prompt.data("review-ledger", t)

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def snapshot(slug: String, unwritten: Option[String]): Either[String, Snapshot] =
    h.getArticle(slug).left.map(_.message).flatMap { v =>
      val a    = v.article
      val body = unwritten.getOrElse(a.body)
      if body.trim.isEmpty then Left(s"article '$slug' has no body to review")
      else
        val cut = body.length > cfg.review.articleChars
        Right(Snapshot(a.slug, a.title, a.kind, if cut then body.take(cfg.review.articleChars) else body,
          a.updatedAt, cut, v.coverage))
    }

  /** What code checks with certainty: [[mechanical]] on the body (with the generated sections in
    * mind when the article records a filter) and the coverage drift (#958). An article with no
    * filter keeps its hand-written sections; the run says so once. After a fix (`before`: the body the
    * fixer revised) also the formatting errors `check_edits` finds in the new body and not in the old
    * one — what the edits broke. */
  private def mechanicalOf(ctx: RunCtx, s: Snapshot, r: Int, before: Option[String]): List[Finding] =
    val body = mechanical(s.slug, s.kind, s.body, isTarget(s.body), generated = s.coverage.isDefined)
    if s.coverage.isEmpty && r == 1 && !s.kind.trim.equalsIgnoreCase("index") then
      ctx.warn(s"the article records no filter: its Not used list and Method note are hand-written and unchecked — " +
        s"set the filter (histmersion article patch ${s.slug} --filter-…) to have them generated")
    val drift = s.coverage.toList.flatMap(coverageDrift(_, body.size + 1))
    val formatting = before.toList.flatMap { b =>
      val corpus = h.bodyCorpus(List(b, s.body), generated = s.coverage.isDefined)
      val old    = corpus.formatErrors(b).toSet
      corpus.formatErrors(s.body).filterNot(old)
    }.zipWithIndex.map { (e, i) =>
      Finding(s"M${body.size + drift.size + i + 1}", Reviewer.Mechanical, Severity.Minor, "formatting", "body", "",
        s"a formatting error the last edits introduced: $e", "repair the markup")
    }
    body ++ drift ++ formatting

  /** Does `[[t]]` resolve — a citekey (or its unique short form) or an article slug? Resolved once
    * per body, in three queries, as the page renderer does. */
  private def isTarget(body: String): String => Boolean =
    val resolve = h.pageLinks(AgentVerify.wikilinks(body))
    t => resolve(t).isDefined

  private def isCitekey(ck: String): Boolean = h.getSource(ck, body = false).isRight

  /** What a quote may be checked against: a source's summary, findings, notes and body — what
    * `get_source` shows. */
  private def recordText(ck: String): Option[String] =
    h.getSource(ck, body = true).toOption.map { s =>
      (s.source.summary.toList ++ s.findings.map(_.claim) ++ s.source.notes.toList ++ s.source.ocrMarkdown.toList)
        .mkString("\n\n")
    }

  /** [[recordText]] read once per source for one answer's checks (a reviewer cites a few records
    * many times over). */
  private def records(): String => Option[String] =
    val seen = java.util.concurrent.ConcurrentHashMap[String, Option[String]]()
    ck => seen.computeIfAbsent(ck, recordText)

  private def reviewInput(s: Snapshot): String =
    val cut = if s.truncated then s"\n(The body is cut at ${cfg.review.articleChars} characters.)" else ""
    s"Review the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind}.$cut\n\n${Prompt.data("article", s.body)}" +
      generatedText(s)

  /** The sections code generates from the article's filter (#958), shown after the body as the page
    * shows them — data, like the article, and not part of the body an edit can change. */
  private def generatedText(s: Snapshot): String =
    s.generated.fold("")(g =>
      "\n\nAfter the body the page shows these sections, which CODE generates from the article's filter and its " +
        "stored reasons (the filter's sources it does not cite, each with its reason; the counts; the statuses; the " +
        s"disagreement edges). Check the reasons against the records; the text itself is not the writer's.\n\n${Prompt.data("generated-sections", g)}")

  /** The three reviewers, in parallel, each with the ledger and asked to check its own open findings.
    * The step numbers are taken first, in a fixed order. */
  private def panel(ctx: RunCtx, s: Snapshot, r: Int, l: Ledger): Either[Halt, List[Report]] =
    val user = reviewInput(s) + ledgerText(l)
    val seqs = ctx.reserve(3)
    def prior(rv: Reviewer) = Some(Prior(l.open.filter(_.reviewer == rv).map(_.finding), s.body, cfg.verify.quoteMinRatio))
    ctx.parallel(List(
      () => ctx.llmAt[RulesAnswer, Report](seqs(0), s"rules-$r", StepAgent.RulesReviewer, user,
              AnswerSchemas.rules)((a, last) =>
                checkRules(a, last, evidenceCheck(isCitekey, records(), cfg.verify.quoteMinRatio), prior(Reviewer.Rules))
                  .map(Checked(_, _))),
      () => ctx.llmAt[ColdReaderAnswer, Report](seqs(1), s"cold-reader-$r", StepAgent.ColdReader, user,
              AnswerSchemas.coldReader)((a, last) => checkColdReader(a, last, prior(Reviewer.ColdReader)).map(Checked(_, _))),
      () => ctx.llmAt[SkepticAnswer, Report](seqs(2), s"skeptic-$r", StepAgent.Skeptic, user + ctx.notes,
              AnswerSchemas.skeptic)((a, last) =>
                checkSkeptic(a, last, isCitekey, records(), cfg.verify.quoteMinRatio, prior(Reviewer.Skeptic)).map(Checked(_, _)))
    ))

  private def agentOf(rv: Reviewer): StepAgent = rv match
    case Reviewer.ColdReader => StepAgent.ColdReader
    case Reviewer.Skeptic    => StepAgent.Skeptic
    case _                   => StepAgent.RulesReviewer

  /** A verification pass's re-checks, in parallel: one call per reviewer that owns a finding the fixer
    * claims to have fixed (none for the others), each given those findings, the edited passages, its
    * part of the ledger and the article. Its turns: `agents.review.recheck-turns` plus one per finding,
    * never more than its agent's `max-turns`. */
  private def recheck(ctx: RunCtx, s: Snapshot, r: Int, l: Ledger, edited: ReviewLedger.Edited): Either[Halt, List[Recheck]] =
    val who  = Reviewers.filter(rv => l.claimed(rv).nonEmpty)
    val seqs = ctx.reserve(who.size)
    ctx.parallel(who.zip(seqs).map { (rv, seq) =>
      val asked = l.claimed(rv)
      val agent = agentOf(rv)
      val turns = definitions.agent(agent).toOption.map(d => math.max(1, math.min(d.maxTurns, cfg.review.recheckTurns + asked.size)))
      val ev    = if rv == Reviewer.ColdReader then NoEvidence else evidenceCheck(isCitekey, records(), cfg.verify.quoteMinRatio)
      val prior = Prior(asked.map(_.finding), s.body, cfg.verify.quoteMinRatio)
      // the person's notes reach the skeptic (and the fixer), never the other reviewers
      val notes = if rv == Reviewer.Skeptic then ctx.notes else ""
      () => ctx.llmAt[RecheckAnswer, Recheck](seq, s"${rv.wire}-check-$r", agent, recheckInput(s, rv, l, asked, edited) + notes,
        AnswerSchemas.recheck, None, turns)((a, last) =>
          checkRecheck(rv, a, prior, edited.text, ev, last).map(Checked(_, _)))
    })

  /** What a re-check reads: the findings to verify (in full, with their evidence and the fixer's claim),
    * the edited passages, its own closed findings and the lessons, then the article. */
  private def recheckInput(s: Snapshot, rv: Reviewer, l: Ledger, asked: List[ReviewLedger.Entry],
      edited: ReviewLedger.Edited): String =
    val ids    = asked.map(_.id).mkString(", ")
    val toCheck = findingsText(ReviewLedger.merged(l.copy(entries = asked), 0), ReviewLedger.notes(l))
    val rest   = ReviewLedger.promptText(l.copy(entries = l.entries.filterNot(e => asked.exists(_.id == e.id))),
      ReviewLedger.OpenStyle.Lines, cfg.review.ledgerClosedLines, Some(rv))
    val passages = if edited.isEmpty then "(no passage changed)" else edited.render
    s"VERIFICATION PASS on the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind}. The fixer revised it and claims " +
      s"to have fixed these findings of yours: $ids. Give one check per finding against the CURRENT article, then look " +
      "for regressions in the edited passages only (outside them, report only a factual error, with evidence).\n\n" +
      Prompt.data("findings-to-check", toCheck) + "\n\n" + Prompt.data("edited-passages", passages) +
      (if rest.isEmpty then "" else "\n\n" + Prompt.data("review-ledger", rest)) + "\n\n" +
      Prompt.data("article", s.body) + (if rv == Reviewer.ColdReader then "" else generatedText(s))

  /** The skeptic's verified corrections, routed through the approval gate. */
  private def propose(ctx: RunCtx, m: Merged): List[String] =
    m.proposals.map { p =>
      val o = approvals.propose(ctx, p)
      o match
        case Approvals.Outcome.Recorded(id, w) => ctx.warn(s"approval $id waits for a person: $w")
        case Approvals.Outcome.Failed(w)       => ctx.warn(s"proposal not applied: $w")
        case _                                 => ()
      o.line
    }

  private def fixer(ctx: RunCtx, s: Snapshot, m: Merged, l: Ledger, r: Int): Either[Halt, Fix] =
    val explain = m.explainBack.fold("")(e => s"\n\nThe cold reader's explain-back: $e")
    val history = ReviewLedger.promptText(l, ReviewLedger.OpenStyle.Omit, cfg.review.ledgerClosedLines)
    val ledger  = if history.isEmpty then "" else "\n\nWhat earlier passes closed, and the lessons — do not undo a fix, " +
      "and follow every lesson:\n\n" + Prompt.data("review-ledger", history)
    val user = s"Answer every finding on the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind} — with edits to " +
      "the text below (anchors copied from it exactly)" +
      (if s.coverage.isDefined then ", and with `reasons` for the sources of its filter it leaves out" else "") +
      ".\n\n" + Prompt.data("article", s.body) + generatedText(s) +
      s"\n\nThe open findings after pass $r:\n\n${findingsText(m, ReviewLedger.notes(l))}$explain$ledger" + ctx.notes
    // more findings, more lookups: the fixer's turns grow with them (agents.review.fixer-turns-*)
    val turns = definitions.agent(StepAgent.Fixer).toOption.map(d =>
      fixerTurns(d.maxTurns, m, cfg.review.fixerTurnsPerFinding, cfg.review.fixerTurnsCap))
    ctx.llm[FixerAnswer, Fix](s"fixer-$r", StepAgent.Fixer, user,
      AnswerSchemas.fixer, Some(cfg.review.fixerMaxTokens), turns) { (a, last) =>
      val corpus = h.bodyCorpus(s.body :: Option(a.edits).getOrElse(Nil).flatMap(_.text), generated = s.coverage.isDefined)
      checkFix(a, m, s.slug, s.body, s.kind, corpus, isCitekey, records(), cfg.verify.quoteMinRatio, last, s.coverage)
        .map(Checked(_, _))
    }

  /** The one write of a round: the revised body, guarded by the snapshot's token. A 412 (someone
    * else wrote the article meanwhile) drops this fix — the next round reviews their text — and never
    * overwrites it. */
  private def write(ctx: RunCtx, s: Snapshot, f: Fix, r: Int, after: Ledger): Either[String, ReviewArticle.Patched] =
    val bodyChanged = f.body.trim != s.body.trim
    if !bodyChanged && f.reasons.isEmpty then
      persist(ctx, after)
      Right(ReviewArticle.Patched(written = false, None, conflict = false))
    else
      val p = ArticlePatch(body = Option.when(bodyChanged)(f.body), reasons = f.reasons, ifUpdatedAt = s.updatedAt)
      // a dry run: not written — the next round reviews the revision in memory
      ctx.write(s"patch article [[${s.slug}]] (round $r): ${f.applied.size} finding(s) answered, ${f.edits.size} edit(s)", p.asJson)(
          h.patchArticle(s.slug, p).map(_ => true))(Right(false)) match
        case Right(false) =>
          persist(ctx, after)
          Right(ReviewArticle.Patched(written = false, None, conflict = false))
        case Right(_) =>
          persist(ctx, after)
          Right(ReviewArticle.Patched(written = true, AgentRepo.latestArticleVersion(ds, s.slug), conflict = false))
        case Left(e) if e.code.contains(Write.CodePrecondition) =>
          ctx.warn(s"round $r: the article changed while the fixer worked; the fix was not written")
          Right(ReviewArticle.Patched(written = false, None, conflict = true))
        case Left(e) => Left(e.message)

  /** Bookkeeping: one `log` line, the article's ONE backlog item listing its open MAJORs (#934) —
    * created, rewritten, or closed when none is left — and the ledger, which keeps the item's id. The
    * per-finding items earlier runs left for the article are closed as superseded. */
  private def record(ctx: RunCtx, slug: String, end: End): Either[String, String] =
    val st      = end.state
    val c       = st.carry
    val last    = st.merged
    val outcome = Outcome(slug, end.round, last.verdict, last.majors, c.applied, c.rejectedCount, c.fixed,
      last.proposals.size)
    val ledger  = persist(ctx, backlog(ctx, slug, c.ledger))
    val line    = logLine(outcome, last, ctx.run.id) +
      (if end.confirmed then ", confirmed by a full panel" else "") +
      s"; ledger ${ledger.entries.count(_.status == ReviewLedger.Status.Fixed)} fixed, ${ledger.open.size} open"
    val entry = QueueAdd(line)
    ctx.write("log line", entry.asJson)(h.ops.queueAdd("log", entry).left.map(_.message).map(_ => ()))(Right(()))
      .map(_ => line)

  private def backlog(ctx: RunCtx, slug: String, l: Ledger): Ledger =
    val tasks   = Queue.Kind.Tasks.label
    val current = l.task.flatMap(QueueRepo.get(ds, _)).filter(i => i.list == tasks && i.status == Queue.Open)
    val kept = ReviewLedger.taskText(l, ctx.run.id) match
      case Some(text) =>
        ctx.warn(text)
        current match
          case Some(item) =>
            val p = QueuePatch(text = Some(text))
            ctx.write(s"tasks item #${item.id} rewritten", p.asJson)(h.ops.queuePatch(item.id, p).toOption.map(_.id))(Some(item.id))
          case None =>
            val add = QueueAdd(text, topic = Some("agent review"))
            ctx.write("tasks item", add.asJson)(h.ops.queueAdd(tasks, add).toOption.map(_.id))(None)
      case None =>
        current.foreach { item =>
          val p = QueuePatch(status = Some(Queue.Done), reason = Some(s"no MAJOR open after agent run ${ctx.run.id.take(8)}"))
          val _ = ctx.write(s"tasks item #${item.id} done", p.asJson)(h.ops.queuePatch(item.id, p).map(_ => ()))(Right(()))
        }
        None
    // the items runs before #934 left, one per finding: superseded by the one item (or by none open)
    QueueRepo.list(ds, Queue.Kind.Tasks, all = false).filter(i => ReviewLedger.legacyTask(slug, i.text)).foreach { item =>
      val why = kept.fold(s"no MAJOR open after agent run ${ctx.run.id.take(8)}")(id => s"superseded by #$id (one item per article)")
      val p = QueuePatch(status = Some(Queue.Done), reason = Some(why))
      val _ = ctx.write(s"tasks item #${item.id} done", p.asJson)(h.ops.queuePatch(item.id, p).map(_ => ()))(Right(()))
    }
    l.copy(task = kept)
