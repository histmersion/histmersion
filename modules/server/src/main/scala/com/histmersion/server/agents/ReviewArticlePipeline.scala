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
  * held by the engine — no export, no token copied between commands) · `mechanical-r` (what code can
  * check with certainty, as `M` findings) · `rules-r`, `cold-reader-r`, `skeptic-r` IN PARALLEL ·
  * `merge-r` (worst verdict, dedupe, escalation of re-raised rejections) · `proposals-r` (the
  * skeptic's database corrections, through [[Approvals]]). While a MAJOR finding is open, fixing was
  * asked for and rounds remain: `fixer-r` (a revised body, verified: links, lint, every major decided,
  * every rejection quoted from the record) · `patch-r` (with the snapshot's `ifUpdatedAt`; a 412 means
  * someone else wrote the article, so the fix is dropped and the next round reviews their text).
  * Then `record`: the log line, and a `tasks` item for each MAJOR still open.
  *
  * Tools: the rules reviewer and the skeptic read the database (the skeptic also the citation graph
  * forward); the cold reader gets NOTHING but the article; the fixer reads, and writes only through
  * code. The prompts are the four shared agent definitions plus the server's notes. */
final class ReviewArticlePipeline(h: Handlers, ds: DataSource, cfg: AgentsConfig, prompts: Prompts,
    approvals: Approvals) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.ReviewArticle

  private val RulesTools   = List(ToolName.GetSource, ToolName.Neighbors, ToolName.Search, ToolName.Query, ToolName.Facets)
  private val SkepticTools = List(ToolName.GetSource, ToolName.Neighbors, ToolName.Search, ToolName.Query, ToolName.Followups)
  private val FixerTools   = List(ToolName.GetSource, ToolName.Neighbors, ToolName.Search, ToolName.Query)

  /** The four system prompts. */
  private final case class Systems(rules: String, cold: String, skeptic: String, fixer: String)

  private def systems: Either[Halt, Systems] =
    val p = pipeline.wire
    (for
      guide  <- prompts.load("writing-guide.md")
      common <- prompts.step(p, "common")
      withDb  = (agent: String, notes: String) =>
                  s"$agent\n\n$common\n$notes\n\n# The writing guide (docs/writing-guide.md)\n\n$guide"
      rules  <- prompts.agent("article-rules-reviewer").flatMap(a => prompts.step(p, "rules").map(withDb(a, _)))
      cold   <- prompts.agent("article-cold-reader").flatMap(a => prompts.step(p, "cold-reader").map(n => s"$a\n\n$n"))
      skep   <- prompts.agent("article-skeptic").flatMap(a => prompts.step(p, "skeptic").map(withDb(a, _)))
      fixer  <- prompts.agent("article-fixer").flatMap(a => prompts.step(p, "fixer").map(withDb(a, _)))
    yield Systems(rules, cold, skep, fixer)).left.map(Halt.Fail(_))

  /** The running totals a round hands the next one; `fixed` = the fixer has run at least once. */
  private final case class Carry(rejected: List[Finding], applied: Int, rejectedCount: Int, fixed: Boolean)

  def run(ctx: RunCtx): Either[Halt, String] =
    val slug   = ctx.run.target
    val fix    = ctx.run.params.fixes
    val rounds = ReviewArticle.rounds(ctx.run.params.rounds, cfg.review.maxRounds, cfg.review.roundsCap, fix)
    for
      sys  <- systems
      last <- round(ctx, sys, slug, 1, rounds, fix, Carry(Nil, 0, 0, fixed = false))
      line <- ctx.code[String]("record")(record(ctx, slug, last._1, last._2, last._3))
    yield line

  /** One round, and — while it is not done — the fixer, the patch and the next round. Returns the
    * last round's merged result, its number, and the totals. */
  private def round(ctx: RunCtx, sys: Systems, slug: String, r: Int, rounds: Int, fix: Boolean,
      carry: Carry): Either[Halt, (Merged, Int, Carry)] =
    for
      snap    <- ctx.code[Snapshot](s"snapshot-$r")(snapshot(slug))
      mech    <- ctx.code[List[Finding]](s"mechanical-$r")(Right(mechanical(snap.slug, snap.kind, snap.body, isTarget(snap.body))))
      reports <- panel(ctx, sys, snap, r)
      merged  <- ctx.code[Merged](s"merge-$r")(Right(merge(r, mech, reports, carry.rejected)))
      _       <- ctx.code[List[String]](s"proposals-$r")(Right(propose(ctx, merged)))
      out     <-
        if done(merged, r, rounds, fix) then Right((merged, r, carry))
        else if snap.truncated then
          ctx.warn(s"round $r: the article is longer than agents.review.article-chars; it was reviewed cut and is not revised")
          Right((merged, r, carry))
        else
          for
            fixed   <- fixer(ctx, sys, snap, merged, r)
            patched <- ctx.code[ReviewArticle.Patched](s"patch-$r")(write(ctx, snap, fixed, r))
            next    <- round(ctx, sys, slug, r + 1, rounds, fix, Carry(
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
          a.updatedAt, cut))
    }

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

  private def reviewInput(s: Snapshot): String =
    val cut = if s.truncated then s"\n(The body is cut at ${cfg.review.articleChars} characters.)" else ""
    s"Review the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind}.$cut\n\n${Prompt.data("article", s.body)}"

  /** The three reviewers, in parallel. The step numbers are taken first, in a fixed order. */
  private def panel(ctx: RunCtx, sys: Systems, s: Snapshot, r: Int): Either[Halt, List[Report]] =
    val user = reviewInput(s)
    val seqs = ctx.reserve(3)
    ctx.parallel(List(
      () => ctx.llmAt[RulesAnswer, Report](seqs(0), s"rules-$r", Role.Reviewer, Prompt(sys.rules, user), RulesTools,
              ResponseSchema.derived[RulesAnswer]())((a, last) => checkRules(a, last).map(Checked(_, _))),
      () => ctx.llmAt[ColdReaderAnswer, Report](seqs(1), s"cold-reader-$r", Role.ColdReader, Prompt(sys.cold, user), Nil,
              ResponseSchema.derived[ColdReaderAnswer]())((a, last) => checkColdReader(a, last).map(Checked(_, _))),
      () => ctx.llmAt[SkepticAnswer, Report](seqs(2), s"skeptic-$r", Role.Skeptic, Prompt(sys.skeptic, user), SkepticTools,
              ResponseSchema.derived[SkepticAnswer]())((a, last) =>
                checkSkeptic(a, last, isCitekey, recordText, cfg.verify.quoteMinRatio).map(Checked(_, _)))
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

  private def fixer(ctx: RunCtx, sys: Systems, s: Snapshot, m: Merged, r: Int): Either[Halt, Fix] =
    val explain = m.explainBack.fold("")(e => s"\n\nThe cold reader's explain-back: $e")
    val user = s"Revise the article [[${s.slug}]] — \"${s.title}\", kind ${s.kind} — so that it answers every finding.\n\n" +
      Prompt.data("article", s.body) + s"\n\nThe findings of round $r:\n\n${findingsText(m)}$explain"
    ctx.llm[FixerAnswer, Fix](s"fixer-$r", Role.Fixer, Prompt(sys.fixer, user), FixerTools,
      ResponseSchema.derived[FixerAnswer](), Some(cfg.review.fixerMaxTokens)) { (a, last) =>
      checkFix(a, m, s.body, s.kind, isTarget(Option(a.body).getOrElse("")), isCitekey, recordText,
        cfg.verify.quoteMinRatio, last).map(Checked(_, _))
    }

  /** The one write of a round: the revised body, guarded by the snapshot's token. A 412 (someone
    * else wrote the article meanwhile) drops this fix — the next round reviews their text — and never
    * overwrites it. */
  private def write(ctx: RunCtx, s: Snapshot, f: Fix, r: Int): Either[String, ReviewArticle.Patched] =
    if f.body.trim == s.body.trim then Right(ReviewArticle.Patched(written = false, None, conflict = false))
    else
      h.patchArticle(s.slug, ArticlePatch(body = Some(f.body), ifUpdatedAt = s.updatedAt)) match
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
