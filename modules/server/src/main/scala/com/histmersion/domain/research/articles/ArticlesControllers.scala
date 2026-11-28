package com.histmersion
package domain.research.articles

import com.histmersion.util.problem.{Legacy, Problem}
import io.scalaland.chimney.dsl.*
import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Citekey
import com.histmersion.api.text.WikiLinks
import com.histmersion.util.identity.AliasTable

import com.histmersion.api.aliases.AliasKinds

import com.histmersion.api.research.BacklinksMarkdown

import com.histmersion.api.{Api, Write}
import com.histmersion.config.AppConfig
import com.histmersion.domain.{ArticleReviewStatus, Caller, ReviewArticle, ReviewLedger}
import com.histmersion.domain.catalogue.places.PlaceScope
import com.histmersion.domain.library.ingest.SeedImport
import com.histmersion.domain.library.search.SearchFilters
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import com.histmersion.domain.research.articles.review.ReviewStateService
import com.histmersion.domain.research.articles.ArticleNote
import com.histmersion.server.{LinkTarget, MarkdownLint}
import com.histmersion.util.data.maintenance.WriteGate
import com.histmersion.api.aliases.AliasKind
import com.histmersion.api.aliases.{LabelRename, LabelRenamed, LabelRenames}
import com.histmersion.api.catalogue.{ArticleEventLead, Period}
import com.histmersion.api.checks.{FormatField, FormatIssue, FormatReport, FormatSeverity}
import com.histmersion.api.library.{Filter, SeedResult}
import com.histmersion.api.research.{
  Article,
  Articles,
  Backlink,
  Coverage,
  CoverageSearch,
  CritiquePair,
  CritiquePairing,
  CritiqueReason,
  Critiques,
  ResolvedArticle
}
import com.histmersion.api.library.Source
import com.histmersion.domain.library.Edge
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.automation.Run
import com.histmersion.domain.research.workflows.ReviewManagerPolicy
import com.histmersion.api.exchange.Bundle
import io.github.iltotore.iron.constraint.any.Pure
import com.histmersion.util.problem.Problems
import com.histmersion.util.database.WritePermit
import com.histmersion.domain.Creator
import com.histmersion.lint.{tolerated, LintRule}

/** The articles' operations, each the one implementation REST, MCP and the agents' tools call: the list and one article
  * in every view, the writes (put, patch, delete, rename, a note imported) with the strict formatting gate, the
  * versions, the coverage of the filter an article records with its critique checklist and pairing rule, the review
  * state, a draft checked as the review checks it, the formatting lint and its repair, the export, and how a page's
  * `[[wikilinks]]` resolve. Slugs are normalised here; an old slug follows to the article it was renamed to.
  *
  * `ledgers` reads an article's review ledger (none: never reviewed; an error: one that no longer decodes),
  * `articleLeads` the catalogue's leads of an article's events, `note` the article's note as the mirror renders it.
  */
final class ArticlesControllers(
    val store: ArticleService,
    search: ArticleSearch,
    coverageReads: CoverageService,
    reviewState: ReviewStateService,
    sources: SourcesControllers,
    sourceStore: SourceService,
    filters: SearchFilters,
    ledgers: Slug => Either[Problem, Option[ReviewLedger.Ledger]],
    articleLeads: (ArticleEventLead.Body, List[Citekey], Option[Period]) => List[ArticleEventLead],
    note: Slug => Either[Api.Error, domain.research.Article.Note],
    current: (AliasKind, AliasTable.Id) => AliasTable.Id,
    cfg: () => AppConfig,
    maintenance: WriteGate
):

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def error(message: Problem, code: String): Api.Error =
    Api.Error(message, Some(Api.Error.Code(code)))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def notFound(slug: Slug): Api.Error = Articles.notFound(Article.Slug(slug.value))

  /** The slug of an article read back from the store. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def slugOf(a: Article): Slug = Slug.assume(a.slug.value)

  /** The slug an old slug was renamed to, as the alias tables hold it. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def currentSlug(slug: Slug): Slug =
    Slug.assume(current(AliasKinds.ArticleRecords, AliasTable.Id(slug.value)).value)

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def apiCitekey(ck: Citekey): com.histmersion.api.library.Citekey =
    com.histmersion.api.library.Citekey(ck.value)

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private val disagreementKinds: List[Edge.Kind.Wire] =
    Article.Coverage.DisagreementKinds.map(k => Edge.Kind.Wire(k.value))

  private def resolve(f: Filter): Filter = filters.page(f)
  private def scopeOf(f: Filter): PlaceScope = filters.scope(f)

  // ── formatting: what in the agent-written Markdown will not display as meant ─────────────────

  /** The formatting problems of an article's body and summary, its links resolved as the page resolves them. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def formatting(body: Article.Body, summary: Option[Article.SummaryText]): List[FormatIssue] =
    val links = pageLinks(
      MarkdownLint
        .lookups(MarkdownLint.Scan.Text(body.value) :: summary.map(s => MarkdownLint.Scan.Text(s.value)).toList)
        .map(t => t)
    )
    MarkdownLint.ofArticle(
      MarkdownLint.Scan.Text(body.value),
      summary.map(s => ArticleSummaryRow.Summary(s.value)),
      ck => links(ck)
    )

  /** `strict`: refuse a write whose text has a formatting ERROR; otherwise the problems ride along in the response, and
    * the write goes through.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  private def strictGate(
      strict: domain.research.Article.StrictMode,
      issues: => List[FormatIssue]
  ): Either[Api.Error, Unit] =
    if !strict.value then Right(())
    else MarkdownLint.refusal(issues).fold(Right(()))(m => Left(error(m, Write.CodeInvalid)))

  /** `GET /api/articles/{slug}/lint`. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def lint(slug: Slug): Either[Api.Error, FormatReport] =
    get(slug).map(v => FormatReport(FormatReport.Subject(v.article.slug.value), v.formatting))

  /** `POST /api/articles/{slug}/lint/fix`: the safe repairs of the body and summary, written guarded by the article's
    * token, then the lint again.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def fix(slug: Slug)(using Creator.New): Either[Api.Error, FormatReport] =
    maintenance.guard(WriteGate.OperationName("fix_article_format")):
      store.load(store.canonical(slug)).toRight(notFound(slug)).flatMap { a =>
        val (body, fb) = MarkdownLint.fix(
          MarkdownLint.Scan.Text(a.body.value),
          FormatField.Body,
          MarkdownLint.Context(article = MarkdownLint.Context.Article(true))
        )
        val (summary, fs) =
          a.summary
            .map(t => MarkdownLint.fix(MarkdownLint.Scan.Text(t.value), FormatField.Summary))
            .fold((None, Nil))((t, f) => (Some(t), f))
        val done = fb.map("body: " + _) ++ fs.map("summary: " + _)
        val write =
          if done.isEmpty then Right(())
          else
            store
              .patch(
                slugOf(a),
                Article.Patch(
                  body = Some(Article.Body(body.value)).filter(_ => fb.nonEmpty),
                  summary = summary.map(s => Article.SummaryText(s.value)).filter(_ => fs.nonEmpty),
                  ifUpdatedAt = a.updatedAt.map(u => Article.Patch.IfUpdatedAt(u.value))
                ),
                Caller.Local
              )
              .map(_ => ())
        write.flatMap(_ => lint(slugOf(a))).map(_.modify(_.fixed).setTo(done.map(FormatReport.Fixed(_))))
      }

  /** A whole body written by the mechanical pairing repair, guarded by the token it was computed against: whether it
    * was written.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def writeRepairedBody(slug: Slug, body: Article.Body, token: Option[BArticleRow.UpdatedAt])(using
      WritePermit
  )(using Creator.New): Boolean =
    store
      .patch(
        slug,
        Article.Patch(body = Some(body), ifUpdatedAt = token.map(t => Article.Patch.IfUpdatedAt(t.value))),
        Caller.Local
      )
      .isRight

  // ── a draft of an article, checked as review-article's fixer validation checks it ──────────────

  /** What [[ReviewArticle.checkBody]] needs from the corpus, resolved once for every link in `texts` (the body and the
    * draft's texts): `[[x]]` as a source, as anything, and the formatting ERRORS of a text (the `--strict` gate's), one
    * position-free line each. `generated`: the article records a filter, so its Not used list and Method note are
    * generated and must not be written into the body.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def bodyCorpus(
      texts: List[domain.research.Article.Body],
      generated: ReviewArticle.BodyCorpus.Generated = ReviewArticle.BodyCorpus.Generated.Given
  ): ReviewArticle.BodyCorpus =
    val links = pageLinks(
      MarkdownLint
        .lookups(texts.map(t => MarkdownLint.Scan.Text(t.value)))
        .map(t => t)
    )
    ReviewArticle.BodyCorpus(
      t => links(WikiLinks.Occurrence.Target(t.value)).exists(_.isInstanceOf[Source.Ref]),
      t => links(WikiLinks.Occurrence.Target(t.value)).isDefined,
      text =>
        MarkdownLint
          .ofArticle(MarkdownLint.Scan.Text(text.value), None, ck => links(ck))
          .filter(_.severity == FormatSeverity.Error)
          .map(i =>
            Problem.Content.Answer(
              Problem.Content.Requirement
                .FormatClean(Problem.Raw.of(i.rule.wire), Problem.Raw.of(i.message), Problem.Raw.of(i.excerpt))
            )
          )
          .distinct,
      generated
    )

  /** `POST /api/articles/{slug}/check` (MCP and agent tool `check_edits`) — read only. Edits are applied to the STORED
    * body; a whole `body` is checked as it stands (the article need not exist yet: a compile writer's draft). The same
    * function the review pipeline validates its fixer with.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def check(slug: Slug, in: Article.Edits.ArticleCheckRequest): Either[Api.Error, Article.Edits.ArticleCheck] =
    in.draft.left.map(m => error(m, Write.CodeInvalid)).flatMap { draft =>
      val stored = store.load(store.canonical(slug))
      (draft, stored) match
        case (Article.Edits.Draft.Edits(_), None) => Left(notFound(slug))
        case _                                    =>
          val before = stored.fold("")(_.body.value)
          val kind = in.kind.map(_.value.trim).filter(_.nonEmpty).orElse(stored.map(_.kind.value)).getOrElse("concept")
          val texts = draft match
            case Article.Edits.Draft.Edits(es) => before :: es.flatMap(_.text).map(_.value)
            case Article.Edits.Draft.Body(t)   => List(before, t.value)
          // a new article is compiled with a filter: its coverage sections will be generated
          val generated = stored.forall(_.filter.isDefined)
          Right(
            ReviewArticle.checkBody(
              stored.fold(slug)(slugOf),
              ReviewArticle.Fix.Body(before),
              ReviewArticle.Snapshot.Kind(kind),
              draft,
              bodyCorpus(
                texts.map(domain.research.Article.Body(_)),
                (if generated then ReviewArticle.BodyCorpus.Generated.Generated
                 else ReviewArticle.BodyCorpus.Generated.Given)
              )
            )
          )
    }

  // ── the list, one article, its review ───────────────────────────────────────────────────────

  /** `GET /api/articles` — the compiled articles citing the filter's sources, narrowed by kind and searched with
    * `text`, each with its review badge.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def list(q: Api.ArticleQuery): Article.List =
    val f = resolve(q.filter)
    val list = search.list(
      f,
      scopeOf(f),
      q.kind.map(k => domain.research.Article.Kind(k.value)),
      q.text.map(t => Search.Query(t.value)),
      ArticleRepo.Limit(f.limit.value),
      Offset.option(f.offset.value).getOrElse(Offset(0))
    )
    val facts = reviewState.facts(list.articles.map(a => Slug.assume(a.slug.value)))
    list
      .modify(_.articles)
      .setTo(
        list.articles.map(a =>
          a.modify(_.review)
            .setTo(
              Some(
                ArticleReviewStatus.badge(facts.get(Slug.assume(a.slug.value)).fold(ArticleReviewStatus.Facts())(_._1))
              )
            )
        )
      )

  /** `GET /api/articles/{slug}/review`: the article's review state, its review history from the ledger (the only read
    * that decodes one), the goals linking it and the approvals about it that wait for a person.
    */
  def review(slug: Slug): Either[Api.Error, Article.ReviewView] =
    val canon = store.canonical(slug)
    store.load(canon).toRight(notFound(slug)).flatMap(_ => reviewOf(canon).left.map(Api.Error(_)))

  /** The review view of an article known to exist (its canonical slug); a ledger that no longer decodes is an error
    * rather than an empty history.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def reviewOf(slug: Slug): Either[Problems, Article.ReviewView] =
    ledgers(slug).map { ledger =>
      val (facts, updatedAt) =
        reviewState.facts(List(slug)).getOrElse(slug, (ArticleReviewStatus.Facts(), None))
      val starts =
        ledger.fold(Map.empty[Run.Id, ArticleReviewStatus.RunStart])(l =>
          reviewState.runStarts(ArticleReviewStatus.runOrder(l, Map.empty))
        )
      Article.ReviewView(
        Article.Slug(slug.value),
        ArticleReviewStatus.badge(facts),
        facts.item.flatMap(_.outcome).map(o => Article.ReviewView.Outcome(o.value)),
        updatedAt.map(u => Article.ReviewView.UpdatedAt(u.value)),
        ledger.fold(Nil)(ArticleReviewStatus.history(_, starts)),
        reviewState.goals(slug),
        facts.waiting
      )
    }

  /** One article, with its backlinks, the sources it cites, and — when it records a filter — its coverage, from which
    * every surface renders the generated Not used list and Method note.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def get(
      slug: Slug,
      brief: ArticlesControllers.GetBrief = ArticlesControllers.GetBrief(false)
  ): Either[Api.Error, Article.View] =
    val view = store.view(slug).toRight(notFound(slug))
    // `brief`: the row and its links only — the lint and the coverage are most of a read's cost
    // (a hover card waited ~20 ms on the lint alone) and a preview shows neither
    if brief.value then view
    else
      view.map(v =>
        v.modify(_.formatting)
          .setTo(formatting(v.article.body, v.article.summary))
          .modify(_.coverage)
          .setTo(store.load(Slug.assume(v.article.slug.value)).flatMap(coverageOf))
      )

  // ── coverage: the article's filter against the live corpus ───────────────────────────────────

  /** `GET /api/articles/{slug}/coverage`. An article that records no filter has no coverage: 404 saying so and how to
    * set one.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def coverage(slug: Slug): Either[Api.Error, Coverage] =
    store.load(store.canonical(slug)).toRight(notFound(slug)).flatMap { a =>
      coverageOf(a).toRight(
        error(
          Problem.Invalid.Broken(Problem.Invalid.Rule.FilterRecorded, Vector(Problem.Field(s"article '${a.slug}'"))),
          Write.CodeNotFound
        )
      )
    }

  /** The coverage of one stored article, `None` when it records no filter: the filter's source set (at most
    * `limits.coverage-sources` read; the counts are exact), which of them the body cites (its `[[links]]` resolved as
    * the page resolves them, a hand-written Not used section excluded), the disagreement edges touching the cited
    * sources, and the writer's further searches — handed to the pure [[ArticleCoverage.compute]].
    */
  @tolerated(LintRule.Copy, "pending G19 (lenses instead of .copy)")
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def coverageOf(a: Article): Option[Coverage] =
    a.filter.map { af =>
      val f = af.toFilter
      val scope = scopeOf(f)
      val (total, hits) = coverageReads.sourceSet(f, scope, ArticleRepo.Limit(cfg().limits.coverageSources.value))
      val links = bodyLinks(a.body)
      val cited = citedOf(a.body, links)
      val inFilter = coverageReads.members(f, scope, cited)._2
      val edges = coverageReads.edgesTouching(cited, disagreementKinds)
      val searches = af.extraQueries.map { q =>
        val (n, in) = coverageReads.members(Filter(q = Some(Filter.Q(q.value))), PlaceScope.empty, cited)
        CoverageSearch(CoverageSearch.Query(q.value), CoverageSearch.Total(n.value), cited.filter(in).map(apiCitekey))
      }
      Article.Coverage
        .compute(
          Article.Coverage.ComputeSlug(a.slug.value),
          af,
          Article.Coverage.ComputeTotal(total.value),
          hits,
          inFilter.map(apiCitekey),
          cited.map(apiCitekey),
          a.reasons.map((k, v) => k -> v),
          edges,
          searches
        )
        .copy(
          eventLeads = articleLeads(
            a.body,
            (cited ++ hits.map(h => Citekey.assume(h.citekey.value))).distinct,
            f.period
          ),
          critiques = critiquesOf(a.body, links, edges, store.critiqueReasons(slugOf(a)))
        )
    }

  /** The stored citekeys a body cites: its `[[links]]` outside a hand-written Not used section, resolved as the page
    * resolves them (an alias or a unique short form names its source).
    */
  def citedSources(body: Article.Body): List[Citekey] = citedOf(body, bodyLinks(body))

  /** How the links of a body (outside a hand-written Not used section) resolve. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def bodyLinks(body: Article.Body): WikiLinks.Occurrence.Target => Option[LinkTarget] =
    pageLinks(Article.Links.targets(WikiLinks.Body(Article.Coverage.withoutNotUsed(body).value)))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def citedOf(body: Article.Body, links: WikiLinks.Occurrence.Target => Option[LinkTarget]): List[Citekey] =
    Article.Links
      .targets(WikiLinks.Body(Article.Coverage.withoutNotUsed(body).value))
      .flatMap(t => links(t).collect { case r: Source.Ref => Citekey.assume(r.citekey.value) })
      .distinct

  /** THE CRITIQUE CHECKLIST of an article: every challenges/refutes/updates/superseded_by edge with an end cited by
    * `body`, addressed (both cited in one paragraph), reasoned (a stored reason) or open — whether or not the article
    * records a filter. `body` is the article's own unless a caller reviews a revision held in memory; `extra` reasons
    * count as stored (a dry run's, which were never written).
    */
  def critiques(slug: Slug, body: Article.Body, extra: List[CritiqueReason] = Nil): List[CritiquePair] =
    val links = bodyLinks(body)
    val cited = citedOf(body, links)
    val edges = coverageReads.edgesTouching(cited, disagreementKinds)
    val stored = store.critiqueReasons(store.canonical(slug))
    critiquesOf(body, links, edges, extra ++ stored.filterNot(s => extra.exists(_.ref.same(s.ref))))

  /** THE PAIRING RULE of a body (lint `article-critique-pairing`): each paragraph that cites one side of a disagreement
    * edge and names the other side's author without citing it, and how its links resolve. Read only.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  def pairing(
      body: Article.Body
  ): (List[CritiquePairing.Hit], CritiquePairing.Mention.Key => Option[com.histmersion.api.library.Citekey]) =
    val links = bodyLinks(body)
    val cited = citedOf(body, links)
    val edges = coverageReads.edgesTouching(cited, disagreementKinds)
    val works = sourceStore
      .refs(edges.flatMap(e => List(e.from, e.to)).distinct.map(ck => Citekey.assume(ck.value)))
      .map(r => r.citekey -> CritiquePairing.Work(r.citekey, CritiquePairing.names(r.citekey, r.authors), r.year))
      .toMap
    val resolve: CritiquePairing.Mention.Key => Option[com.histmersion.api.library.Citekey] =
      t => links(t).collect { case r: Source.Ref => r.citekey }
    (CritiquePairing.hits(body, resolve, edges, works.get), resolve)

  /** [[pairing]]'s mechanical repair: the body with each named partner's citekey appended, and the hits applied. */
  def repairPairing(body: Article.Body): (Article.Body, List[CritiquePairing.Hit]) =
    CritiquePairing.repair(body, pairing(body)._1)

  /** What filter `af` returns from the live corpus: its source count, which of `citekeys` it returns, and up to `cap`
    * of its sources (by citekey) — what the managers' code checks measure (infer-filter, propose-articles).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def filterReach(
      af: Article.Filter,
      citekeys: List[Citekey],
      cap: ArticlesControllers.FilterReachCap
  ): (ReviewManagerPolicy.FilterHits, Set[Citekey], List[Citekey]) =
    val f = af.toFilter
    val scope = scopeOf(f)
    val (total, rows) = coverageReads.sourceSet(f, scope, ArticleRepo.Limit(math.max(0, cap.value)))
    val in = if citekeys.isEmpty then Set.empty[Citekey] else coverageReads.members(f, scope, citekeys)._2
    (ReviewManagerPolicy.FilterHits(total.value), in, rows.map(r => Citekey.assume(r.citekey.value)))

  /** The coverage filter an article records, as stored (no alias followed) — the review manager kind's. */
  def recordedFilter(slug: Slug): Option[Article.Filter] = store.load(slug).flatMap(_.filter)

  /** How many sources the filter of the article `slug` names returns now (none: no article, or no filter). */
  def filterTotal(slug: Slug): Option[ReviewManagerPolicy.FilterHits] =
    store
      .load(store.canonical(slug))
      .flatMap(_.filter)
      .map(af => filterReach(af, Nil, ArticlesControllers.FilterReachCap(0))._1)

  /** How many sources an article's filter returns (of the first `limits.coverage-sources`) that its body neither cites
    * nor gives a reason for — what an extension would add (the autopilot's article-extend threshold). None: no such
    * article, or it records no filter.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def newFilterSources(slug: Slug): Option[ArticlesControllers.NewSourceCount] =
    store
      .load(store.canonical(slug))
      .flatMap(a =>
        a.filter.map { af =>
          val f = af.toFilter
          val (_, hits) = coverageReads.sourceSet(f, scopeOf(f), ArticleRepo.Limit(cfg().limits.coverageSources.value))
          val cited = citedOf(a.body, bodyLinks(a.body)).toSet
          ArticlesControllers.NewSourceCount(
            hits
              .map(h => Citekey.assume(h.citekey.value))
              .count(ck => !cited(ck) && !a.reasons.keys.exists(_.value == ck.value))
          )
        }
      )

  /** How many pairs of the article's critique checklist are open now and bear on a work it cites (None: no such
    * article). An open pair on a work the article does not cite is background: no run owes it anything.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def openCritiques(slug: Slug): Option[ReviewManagerPolicy.OpenPairs] =
    store
      .load(store.canonical(slug))
      .map(a =>
        ReviewManagerPolicy.OpenPairs(
          Critiques.toRule(critiques(slugOf(a), a.body)).size
        )
      )

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  private def critiquesOf(
      body: Article.Body,
      links: WikiLinks.Occurrence.Target => Option[LinkTarget],
      edges: List[Coverage.Edge],
      reasons: List[CritiqueReason]
  ): List[CritiquePair] =
    if edges.isEmpty then Nil
    else
      val refs = sourceStore
        .refs(edges.flatMap(e => List(e.from, e.to)).distinct.map(ck => Citekey.assume(ck.value)))
        .map(r => r.citekey -> (r.title, r.year))
        .toMap
      Critiques.build(
        body,
        t => links(t).collect { case r: Source.Ref => r.citekey },
        edges,
        reasons,
        refs.get
      )

  /** Reasons are stored under the citekey a source has NOW (an old one resolves). */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G47Fqn, "pending G47 (no FQN noise)")
  private def canonicalReasons(rs: List[Coverage.Reason]): List[Coverage.Reason] =
    rs.map { r =>
      val cleaned = r.citekey.value.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase
      r.modify(_.citekey)
        .setTo(
          com.histmersion.api.library.Citekey(
            Citekey.option(cleaned).fold(cleaned)(ck => sources.canonical(ck).value)
          )
        )
    }

  // ── links ──────────────────────────────────────────────────────────────────────────────────

  /** How a page's `[[wikilinks]]` resolve: the citekey aliases its targets expand to (exact first, else the unique
    * `<target>_%` prefix — the wiki writes `[[knapp2016]]` for `knapp2016_seven`), the stored sources behind them, and
    * which of the rest are compiled articles — three queries for the whole page, whatever its number of links. A source
    * wins over an article, as in the pure [[ArticleLinks.hrefFor]]; anything unresolvable is `None` and renders as text
    * rather than a link to a 404.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def pageLinks(targets: List[WikiLinks.Occurrence.Target]): WikiLinks.Occurrence.Target => Option[LinkTarget] =
    val aliases = sourceStore.resolveLinks(targets)
    val sourceRefs = sourceStore.refs(aliases.values.toList).map(r => (r.citekey.value: String) -> r).toMap
    val articles = store.refs(targets.flatMap(t => Slug.option(t.value))).map(a => (a.slug.value: String) -> a).toMap
    t => aliases.get(t).flatMap(ck => sourceRefs.get(ck.value)).orElse(articles.get(t.value))

  /** `GET /api/sources/{ck}/articles` — the compiled articles that cite this source. 404 when the citekey itself is
    * unknown, so "no articles yet" and "no such source" are different answers.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def sourceArticles(citekey: Citekey): Either[Api.Error, List[Backlink]] =
    sources
      .get(citekey)
      .left
      .map(Api.Error(_))
      .map(s => store.backlinksForSource(Citekey.assume(s.source.citekey.value)))

  /** "Mentioned in articles", the part a source's default and full views carry. */
  def mentionedIn(citekey: Citekey): String =
    BacklinksMarkdown.sourceArticles(store.backlinksForSource(citekey).toVector)

  /** The article an identifier names, as `GET /api/resolve/{id}` shows it. */
  def resolved(id: Slug): Option[ResolvedArticle] =
    get(id, brief = ArticlesControllers.GetBrief(true)).toOption.map(a =>
      ResolvedArticle(a.article.slug, a.article.title)
    )

  // ── writes ─────────────────────────────────────────────────────────────────────────────────

  /** Create or REPLACE one article. The response carries its formatting problems; `strict` refuses the write when one
    * of them is an error.
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def put(
      slug: Slug,
      in: Article.Put,
      strict: domain.research.Article.StrictMode = domain.research.Article.StrictMode(false)
  )(using Creator.New): Either[Api.Error, Article.View] =
    maintenance.guard(WriteGate.OperationName("put_article")):
      // an OLD slug follows the alias: the put replaces the renamed article
      Articles
        .applyPut(
          Article.Slug(currentSlug(slug).value),
          in.modify(_.reasons).setTo(canonicalReasons(in.reasons))
        )
        .left
        .map(m => error(m, Write.CodeInvalid))
        .flatMap(a => strictGate(strict, formatting(a.body, a.summary)).map(_ => a))
        .flatMap { a =>
          val _ = store.put(a, Caller.Local); get(slugOf(a))
        }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). With `strict`, the merged
    * result is checked first — an append is judged in the article it lands in.
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  def patch(
      slug: Slug,
      p: Article.Patch,
      strict: domain.research.Article.StrictMode = domain.research.Article.StrictMode(false)
  )(using Creator.New): Either[Api.Error, Article.View] =
    maintenance.guard(WriteGate.OperationName("patch_article")):
      Right(currentSlug(slug))
        .flatMap { s =>
          val gate =
            if !strict.value then Right(())
            else
              store
                .load(s)
                .fold(Right(()))(cur =>
                  Articles
                    .applyPatch(cur, p)
                    .fold(
                      _ => Right(()),
                      next =>
                        strictGate(
                          domain.research.Article.StrictMode(true),
                          formatting(next.body, next.summary)
                        )
                    )
                )
          gate.flatMap(_ => store.patch(s, p.modify(_.reasons).setTo(canonicalReasons(p.reasons)), Caller.Local))
        }
        .flatMap(_ => get(slug))

  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def delete(slug: Slug): Either[Api.Error, Article.Deleted] =
    maintenance.guard(WriteGate.OperationName("delete_article")):
      val now = currentSlug(slug)
      if store.delete(now, Caller.Local) then
        Right(Article.Deleted(Article.Slug(now.value), Article.Deleted.Value.Deleted))
      else Left(notFound(slug))

  /** `POST /api/articles/{slug}/rename` — a new slug; the old one keeps resolving and every `[[old]]` in other articles
    * is rewritten (each a new version).
    */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  def rename(slug: Slug, req: LabelRename)(using Creator.New): Either[Api.Error, LabelRenamed] =
    maintenance.guard(WriteGate.OperationName("rename_article")):
      val from = currentSlug(slug)
      for
        label <- LabelRenames.validate(
          Bundle.Kind.Article,
          LabelRenames.Label(from.value),
          LabelRenames.Label(req.to.value)
        )
        to <- Slug.parse(label.value).left.map(p => Api.Error(p, Some(Api.Error.Code(Write.CodeInvalid))))
        rewritten <- store.rename(from, to, Caller.Local)
      yield LabelRenamed(
        Bundle.Kind.Article,
        LabelRenamed.From(from.value),
        LabelRenamed.To(to.value),
        rewritten.map(s => LabelRenamed.Rewritten(s.value))
      )

  // ── the mirror, the versions, the slugs, a note imported ────────────────────────────────────

  /** The article as it would live on disk — `wiki/<kind>/<slug>.md`, with its generated coverage sections after the
    * body (between markers an import drops) and its filter and reasons in the frontmatter.
    */
  def exportNote(slug: Slug): Either[Api.Error, domain.research.Article.Note] = note(slug)

  /** The note of the article an old slug or its own names, as the mirror writes it. */
  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def articleNote(slug: Slug): Either[Api.Error, domain.research.Article.Note] =
    store
      .load(currentSlug(slug))
      .map(a =>
        domain.research.Article
          .Note(ArticleNote.render(a, coverageOf(a).map(c => Article.Coverage.exported(c).value)))
      )
      .toRight(notFound(slug))

  /** The edit history of one article. 404 when the slug itself is unknown (so "no versions yet" and "no such article"
    * are different answers).
    */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def versions(slug: Slug): Either[Api.Error, List[Article.Version]] =
    get(slug).map(v => store.versions(Slug.assume(v.article.slug.value)))

  /** One historical version's full body. 404 when the slug or the version id is unknown. */
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  def version(slug: Slug, versionId: ArticleRepo.VersionId): Either[Api.Error, Article.VersionFull] =
    get(slug).flatMap(v =>
      store
        .version(Slug.assume(v.article.slug.value), versionId)
        .toRight(
          error(
            Problem.NotFound.Entity(Problem.TypeName.of(ArticleRepo.VersionId), Problem.Raw.of(versionId)),
            Write.CodeNotFound
          )
        )
    )

  def slugs: List[Slug] = store.slugs

  /** Import ONE article note from raw text (the host-side path of `histmersion seed`). Applies the same "stored row is
    * newer than the file" guard as the source seed whenever the client sends the file's mtime, so a reseed cannot wipe
    * an agent's compiled prose.
    */
  def importNote(req: Article.Import)(using Creator.New): Either[Api.Error, SeedResult] =
    maintenance.guard(WriteGate.OperationName("import_article"))(importNow(req))

  @tolerated(LintRule.G44, "pending G44 (raw value only in the newtype's companion or at a lifted boundary)")
  @tolerated(
    LintRule.G44Conversion,
    "pending G44 (a controller hands the wire value to the domain's parse, not .value)"
  )
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  @tolerated(LintRule.G60FromString, "pending G60 (problems built from cases, not text)")
  private def importNow(req: Article.Import)(using WritePermit)(using Creator.New): Either[Api.Error, SeedResult] =
    val fallbackSlug = Article.Slug(req.slug.map(_.value.trim).filter(_.nonEmpty).getOrElse(""))
    val fallbackKind = Article.Kind.Wire(req.kind.map(_.value.trim).filter(_.nonEmpty).getOrElse(Articles.Kinds.head))
    ArticleNote.parse(req.text.value, fallbackSlug, fallbackKind) match
      case None         => Left(Api.Error(Problem.Invalid.Broken.here(Problem.Invalid.Rule.Frontmatter)))
      case Some(parsed) =>
        Slug
          .parse(parsed.slug.value)
          .left
          .map(m => error(m, Write.CodeInvalid))
          .flatMap { s =>
            // as for a source note: a note filed under an OLD slug is a stale mirror file — it would
            // otherwise create a second article that shadows the alias
            Option(store.canonical(s))
              .filter(_ != s)
              .fold(Right(s))(now =>
                Left(
                  error(
                    Problem
                      .Unclassified(Legacy.Text(s"'$s' was renamed to '$now' — import the note under '$now' instead")),
                    Write.CodeInvalid
                  )
                )
              )
          }
          .flatMap { s =>
            if !req.force.force && req.mtime.exists(m => store.updatedAtMillis.get(s).exists(_.value > m.value)) then
              Right(
                SeedResult(SeedResult.Imported(0), Nil, SeedResult.EdgesResolved(0), List(SeedResult.Skipped(s.value)))
              )
            else
              Articles
                .kindOf(parsed.kind)
                .left
                .map(m => error(m, Write.CodeInvalid))
                .flatMap(k =>
                  strictGate(
                    domain.research.Article.StrictMode(req.strict.strict),
                    formatting(parsed.body, parsed.summary)
                  ).map(_ => k)
                )
                .map { k =>
                  val _ =
                    store.put(parsed.modify(_.slug).setTo(Article.Slug(s.value)).modify(_.kind).setTo(k), Caller.Local)
                  SeedResult(SeedResult.Imported(1), Nil, SeedResult.EdgesResolved(0), Nil)
                }
          }

object ArticlesControllers:
  type NewSourceCount = NewSourceCount.T
  object NewSourceCount extends Newtype[Int, Pure]
  type GetBrief = GetBrief.T
  object GetBrief extends Newtype[Boolean, Pure]
  type FilterReachCap = FilterReachCap.T
  object FilterReachCap extends Newtype[Int, Pure]
