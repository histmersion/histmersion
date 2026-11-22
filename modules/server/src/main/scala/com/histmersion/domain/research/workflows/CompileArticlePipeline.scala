package com.histmersion
package domain.research.workflows
import com.histmersion.util.json.RawJson.*
import com.histmersion.util.json.RawJson
import com.histmersion.util.problem.{Problem, Problems}
import com.histmersion.util.problem.Text.{sentence, sentences}

import hearth.kindlings.optics.syntax.*

import io.scalaland.chimney.dsl.*

import com.histmersion.domain.automation.agents.*
import com.histmersion.domain.automation.approvals.Decided
import com.histmersion.domain.automation.workflows.PipelineDef
import com.histmersion.domain.automation.workflows.Pipelines

import com.histmersion.api.Write
import com.histmersion.config.*
import com.histmersion.domain.*
import com.histmersion.api.automation.Agents.{ApprovalKind, Pipeline, Role, RunInfo, RunRequest}
import com.histmersion.domain.CompileArticle.*
import com.histmersion.api.automation.Goal.Plans.{ArticleDraft, DraftMode, LinkKind}
import com.histmersion.api.automation.Queue.QueueAdd
import com.histmersion.domain.automation.goals.GoalService
import com.histmersion.api.VocabularyCodecs.given
import com.histmersion.util.json.JsonCodec
import com.histmersion.api.automation.AgentsMarkdown
import com.histmersion.api.library.NeighborTarget
import com.histmersion.api.Api
import com.histmersion.api.research.Article
import com.histmersion.api.research.Coverage
import com.histmersion.api.json.NewtypeCodecs.given
import com.histmersion.api.text.WikiLinks
import com.histmersion.domain.automation.agents.AgentRepo.StepRecord
import com.histmersion.domain.research.Slug
import com.histmersion.domain.automation.approvals.ApprovalQuery
import com.histmersion.domain.automation.Approval
import com.histmersion.domain.library.Citekey
import com.histmersion.domain.library.sources.SourcesControllers
import com.histmersion.api.library.Filter
import com.histmersion.domain.automation.agents.RunCtx
import com.histmersion.domain.library.Edge
import com.histmersion.domain.automation.worklists.WorkListControllers
import com.histmersion.api.automation.Goal
import com.histmersion.domain.automation.Run
import com.histmersion.domain.automation.managers.ManagerContext
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.database.WritePermit
import com.histmersion.util.database.Unused

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
    site: ResearchSite,
    goals: GoalService,
    cfg: => AgentsConfig,
    research: => ResearchAgentsConfig,
    lib: => LibraryAgentsConfig,
    startChild: (RunRequest, RunInfo) => Either[Api.Error, RunInfo]
) extends PipelineDef:
  val pipeline: Pipeline = Pipeline.CompileArticle
  import CompileArticlePipeline.*

  def run(ctx: RunCtx)(using WritePermit): Either[Halt, PipelineDef.Summary] =
    for
      snap <- ctx.code[Snap](StepRecord.Name("snapshot"))(snapshot(ctx, ctx.run.target))
      found <- ctx.code[Found](StepRecord.Name("filter"))(filter(ctx, snap))
      line <-
        if found.toExtract.isEmpty then
          ctx.code[String](StepRecord.Name("close"))(
            log(
              ctx,
              s"compile-article [[${snap.slug}]]: nothing new — the filter (${found.filter} → " +
                s"${found.total}) returns no source the article does not already cite (agent run ${ctx.run.id.value.take(8)})"
            )
          )
        else compile(ctx, snap, found)
    yield PipelineDef.Summary(line)

  private def compile(ctx: RunCtx, snap: Snap, found: Found)(using WritePermit): Either[Halt, String] =
    for
      extracted <- extractAll(ctx, snap, found)
      ev <- ctx.code[Evidence](StepRecord.Name("evidence"))(Right(evidenceOf(found, extracted)))
      line <-
        if ev.used.isEmpty then
          ctx.code[String](StepRecord.Name("close"))(
            log(
              ctx,
              s"compile-article [[${snap.slug}]]: no claim of the ${found.toExtract.size} " +
                s"source(s) could be verified; nothing drafted (agent run ${ctx.run.id.value.take(8)})"
            )
          )
        else
          for
            draft <- draft(ctx, snap, found, ev, extracted)
            decided <- ctx.approval(
              StepRecord.Name("approve"),
              ApprovalKind.ArticlePublish,
              ApprovalQuery.Subject(snap.slug.value),
              io.scalaland.chimney.Transformer
                .derive[Approval.Mode, com.histmersion.api.automation.Approval.Mode]
                .transform(cfg.approvals.publishMode)
            )(
              Right(draft.asJson.noSpaces)
            )
            written <- ctx.code[Written](StepRecord.Name("write"))(write(ctx, snap, draft, decided))
            review <- ctx.code[Option[String]](StepRecord.Name("review"))(Right(review(ctx, snap, written)))
            closed <- ctx.code[String](StepRecord.Name("close"))(close(ctx, draft, written, review))
          yield closed
    yield line

  // ── steps ──────────────────────────────────────────────────────────────────────────────────────

  private def snapshot(ctx: RunCtx, target: RunInfo.Target): Either[Problems, Snap] =
    val p = ctx.run.params
    Slug.parse(target.value).flatMap { slug =>
      site.articles.get(slug) match
        case Right(v) =>
          val a = v.article
          val stored = site.articleStore.load(Slug.assume(a.slug.value))
          val f = CompileArticle.runFilter(
            p.query.map(q => Article.Filter.Query(q.value)),
            p.topics.getOrElse(Nil).map(t => Article.Filter.Topics(t.value)),
            p.tags.getOrElse(Nil).map(t => Article.Filter.Tags(t.value)),
            stored.flatMap(_.filter),
            Some(Title(a.title.value))
          )
          Right(
            Snap(
              slug,
              DraftMode.Extend,
              Title(a.title.value),
              a.kind,
              a.body,
              a.updatedAt,
              f.flatMap(_.query),
              f.fold(Nil)(_.topics),
              f.fold(Nil)(_.tags),
              f,
              stored.fold(Nil)(x => Coverage.Reason.list(x.reasons))
            )
          )
        case Left(e) if e.code.exists(_.value == Write.CodeNotFound) =>
          val query = p.query.map(q => Article.Filter.Query(q.value))
          val topics = p.topics.getOrElse(Nil).map(t => Article.Filter.Topics(t.value))
          val tags = p.tags.getOrElse(Nil).map(t => Article.Filter.Tags(t.value))
          val f = CompileArticle.runFilter(query, topics, tags, None, None)
          Right(
            Snap(
              slug,
              DraftMode.Create,
              Title(p.title.fold(slug.value)(_.value)),
              Article.Kind.Wire(p.kind.fold(DefaultKind)(_.value)),
              Article.Body(""),
              None,
              query,
              topics,
              tags,
              f
            )
          )
        case Left(e) => Left(e.problems)
    }

  private def isCitekey(ck: Citekey): Boolean =
    site.sources.get(ck, body = SourcesControllers.GetBody(false)).left.map(Api.Error(_)).isRight

  private def filter(ctx: RunCtx, s: Snap)(using WritePermit): Either[Problems, Found] =
    val af = s.filter.getOrElse(Article.Filter(query = s.query, topics = s.topics, tags = s.tags))
    val f = af.toFilter.copy(limit = Filter.Limit(research.compile.maxSources.value))
    val res = site.search.sources(f)
    val line = af.line
    val facts = res.hits.map(x =>
      SourceFact(
        Citekey.assume(x.source.citekey.value),
        Title(x.source.title.value),
        x.source.year.map(y => Year(y.value.toInt)),
        x.source.status
      )
    )
    // cited, or listed (a hand-written Not used list, or a stored reason): handled already
    val already: Set[String] =
      AgentVerify.wikilinks(AgentVerify.Text(s.body.value)).map(w => w.value: String).toSet ++ s.reasons.map(r =>
        r.citekey.value: String
      )
    if res.total.value > facts.size then
      ctx.warn(
        s"filter: $line → ${res.total.value}; only the first ${facts.size} are read (agents.compile.max-sources)"
      )
    Right(
      Found(
        ArticleDraft.Filter(line),
        RowCount(res.total.value),
        facts,
        facts.map(_.citekey).filterNot(ck => already(ck.value))
      )
    )

  /** A source's record, as an extraction reads it: summary, findings, notes, then the body, cut at
    * `agents.compile.record-chars`.
    */
  private def record(ck: Citekey): Option[String] =
    site.sources.get(ck, body = SourcesControllers.GetBody(true)).left.map(Api.Error(_)).toOption.map { s =>
      val parts = List(
        s.source.summary.map(x => s"Summary:\n$x"),
        Option.when(s.findings.nonEmpty)("Findings:\n" + s.findings.map(f => s"- ${f.claim}").mkString("\n")),
        s.source.notes.map(n => s"Notes:\n$n"),
        s.source.ocrMarkdown.map(b => s"Text:\n$b")
      ).flatten
      parts.mkString("\n\n").take(research.compile.recordChars.value)
    }

  private def extractAll(ctx: RunCtx, s: Snap, found: Found)(using WritePermit): Either[Halt, List[Extracted]] =
    locally {
      found.toExtract.zipWithIndex.foldLeft[Either[Halt, List[Extracted]]](Right(Nil)) { case (acc, (ck, i)) =>
        acc.flatMap { sofar =>
          val text = record(ck).getOrElse("")
          val fact = found.sources.find(_.citekey == ck)
          val user = s"The article: \"${s.title}\" (${s.kind}) — its question is what the sources say about it.\n" +
            s"The source: [[$ck]] — ${fact.fold("")(_.title.value)} (${fact.flatMap(_.year).fold("n.d.")(_.toString)}), " +
            s"status ${fact.fold("unknown")(_.status.wire.value)}.\n\n${Prompt.data("source-record", text)}"
          ctx
            .llm[ExtractAnswer, Extracted](
              StepRecord.Name(s"extract-${i + 1}"),
              StepAgent.CompileExtractor,
              Prompt.UserText(user),
              ResearchAnswers.extract
            ) { (a, last) =>
              CompileArticle
                .checkExtract(
                  a,
                  ck,
                  AgentVerify.SourceBody(text),
                  AgentVerify.MinRatio(lib.verify.quoteMinRatio.value),
                  MaxClaims(research.compile.maxClaims.value),
                  CompileArticle.Last(last.value)
                )
                .map((v, ws) => Checked(v, ws.map(w => Checked.Warnings(w.sentence))))
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
      site.citations
        .neighbors(s.citekey, CompileArticle.DisagreementKinds.toList.map(k => Edge.Kind.Wire(k.value)))
        .filter(n => n.direction.value == "out" && n.target == NeighborTarget.Source)
        .map(n =>
          EdgeFact(
            s.citekey,
            Article.Coverage.DisagreementKind(com.histmersion.api.library.Edge.Kind.wireOf(n.kind).value),
            Citekey.assume(n.citekey.value)
          )
        )
    }
    CompileArticle.evidence(found.filter, found.total, found.sources, extracted, edges)

  private def draft(
      ctx: RunCtx,
      s: Snap,
      found: Found,
      ev: Evidence,
      extracted: List[Extracted]
  )(using WritePermit): Either[Halt, ArticleDraft] =
    locally {
      val task = s.mode match
        case DraftMode.Create =>
          s"Write a NEW ${s.kind} article titled \"${s.title}\" (slug ${s.slug}) from the claim table below."
        case DraftMode.Extend =>
          s"EXTEND the ${s.kind} article \"${s.title}\" (slug ${s.slug}): write only what the new sources in the " +
            s"claim table add, and say under which existing heading it goes. The current article:\n\n" +
            Prompt.data("article", s.body.value)
      val user = s"$task\n\n${Prompt.data("evidence", CompileArticle.evidenceText(ev, extracted).value)}\n\n" +
        s"The article is compiled from ${found.filter} → ${found.total}. Code records this filter with the article and " +
        "generates its Not used list and Method note from it; give a reason for each source of the claim table you do " +
        "not cite in `reasons`." + ctx.notes
      ctx.llm[DraftAnswer, ArticleDraft](
        StepRecord.Name("draft"),
        StepAgent.CompileWriter,
        Prompt.UserText(user),
        ResearchAnswers.draft,
        Some(RunCtx.MaxTokens(research.compile.draftMaxTokens.value))
      ) { (a, last) =>
        val resolve = site.articles.pageLinks(
          AgentVerify
            .wikilinks(AgentVerify.Text(s.body.value + "\n" + Option(a.body).fold("")(_.value)))
            .map(w => WikiLinks.Occurrence.Target(w.value))
        )
        val corpus = Corpus(
          isCitekey,
          t => resolve(WikiLinks.Occurrence.Target(t.value)).isDefined,
          ck =>
            site.sources
              .get(ck, body = SourcesControllers.GetBody(false))
              .left
              .map(Api.Error(_))
              .toOption
              .map(_.source.status)
        )
        val title =
          Option.when(s.mode == DraftMode.Extend)(s.title).orElse(ctx.run.params.title.map(t => Title(t.value)))
        CompileArticle
          .checkDraft(
            a,
            s.slug,
            s.mode,
            s.kind,
            title,
            s.body,
            ev,
            found.filter,
            corpus,
            CompileArticle.Last(last.value)
          )
          .map((v, ws) => Checked(v, ws.map(w => Checked.Warnings(w.sentence))))
      }
    }

  /** The one write, only after approval: a new article only while its slug is still free (never an overwrite), an
    * extension appended with the snapshot's token (a 412 drops it, never overwrites).
    */
  private def write(ctx: RunCtx, s: Snap, d: ArticleDraft, decided: Decided)(using
      WritePermit
  ): Either[Problems, Written] =
    def task(why: String)(using WritePermit): Unit =
      ctx.warn(why)
      val item = Pipelines.entry(
        ctx,
        Pipelines.EntryText(s"compile-article [[${s.slug}]]: $why (agent run ${ctx.run.id.value.take(8)})"),
        Some(Pipelines.EntryTopic("agent compile"))
      )
      val _ = ctx.write("tasks item", item.asJson)(
        site.workLists.record(WorkListControllers.ListName("tasks"), item, ctx.creator).map(_ => ())
      )(Right(()))
    if !decided.approved.value then
      Right(Written(written = Written.Value(false), Rationale("the draft was rejected"), None))
    else
      d.mode match
        case DraftMode.Create =>
          if site.articleStore.exists(s.slug) then
            task("an article with this slug was created while the draft waited; it was NOT overwritten")
            Right(
              Written(
                written = Written.Value(false),
                Rationale("the slug was taken meanwhile"),
                None
              )
            )
          else
            val put = Article.Put(
              d.title,
              d.kind,
              Article.Body(d.text.value),
              d.summary,
              s.tags.map(t => Article.Put.Tags(t.value)),
              filter = s.filter,
              reasons = d.reasons
            )
            ctx.write(s"create article [[${s.slug}]]", put.asJson)(
              site.articles
                .put(s.slug, put)
                .left
                .map(_.problems)
                .map(_ => Written(written = Written.Value(true), Rationale("created"), None))
            )(
              Right(
                Written(
                  written = Written.Value(false),
                  Rationale(AgentsMarkdown.DryRunNote),
                  None
                )
              )
            )
        case DraftMode.Extend =>
          // a stored reason for a source the new text now cites is dropped (an empty reason removes it)
          val stale =
            s.reasons
              .filter(r => d.cited.contains(r.citekey))
              .map(_.modify(_.reason).setTo(Coverage.Rationale("")))
          val patch = Article.Patch(
            append = Some(Article.Patch.Append(d.text.value)),
            section = d.section.map(x => Article.Patch.Section(x.value)),
            ifUpdatedAt = s.updatedAt.map(u => Article.Patch.IfUpdatedAt(u.value)),
            filter = s.filter,
            reasons = d.reasons ++ stale
          )
          ctx.write(s"append to article [[${s.slug}]]${d.section.fold("")(x => s" under '$x'")}", patch.asJson)(
            site.articles.patch(s.slug, patch).map(_ => true)
          )(Right(false)) match
            case Right(false) =>
              Right(
                Written(
                  written = Written.Value(false),
                  Rationale(AgentsMarkdown.DryRunNote),
                  None
                )
              )
            case Right(_) =>
              Right(
                Written(
                  written = Written.Value(true),
                  Rationale("appended"),
                  site.articleStore.latestVersion(s.slug).map(v => Written.PreviousVersion(v.value))
                )
              )
            case Left(e) if e.code.exists(_.value == Write.CodePrecondition) =>
              task(
                "the article changed while the draft waited for approval; the section was NOT appended — compile again"
              )
              Right(
                Written(
                  written = Written.Value(false),
                  Rationale("the article changed meanwhile"),
                  None
                )
              )
            case Left(e) => Left(e.problems)

  /** The independent review (phase 5): a review-article run with fix, started by this one. A refusal (no reviewer model
    * configured) is a warning: the article is written either way.
    */
  private def review(ctx: RunCtx, s: Snap, w: Written)(using WritePermit): Option[String] =
    if !w.written.value || !ctx.run.params.chainReview.map(_.value).getOrElse(research.compile.review.value) then None
    else
      // the run's per-role models carry over to the roles the review uses
      val overrides =
        ctx.run.params.overrides.filter(o => Role.parse(o.role).exists(Pipeline.ReviewArticle.roles.contains))
      startChild(
        RunRequest(
          RunRequest.Pipeline(Pipeline.ReviewArticle.wire.value),
          RunRequest.Target(s.slug.value),
          model = ctx.run.model.map(m => RunRequest.Model(m.value)),
          fix = Some(RunRequest.Fix(true)),
          goal = ctx.run.goal,
          modelFor = overrides
        ),
        ctx.run
      ) match
        case Right(r) => Some(r.id.value)
        case Left(e)  =>
          ctx.warn(s"review: no review-article run started — ${e.problems.sentences.mkString("; ")}; run it by hand")
          None

  private def close(ctx: RunCtx, d: ArticleDraft, w: Written, review: Option[String])(using
      WritePermit
  ): Either[Problems, String] =
    if w.written.value then
      Pipelines
        .goalOf(ctx.run)
        .foreach(g =>
          Unused(
            goals.link(
              g,
              io.scalaland.chimney.Transformer
                .derive[Goal.LinkKind, domain.automation.Goal.LinkKind]
                .transform(LinkKind.Article),
              Goal.Plans.GoalLink.Ref(d.slug.value),
              Some(Goal.Plans.GoalLink.Note("compiled by an agent run"))
            ),
            Unused.Why.Idempotent
          )
        )
    log(
      ctx,
      CompileArticle
        .logLine(
          d,
          CompileArticle.Written(w.written.value),
          w.why,
          review.map(Run.Id.assume(_)),
          ctx.runId
        )
        .value
    )

  private def log(ctx: RunCtx, line: String): Either[Problems, String] =
    Pipelines.log(site.workLists, ctx, Pipelines.LogLine(line)).map(_.value)

object CompileArticlePipeline:

  def written(c: ManagerContext, run: Run.Id): Option[Written] =
    c.stepOutput(run, StepRecord.Name("write")).flatMap(o => RawJson.decode[Written](o.value).toOption)

  /** The kind of a new article when the run names none. */
  val DefaultKind = "concept"

  /** The article as the run found it (checkpointed): `body` empty for a new one. `filter` is the filter the run reads
    * and records (#958); `reasons` the reasons the article stores already.
    */
  final case class Snap(
      slug: Slug,
      mode: DraftMode,
      title: Title,
      kind: Article.Kind.Wire,
      body: Article.Body,
      updatedAt: Option[Article.UpdatedAt],
      query: Option[Article.Filter.Query],
      topics: List[Article.Filter.Topics],
      tags: List[Article.Filter.Tags],
      filter: Option[Article.Filter] = None,
      reasons: List[Coverage.Reason] = Nil
  ) derives JsonCodec

  /** The filter's sources, and the ones to extract (when extending: not cited yet). */
  final case class Found(
      filter: ArticleDraft.Filter,
      total: RowCount,
      sources: List[SourceFact],
      toExtract: List[Citekey]
  ) derives JsonCodec

  /** What the write did; `previousVersion` holds the text an extension replaced (undo from it). */
  final case class Written(written: Written.Value, why: Rationale, previousVersion: Option[Written.PreviousVersion])
      derives JsonCodec

  object Written:
    type Value = Value.T
    object Value extends Newtype[Boolean, Pure]
    type PreviousVersion = PreviousVersion.T
    object PreviousVersion extends Newtype[Long, Pure]
