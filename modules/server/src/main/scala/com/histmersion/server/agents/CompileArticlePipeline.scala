package com.histmersion.server.agents

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.approvals.Decided
import com.histmersion.domain.automation.workflows.{PipelineDef, Pipelines}

import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.Agents.{ApprovalKind, Pipeline, Role, RunInfo, RunRequest}
import com.histmersion.domain.CompileArticle.*
import com.histmersion.api.GoalPlans.{ArticleDraft, DraftMode, LinkKind}
import com.histmersion.api.Queue.QueueAdd
import com.histmersion.db.{AgentRepo, GoalLinkRepo}
import com.histmersion.server.Handlers
import com.histmersion.api.VocabularyCodecs.given
import hearth.kindlings.circederivation.KindlingsCodecAsObject
import io.circe.syntax.*

import javax.sql.DataSource

/** `compile-article <slug>` (docs/research-embedded-agents.md §4.5, §14): the five phases of the writing guide, run by
  * the server.
  *
  * Steps: snapshot (the article if it exists — then the run EXTENDS it — with its update token) · filter (the sources
  * the run's filter returns; when extending, those the article does not cite yet) · extract-1..n (LLM, no tools: one
  * source's record → claims, each quote found in the record by code) · evidence (code: statuses, disagreement edges
  * among the sources, due weight, Not used) · draft (LLM, no tools: the article, or the section to append; the code
  * self-check sends it back until it passes) · approve (BLOCKING by default: the run waits until a person approves the
  * draft) · write (a new article with `put_article` only while the slug is still free; an extension with `append` and
  * the snapshot's `ifUpdatedAt` — never an overwrite; either way the run's filter and the reasons for the sources it
  * leaves out are recorded with the article, #958, so its Not used list and Method note are generated, never written) ·
  * review (a review-article run with fix, chained) · close (the goal link and the log line).
  *
  * The filter: the run's own when it gives one, else the one the article records, else (extending an article that
  * records none) its title as the query.
  *
  * No step has a write tool. `startChild` starts the chained review run (origin `run`).
  *
  * A DRY run opens no approval (the draft is treated as approved), records the article it would create or the section
  * it would append (and the tasks, link and log lines) on their steps, and starts NO review run: there is no written
  * text for one to review.
  */
final class CompileArticlePipeline(
    h: Handlers,
    ds: DataSource,
    cfg: => AgentsConfig,
    startChild: (RunRequest, RunInfo) => Either[ApiError, RunInfo]
) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.CompileArticle
  import CompileArticlePipeline.*

  def run(ctx: RunCtx): Either[Halt, String] =
    val slug = ctx.run.target
    for
      snap <- ctx.code[Snap]("snapshot")(snapshot(ctx, slug))
      found <- ctx.code[Found]("filter")(filter(ctx, snap))
      line <-
        if found.toExtract.isEmpty then
          ctx.code[String]("close")(
            log(
              ctx,
              s"compile-article [[$slug]]: nothing new — the filter (${found.filter} → " +
                s"${found.total}) returns no source the article does not already cite (agent run ${ctx.run.id.take(8)})"
            )
          )
        else compile(ctx, snap, found)
    yield line

  private def compile(ctx: RunCtx, snap: Snap, found: Found): Either[Halt, String] =
    for
      extracted <- extractAll(ctx, snap, found)
      ev <- ctx.code[Evidence]("evidence")(Right(evidenceOf(found, extracted)))
      line <-
        if ev.used.isEmpty then
          ctx.code[String]("close")(
            log(
              ctx,
              s"compile-article [[${snap.slug}]]: no claim of the ${found.toExtract.size} " +
                s"source(s) could be verified; nothing drafted (agent run ${ctx.run.id.take(8)})"
            )
          )
        else
          for
            draft <- draft(ctx, snap, found, ev, extracted)
            decided <- ctx.approval("approve", ApprovalKind.ArticlePublish, snap.slug, cfg.approvals.publishMode)(
              Right(draft.asJson.noSpaces)
            )
            written <- ctx.code[Written]("write")(write(ctx, snap, draft, decided))
            review <- ctx.code[Option[String]]("review")(Right(review(ctx, snap, written)))
            closed <- ctx.code[String]("close")(close(ctx, draft, written, review))
          yield closed
    yield line

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def snapshot(ctx: RunCtx, slug: String): Either[String, Snap] =
    val p = ctx.run.params
    h.getArticle(slug) match
      case Right(v) =>
        val a = v.article
        val stored = com.histmersion.db.ArticleRepo.load(ds, a.slug)
        val f = CompileArticle.runFilter(
          p.query,
          p.topics.getOrElse(Nil),
          p.tags.getOrElse(Nil),
          stored.flatMap(_.filter),
          Some(a.title)
        )
        Right(
          Snap(
            slug,
            DraftMode.Extend,
            a.title,
            a.kind,
            a.body,
            a.updatedAt,
            f.flatMap(_.query),
            f.fold(Nil)(_.topics),
            f.fold(Nil)(_.tags),
            f,
            stored.fold(Nil)(x => CoverageReason.list(x.reasons))
          )
        )
      case Left(e) if e.code.contains(Write.CodeNotFound) =>
        val f = CompileArticle.runFilter(p.query, p.topics.getOrElse(Nil), p.tags.getOrElse(Nil), None, None)
        Right(
          Snap(
            slug,
            DraftMode.Create,
            p.title.getOrElse(slug),
            p.kind.getOrElse(DefaultKind),
            "",
            None,
            p.query,
            p.topics.getOrElse(Nil),
            p.tags.getOrElse(Nil),
            f
          )
        )
      case Left(e) => Left(e.message)

  private def isCitekey(ck: String): Boolean = Pipelines.isCitekey(h)(ck)

  private def filter(ctx: RunCtx, s: Snap): Either[String, Found] =
    val af = s.filter.getOrElse(ArticleFilter(query = s.query, topics = s.topics, tags = s.tags))
    val f = af.toFilter.copy(limit = cfg.compile.maxSources)
    val res = h.sources(f)
    val line = af.line
    val facts = res.hits.map(x => SourceFact(x.source.citekey, x.source.title, x.source.year, x.source.status))
    // cited, or listed (a hand-written Not used list, or a stored reason): handled already
    val already = AgentVerify.wikilinks(s.body).toSet ++ s.reasons.map(_.citekey)
    if res.total > facts.size then
      ctx.warn(s"filter: $line → ${res.total}; only the first ${facts.size} are read (agents.compile.max-sources)")
    Right(Found(line, res.total, facts, facts.map(_.citekey).filterNot(already)))

  /** A source's record, as an extraction reads it: summary, findings, notes, then the body, cut at
    * `agents.compile.record-chars`.
    */
  private def record(ck: String): Option[String] =
    h.getSource(ck, body = true).toOption.map { s =>
      val parts = List(
        s.source.summary.map(x => s"Summary:\n$x"),
        Option.when(s.findings.nonEmpty)("Findings:\n" + s.findings.map(f => s"- ${f.claim}").mkString("\n")),
        s.source.notes.map(n => s"Notes:\n$n"),
        s.source.ocrMarkdown.map(b => s"Text:\n$b")
      ).flatten
      parts.mkString("\n\n").take(cfg.compile.recordChars)
    }

  private def extractAll(ctx: RunCtx, s: Snap, found: Found): Either[Halt, List[Extracted]] =
    locally {
      found.toExtract.zipWithIndex.foldLeft[Either[Halt, List[Extracted]]](Right(Nil)) { case (acc, (ck, i)) =>
        acc.flatMap { sofar =>
          val text = record(ck).getOrElse("")
          val fact = found.sources.find(_.citekey == ck)
          val user = s"The article: \"${s.title}\" (${s.kind}) — its question is what the sources say about it.\n" +
            s"The source: [[$ck]] — ${fact.fold("")(_.title)} (${fact.flatMap(_.year).fold("n.d.")(_.toString)}), " +
            s"status ${fact.fold("unknown")(_.status.wire)}.\n\n${Prompt.data("source-record", text)}"
          ctx
            .llm[ExtractAnswer, Extracted](
              s"extract-${i + 1}",
              StepAgent.CompileExtractor,
              user,
              AnswerSchemas.extract
            ) { (a, last) =>
              CompileArticle
                .checkExtract(a, ck, text, cfg.verify.quoteMinRatio, cfg.compile.maxClaims, last)
                .map(Checked(_, _))
            }
            .map(sofar :+ _)
        }
      }
    }

  /** The evidence model: statuses from the filter, the disagreement edges among its sources (from `neighbors`, only
    * citekey targets), due weight and Not used.
    */
  private def evidenceOf(found: Found, extracted: List[Extracted]): Evidence =
    val edges = found.sources.flatMap { s =>
      h.neighbors(s.citekey, CompileArticle.DisagreementKinds.toList)
        .filter(n => n.direction == "out" && n.target == NeighborTarget.Source)
        .map(n => EdgeFact(s.citekey, n.kind, n.citekey))
    }
    CompileArticle.evidence(found.filter, found.total, found.sources, extracted, edges)

  private def draft(
      ctx: RunCtx,
      s: Snap,
      found: Found,
      ev: Evidence,
      extracted: List[Extracted]
  ): Either[Halt, ArticleDraft] =
    locally {
      val task = s.mode match
        case DraftMode.Create =>
          s"Write a NEW ${s.kind} article titled \"${s.title}\" (slug ${s.slug}) from the claim table below."
        case DraftMode.Extend =>
          s"EXTEND the ${s.kind} article \"${s.title}\" (slug ${s.slug}): write only what the new sources in the " +
            s"claim table add, and say under which existing heading it goes. The current article:\n\n" +
            Prompt.data("article", s.body)
      val user = s"$task\n\n${Prompt.data("evidence", CompileArticle.evidenceText(ev, extracted))}\n\n" +
        s"The article is compiled from ${found.filter} → ${found.total}. Code records this filter with the article and " +
        "generates its Not used list and Method note from it; give a reason for each source of the claim table you do " +
        "not cite in `reasons`." + ctx.notes
      ctx.llm[DraftAnswer, ArticleDraft](
        "draft",
        StepAgent.CompileWriter,
        user,
        AnswerSchemas.draft,
        Some(cfg.compile.draftMaxTokens)
      ) { (a, last) =>
        val resolve = h.pageLinks(AgentVerify.wikilinks(s.body + "\n" + Option(a.body).getOrElse("")))
        val corpus = Corpus(
          isCitekey,
          t => resolve(t).isDefined,
          ck => h.getSource(ck, body = false).toOption.map(_.source.status)
        )
        val title = Option.when(s.mode == DraftMode.Extend)(s.title).orElse(ctx.run.params.title)
        CompileArticle
          .checkDraft(a, s.slug, s.mode, s.kind, title, s.body, ev, found.filter, corpus, last)
          .map(Checked(_, _))
      }
    }

  /** The one write, only after approval: a new article only while its slug is still free (never an overwrite), an
    * extension appended with the snapshot's token (a 412 drops it, never overwrites).
    */
  private def write(ctx: RunCtx, s: Snap, d: ArticleDraft, decided: Decided): Either[String, Written] =
    def task(why: String): Unit =
      ctx.warn(why)
      val item = Pipelines.entry(
        ctx,
        s"compile-article [[${s.slug}]]: $why (agent run ${ctx.run.id.take(8)})",
        Some("agent compile")
      )
      val _ = ctx.write("tasks item", item.asJson)(h.ops.queueAdd("tasks", item).map(_ => ()))(Right(()))
    if !decided.approved then Right(Written(written = false, "the draft was rejected", None))
    else
      d.mode match
        case DraftMode.Create =>
          if AgentRepo.slugExists(ds, s.slug) then
            task("an article with this slug was created while the draft waited; it was NOT overwritten")
            Right(Written(written = false, "the slug was taken meanwhile", None))
          else
            val put = ArticlePut(d.title, d.kind, d.text, d.summary, s.tags, filter = s.filter, reasons = d.reasons)
            ctx.write(s"create article [[${s.slug}]]", put.asJson)(
              h.putArticle(s.slug, put).left.map(_.message).map(_ => Written(written = true, "created", None))
            )(Right(Written(written = false, AgentsMarkdown.DryRunNote, None)))
        case DraftMode.Extend =>
          // a stored reason for a source the new text now cites is dropped (an empty reason removes it)
          val stale = s.reasons.filter(r => d.cited.contains(r.citekey)).map(_.copy(reason = ""))
          val patch = ArticlePatch(
            append = Some(d.text),
            section = d.section,
            ifUpdatedAt = s.updatedAt,
            filter = s.filter,
            reasons = d.reasons ++ stale
          )
          ctx.write(s"append to article [[${s.slug}]]${d.section.fold("")(x => s" under '$x'")}", patch.asJson)(
            h.patchArticle(s.slug, patch).map(_ => true)
          )(Right(false)) match
            case Right(false) => Right(Written(written = false, AgentsMarkdown.DryRunNote, None))
            case Right(_)     => Right(Written(written = true, "appended", AgentRepo.latestArticleVersion(ds, s.slug)))
            case Left(e) if e.code.contains(Write.CodePrecondition) =>
              task(
                "the article changed while the draft waited for approval; the section was NOT appended — compile again"
              )
              Right(Written(written = false, "the article changed meanwhile", None))
            case Left(e) => Left(e.message)

  /** The independent review (phase 5): a review-article run with fix, started by this one. A refusal (no reviewer model
    * configured) is a warning: the article is written either way.
    */
  private def review(ctx: RunCtx, s: Snap, w: Written): Option[String] =
    if !w.written || !ctx.run.params.chainReview.getOrElse(cfg.compile.review) then None
    else
      // the run's per-role models carry over to the roles the review uses
      val overrides =
        ctx.run.params.overrides.filter(o => Role.parse(o.role).exists(Pipeline.ReviewArticle.roles.contains))
      startChild(
        RunRequest(
          Pipeline.ReviewArticle.wire,
          s.slug,
          model = ctx.run.model,
          fix = Some(true),
          goal = ctx.run.goal,
          modelFor = overrides
        ),
        ctx.run
      ) match
        case Right(r) => Some(r.id)
        case Left(e)  =>
          ctx.warn(s"review: no review-article run started — ${e.message}; run it by hand")
          None

  private def close(ctx: RunCtx, d: ArticleDraft, w: Written, review: Option[String]): Either[String, String] =
    if w.written then
      ctx.run.goal.foreach(g => GoalLinkRepo.link(ds, g, LinkKind.Article, d.slug, Some("compiled by an agent run")))
    log(ctx, CompileArticle.logLine(d, w.written, w.why, review, ctx.run.id))

  private def log(ctx: RunCtx, line: String): Either[String, String] = Pipelines.log(h, ctx, line)

object CompileArticlePipeline:
  /** The kind of a new article when the run names none. */
  val DefaultKind = "concept"

  /** The article as the run found it (checkpointed): `body` empty for a new one. `filter` is the filter the run reads
    * and records (#958); `reasons` the reasons the article stores already.
    */
  final case class Snap(
      slug: String,
      mode: DraftMode,
      title: String,
      kind: String,
      body: String,
      updatedAt: Option[String],
      query: Option[String],
      topics: List[String],
      tags: List[String],
      filter: Option[ArticleFilter] = None,
      reasons: List[CoverageReason] = Nil
  ) derives KindlingsCodecAsObject

  /** The filter's sources, and the ones to extract (when extending: not cited yet). */
  final case class Found(filter: String, total: Int, sources: List[SourceFact], toExtract: List[String])
      derives KindlingsCodecAsObject

  /** What the write did; `previousVersion` holds the text an extension replaced (undo from it). */
  final case class Written(written: Boolean, why: String, previousVersion: Option[Long]) derives KindlingsCodecAsObject
