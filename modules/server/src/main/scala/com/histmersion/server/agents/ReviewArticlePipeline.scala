package com.histmersion.server.agents

import com.histmersion.core.*
import com.histmersion.core.Agents.{Pipeline, Role}
import com.histmersion.core.Queue.QueueAdd
import com.histmersion.core.ReviewArticle.*
import com.histmersion.core.ReviewArticle.Finding
import com.histmersion.db.AgentRepo
import com.histmersion.server.Handlers
import sttp.ai.core.agent.ResponseSchema

import javax.sql.DataSource

/** `review-article <slug> [fix] [rounds]` (docs/research-embedded-agents.md §4.3): the adversarial
  * review panel of the `review-article` skill, run by the server.
  *
  * Each round (all checkpointed, numbered per round): `snapshot-r` (the body and its update token,
  * held by the engine — no export, no token copied between commands; with the article's coverage
  * when it records a filter, #958) · `mechanical-r` (what code can check with certainty, as `M`
  * findings — the coverage drift among them) · `rules-r`, `cold-reader-r`, `skeptic-r` IN PARALLEL ·
  * `merge-r` (worst verdict, dedupe, escalation of re-raised rejections) · `proposals-r` (the
  * skeptic's database corrections, through [[Approvals]]). While a MAJOR finding is open, fixing was
  * asked for and rounds remain: `fixer-r` (a list of edits anchored on the body's own text, applied by
  * code and the result verified — [[ReviewArticle.checkBody]], the same check its `check_edits` tool
  * runs — plus every major decided and every rejection quoted from the record; the step's output
  * records each edit) · `patch-r` (with the snapshot's `ifUpdatedAt`; a 412 means someone else wrote
  * the article, so the fix is dropped and the next round reviews their text).
  * Then `record`: the log line, and a `tasks` item for each MAJOR still open.
  *
  * Tools: the rules reviewer and the skeptic read the database (the skeptic also the citation graph
  * forward); the cold reader gets NOTHING but the article; the fixer reads, dry-runs its edits, and
  * writes only through code. A reviewer's finding carries the records it rests on (citekey, offset,
  * quote — checked against the record by code), and the fixer reads them under the finding. The
  * prompts are the four shared agent definitions plus the server's notes. */
final class ReviewArticlePipeline(h: Handlers, ds: DataSource, cfg: AgentsConfig,
    approvals: Approvals, definitions: AgentDefinitions) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.ReviewArticle

  /** The running totals a round hands the next one; `fixed` = the fixer has run at least once. */
  private final case class Carry(rejected: List[Finding], applied: Int, rejectedCount: Int, fixed: Boolean)

  def run(ctx: RunCtx): Either[Halt, String] =
    val slug   = ctx.run.target
    val fix    = ctx.run.params.fixes
    val rounds = ReviewArticle.rounds(ctx.run.params.rounds, cfg.review.maxRounds, cfg.review.roundsCap, fix)
    for
      last <- round(ctx, slug, 1, rounds, fix, Carry(Nil, 0, 0, fixed = false))
      line <- ctx.code[String]("record")(record(ctx, slug, last._1, last._2, last._3))
    yield line

  /** One round, and — while it is not done — the fixer, the patch and the next round. Returns the
    * last round's merged result, its number, and the totals. */
  private def round(ctx: RunCtx, slug: String, r: Int, rounds: Int, fix: Boolean,
      carry: Carry): Either[Halt, (Merged, Int, Carry)] =
    for
      snap    <- ctx.code[Snapshot](s"snapshot-$r")(snapshot(slug))
      mech    <- ctx.code[List[Finding]](s"mechanical-$r")(Right(mechanicalOf(ctx, snap, r)))
      reports <- panel(ctx, snap, r)
      merged  <- ctx.code[Merged](s"merge-$r")(Right(merge(r, mech, reports, carry.rejected)))
      _       <- ctx.code[List[String]](s"proposals-$r")(Right(propose(ctx, merged)))
      out     <-
        if done(merged, r, rounds, fix) then Right((merged, r, carry))
        else if snap.truncated then
          ctx.warn(s"round $r: the article is longer than agents.review.article-chars; it was reviewed cut and is not revised")
          Right((merged, r, carry))
        else
          for
            fixed   <- fixer(ctx, snap, merged, r)
            patched <- ctx.code[ReviewArticle.Patched](s"patch-$r")(write(ctx, snap, fixed, r))
            next    <- round(ctx, slug, r + 1, rounds, fix, Carry(
                         carry.rejected ++ merged.findings.filter(f => fixed.rejected.exists(_.id == f.id)),
                         carry.applied + fixed.applied.size, carry.rejectedCount + fixed.rejected.size,
                         fixed = true))
          yield next
    yield out

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def snapshot(slug: String): Either[String, Snapshot] =
    h.getArticle(slug).left.map(_.message).flatMap { v =>
      val a = v.article
      if a.body.trim.isEmpty then Left(s"article '$slug' has no body to review")
      else
        val cut = a.body.length > cfg.review.articleChars
        Right(Snapshot(a.slug, a.title, a.kind, if cut then a.body.take(cfg.review.articleChars) else a.body,
          a.updatedAt, cut, v.coverage))
    }

  /** What code checks with certainty: [[mechanical]] on the body (with the generated sections in
    * mind when the article records a filter) and the coverage drift (#958). An article with no
    * filter keeps its hand-written sections; the run says so once. */
  private def mechanicalOf(ctx: RunCtx, s: Snapshot, r: Int): List[Finding] =
    val body = mechanical(s.slug, s.kind, s.body, isTarget(s.body), generated = s.coverage.isDefined)
    if s.coverage.isEmpty && r == 1 && !s.kind.trim.equalsIgnoreCase("index") then
      ctx.warn(s"the article records no filter: its Not used list and Method note are hand-written and unchecked — " +
        s"set the filter (histmersion article patch ${s.slug} --filter-…) to have them generated")
    body ++ s.coverage.toList.flatMap(coverageDrift(_, body.size + 1))

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

  /** The three reviewers, in parallel. The step numbers are taken first, in a fixed order. */
  private def panel(ctx: RunCtx, s: Snapshot, r: Int): Either[Halt, List[Report]] =
    val user = reviewInput(s)
    val seqs = ctx.reserve(3)
    ctx.parallel(List(
      () => ctx.llmAt[RulesAnswer, Report](seqs(0), s"rules-$r", StepAgent.RulesReviewer, user,
              ResponseSchema.derived[RulesAnswer]())((a, last) =>
                checkRules(a, last, evidenceCheck(isCitekey, records(), cfg.verify.quoteMinRatio)).map(Checked(_, _))),
      () => ctx.llmAt[ColdReaderAnswer, Report](seqs(1), s"cold-reader-$r", StepAgent.ColdReader, user,
              ResponseSchema.derived[ColdReaderAnswer]())((a, last) => checkColdReader(a, last).map(Checked(_, _))),
      () => ctx.llmAt[SkepticAnswer, Report](seqs(2), s"skeptic-$r", StepAgent.Skeptic, user,
              ResponseSchema.derived[SkepticAnswer]())((a, last) =>
                checkSkeptic(a, last, isCitekey, records(), cfg.verify.quoteMinRatio).map(Checked(_, _)))
    ))

  /** The skeptic's verified corrections, routed through the approval gate. */
  private def propose(ctx: RunCtx, m: Merged): List[String] =
    m.proposals.map { p =>
      val o = approvals.propose(Some(ctx.run.id), p)
      o match
        case Approvals.Outcome.Recorded(id, w) => ctx.warn(s"approval $id waits for a person: $w")
        case Approvals.Outcome.Failed(w)       => ctx.warn(s"proposal not applied: $w")
        case _                                 => ()
      o.line
    }

  private def fixer(ctx: RunCtx, s: Snapshot, m: Merged, r: Int): Either[Halt, Fix] =
    val explain = m.explainBack.fold("")(e => s"\n\nThe cold reader's explain-back: $e")
    val user = s"Answer every finding on the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind} — with edits to " +
      "the text below (anchors copied from it exactly)" +
      (if s.coverage.isDefined then ", and with `reasons` for the sources of its filter it leaves out" else "") +
      ".\n\n" + Prompt.data("article", s.body) + generatedText(s) + s"\n\nThe findings of round $r:\n\n${findingsText(m)}$explain"
    // more findings, more lookups: the fixer's turns grow with them (agents.review.fixer-turns-*)
    val turns = definitions.agent(StepAgent.Fixer).toOption.map(d =>
      fixerTurns(d.maxTurns, m, cfg.review.fixerTurnsPerFinding, cfg.review.fixerTurnsCap))
    ctx.llm[FixerAnswer, Fix](s"fixer-$r", StepAgent.Fixer, user,
      ResponseSchema.derived[FixerAnswer](), Some(cfg.review.fixerMaxTokens), turns) { (a, last) =>
      val corpus = h.bodyCorpus(s.body :: Option(a.edits).getOrElse(Nil).flatMap(_.text), generated = s.coverage.isDefined)
      checkFix(a, m, s.slug, s.body, s.kind, corpus, isCitekey, records(), cfg.verify.quoteMinRatio, last, s.coverage)
        .map(Checked(_, _))
    }

  /** The one write of a round: the revised body, guarded by the snapshot's token. A 412 (someone
    * else wrote the article meanwhile) drops this fix — the next round reviews their text — and never
    * overwrites it. */
  private def write(ctx: RunCtx, s: Snapshot, f: Fix, r: Int): Either[String, ReviewArticle.Patched] =
    val bodyChanged = f.body.trim != s.body.trim
    if !bodyChanged && f.reasons.isEmpty then Right(ReviewArticle.Patched(written = false, None, conflict = false))
    else
      h.patchArticle(s.slug, ArticlePatch(body = Option.when(bodyChanged)(f.body), reasons = f.reasons,
          ifUpdatedAt = s.updatedAt)) match
        case Right(_) => Right(ReviewArticle.Patched(written = true, AgentRepo.latestArticleVersion(ds, s.slug), conflict = false))
        case Left(e) if e.code.contains(Write.CodePrecondition) =>
          ctx.warn(s"round $r: the article changed while the fixer worked; the fix was not written")
          Right(ReviewArticle.Patched(written = false, None, conflict = true))
        case Left(e) => Left(e.message)

  /** Bookkeeping, as the skill does it: one `log` line, and one `tasks` item per MAJOR still open. */
  private def record(ctx: RunCtx, slug: String, last: Merged, r: Int, c: Carry): Either[String, String] =
    val outcome = Outcome(slug, r, last.verdict, last.majors, c.applied, c.rejectedCount, c.fixed,
      last.proposals.size)
    taskLines(outcome).foreach { t =>
      ctx.warn(t)
      val _ = h.ops.queueAdd("tasks", QueueAdd(t, topic = Some("agent review")))
    }
    val line = logLine(outcome, last, ctx.run.id)
    h.ops.queueAdd("log", QueueAdd(line)).left.map(_.message).map(_ => line)
