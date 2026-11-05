package com.histmersion.domain.research.articles

import hearth.kindlings.optics.syntax.*

import com.histmersion.domain.research.Slug

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
    ledgers: ArticlesControllers.LedgersArg1 => Either[ArticlesControllers.LedgersResultPart1, Option[ReviewLedger.Ledger]],
    articleLeads: (ArticlesControllers.ArticleLeadsArg1, List[ArticlesControllers.ArticleLeadsArg2], Option[Period]) => List[ArticleEventLead],
    note: ArticlesControllers.NoteArg1 => Either[Api.Error, ArticlesControllers.NoteResultPart2],
    current: (AliasKind, ArticlesControllers.CurrentArg2) => ArticlesControllers.CurrentResult,
    cfg: () => AppConfig,
    maintenance: WriteGate
):

  /** Slugs are lower-case by construction; be forgiving about what a caller sends. */
  private def norm(slug: com.histmersion.domain.research.articles.ArticlesControllers.NormSlug): com.histmersion.domain.research.articles.ArticlesControllers.NormResult = slug.trim.toLowerCase

  private def resolve(f: Filter): Filter = filters.page(f)
  private def scopeOf(f: Filter): PlaceScope = filters.scope(f)

  // ── formatting: what in the agent-written Markdown will not display as meant ─────────────────

  /** The formatting problems of an article's body and summary, its links resolved as the page resolves them. */
  def formatting(body: com.histmersion.domain.research.articles.ArticlesControllers.FormattingBody, summary: Option[com.histmersion.domain.research.articles.ArticlesControllers.FormattingSummary]): List[FormatIssue] =
    MarkdownLint.ofArticle(body, summary, pageLinks(MarkdownLint.lookups(body :: summary.toList)))

  /** `strict`: refuse a write whose text has a formatting ERROR; otherwise the problems ride along in the response, and
    * the write goes through.
    */
  private def strictGate(strict: com.histmersion.domain.research.articles.ArticlesControllers.StrictGateStrict, issues: => List[FormatIssue]): Either[Api.Error, Unit] =
    if !strict then Right(())
    else MarkdownLint.refusal(issues).fold(Right(()))(m => Left(Api.Error(m, Some(Write.CodeInvalid))))

  /** `GET /api/articles/{slug}/lint`. */
  def lint(slug: com.histmersion.domain.research.articles.ArticlesControllers.LintSlug): Either[Api.Error, FormatReport] =
    get(slug).map(v => FormatReport(v.article.slug, v.formatting))

  /** `POST /api/articles/{slug}/lint/fix`: the safe repairs of the body and summary, written guarded by the article's
    * token, then the lint again.
    */
  def fix(slug: com.histmersion.domain.research.articles.ArticlesControllers.FixSlug): Either[Api.Error, FormatReport] =
    maintenance.guard("fix_article_format"):
      store.load(store.canonical(norm(slug))).toRight(Articles.notFound(slug)).flatMap { a =>
        val (body, fb) = MarkdownLint.fix(a.body, FormatField.Body, MarkdownLint.Context(article = true))
        val (summary, fs) =
          a.summary.map(t => MarkdownLint.fix(t, FormatField.Summary)).fold((None, Nil))((t, f) => (Some(t), f))
        val done = fb.map("body: " + _) ++ fs.map("summary: " + _)
        val write =
          if done.isEmpty then Right(())
          else
            store
              .patch(
                a.slug,
                Article.Patch(
                  body = Some(body).filter(_ => fb.nonEmpty),
                  summary = summary.filter(_ => fs.nonEmpty),
                  ifUpdatedAt = a.updatedAt
                ),
                Caller.Local
              )
              .map(_ => ())
        write.flatMap(_ => lint(a.slug)).map(_.modify(_.fixed).setTo(done))
      }

  /** A whole body written by the mechanical pairing repair, guarded by the token it was computed against: whether it
    * was written.
    */
  def writeRepairedBody(slug: com.histmersion.domain.research.articles.ArticlesControllers.WriteRepairedBodySlug, body: com.histmersion.domain.research.articles.ArticlesControllers.WriteRepairedBodyBody, token: Option[com.histmersion.domain.research.articles.ArticlesControllers.WriteRepairedBodyToken]): com.histmersion.domain.research.articles.ArticlesControllers.WriteRepairedBodyResult =
    store.patch(slug, Article.Patch(body = Some(body), ifUpdatedAt = token), Caller.Local).isRight

  // ── a draft of an article, checked as review-article's fixer validation checks it ──────────────

  /** What [[ReviewArticle.checkBody]] needs from the corpus, resolved once for every link in `texts` (the body and the
    * draft's texts): `[[x]]` as a source, as anything, and the formatting ERRORS of a text (the `--strict` gate's), one
    * position-free line each. `generated`: the article records a filter, so its Not used list and Method note are
    * generated and must not be written into the body.
    */
  def bodyCorpus(texts: List[com.histmersion.domain.research.articles.ArticlesControllers.BodyCorpusTexts], generated: com.histmersion.domain.research.articles.ArticlesControllers.BodyCorpusGenerated = false): ReviewArticle.BodyCorpus =
    val links = pageLinks(MarkdownLint.lookups(texts))
    ReviewArticle.BodyCorpus(
      t => links(t).exists(_.isInstanceOf[Source.Ref]),
      t => links(t).isDefined,
      text =>
        MarkdownLint
          .ofArticle(text, None, links)
          .filter(_.severity == FormatSeverity.Error)
          .map { i =>
            s"${i.rule.wire} — ${i.message}" + (if i.excerpt.isEmpty then ""
                                                else s" · `${i.excerpt.replace("`", "'")}`")
          }
          .distinct,
      generated
    )

  /** `POST /api/articles/{slug}/check` (MCP and agent tool `check_edits`) — read only. Edits are applied to the STORED
    * body; a whole `body` is checked as it stands (the article need not exist yet: a compile writer's draft). The same
    * function the review pipeline validates its fixer with.
    */
  def check(slug: com.histmersion.domain.research.articles.ArticlesControllers.CheckSlug, in: Article.Edits.ArticleCheckRequest): Either[Api.Error, Article.Edits.ArticleCheck] =
    in.draft.left.map(m => Api.Error(m, Some(Write.CodeInvalid))).flatMap { draft =>
      val stored = store.load(store.canonical(norm(slug)))
      (draft, stored) match
        case (Article.Edits.Draft.Edits(_), None) => Left(Articles.notFound(slug))
        case _                                    =>
          val before = stored.fold("")(_.body)
          val kind = in.kind.map(_.trim).filter(_.nonEmpty).orElse(stored.map(_.kind)).getOrElse("concept")
          val texts = draft match
            case Article.Edits.Draft.Edits(es) => before :: es.flatMap(_.text)
            case Article.Edits.Draft.Body(t)   => List(before, t)
          // a new article is compiled with a filter: its coverage sections will be generated
          val generated = stored.forall(_.filter.isDefined)
          Right(
            ReviewArticle.checkBody(stored.fold(norm(slug))(_.slug), before, kind, draft, bodyCorpus(texts, generated))
          )
    }

  // ── the list, one article, its review ───────────────────────────────────────────────────────

  /** `GET /api/articles` — the compiled articles citing the filter's sources, narrowed by kind and searched with
    * `text`, each with its review badge.
    */
  def list(q: Api.ArticleQuery): Article.List =
    val f = resolve(q.filter)
    val list = search.list(f, scopeOf(f), q.kind, q.text, f.limit, f.offset)
    val facts = reviewState.facts(list.articles.map(_.slug))
    list
      .modify(_.articles)
      .setTo(
        list.articles.map(a =>
          a.modify(_.review)
            .setTo(Some(ArticleReviewStatus.badge(facts.get(a.slug).fold(ArticleReviewStatus.Facts())(_._1))))
        )
      )

  /** `GET /api/articles/{slug}/review`: the article's review state, its review history from the ledger (the only read
    * that decodes one), the goals linking it and the approvals about it that wait for a person.
    */
  def review(slug: com.histmersion.domain.research.articles.ArticlesControllers.ReviewSlug): Either[Api.Error, Article.ReviewView] =
    val canon = store.canonical(norm(slug))
    store.load(canon).toRight(Articles.notFound(slug)).flatMap(_ => reviewOf(canon))

  /** The review view of an article known to exist (its canonical slug); a ledger that no longer decodes is an error
    * rather than an empty history.
    */
  def reviewOf(slug: com.histmersion.domain.research.articles.ArticlesControllers.ReviewOfSlug): Either[Api.Error, Article.ReviewView] =
    ledgers(slug).left.map(e => Api.Error(e)).map { ledger =>
      val (facts, updatedAt) =
        reviewState.facts(List(slug)).getOrElse(slug, (ArticleReviewStatus.Facts(), None))
      val starts =
        ledger.fold(Map.empty[String, String])(l => reviewState.runStarts(ArticleReviewStatus.runOrder(l, Map.empty)))
      Article.ReviewView(
        slug,
        ArticleReviewStatus.badge(facts),
        facts.item.flatMap(_.outcome),
        updatedAt,
        ledger.fold(Nil)(ArticleReviewStatus.history(_, starts)),
        reviewState.goals(slug),
        facts.waiting
      )
    }

  /** One article, with its backlinks, the sources it cites, and — when it records a filter — its coverage, from which
    * every surface renders the generated Not used list and Method note.
    */
  def get(slug: com.histmersion.domain.research.articles.ArticlesControllers.GetSlug, brief: com.histmersion.domain.research.articles.ArticlesControllers.GetBrief = false): Either[Api.Error, Article.View] =
    val view = store.view(norm(slug)).toRight(Articles.notFound(slug))
    // `brief`: the row and its links only — the lint and the coverage are most of a read's cost
    // (a hover card waited ~20 ms on the lint alone) and a preview shows neither
    if brief then view
    else
      view.map(v =>
        v.modify(
          _.formatting).setTo(formatting(v.article.body, v.article.summary)).modify(_.coverage).setTo(store.load(v.article.slug).flatMap(coverageOf)
        )
      )

  // ── coverage: the article's filter against the live corpus ───────────────────────────────────

  /** `GET /api/articles/{slug}/coverage`. An article that records no filter has no coverage: 404 saying so and how to
    * set one.
    */
  def coverage(slug: com.histmersion.domain.research.articles.ArticlesControllers.CoverageSlug): Either[Api.Error, Coverage] =
    store.load(store.canonical(norm(slug))).toRight(Articles.notFound(slug)).flatMap { a =>
      coverageOf(a).toRight(
        Api.Error(
          s"article '${a.slug}' records no filter, so it has no generated coverage — " +
            s"set the filter it is compiled from: histmersion article patch ${a.slug} --filter-query … (or --filter-topics/--filter-tags), " +
            "or patch_article with `filter`",
          Some(Write.CodeNotFound)
        )
      )
    }

  /** The coverage of one stored article, `None` when it records no filter: the filter's source set (at most
    * `limits.coverage-sources` read; the counts are exact), which of them the body cites (its `[[links]]` resolved as
    * the page resolves them, a hand-written Not used section excluded), the disagreement edges touching the cited
    * sources, and the writer's further searches — handed to the pure [[ArticleCoverage.compute]].
    */
  def coverageOf(a: Article): Option[Coverage] =
    a.filter.map { af =>
      val f = af.toFilter
      val scope = scopeOf(f)
      val (total, hits) = coverageReads.sourceSet(f, scope, cfg().limits.coverageSources)
      val links = bodyLinks(a.body)
      val cited = citedOf(a.body, links)
      val inFilter = coverageReads.members(f, scope, cited)._2
      val edges = coverageReads.edgesTouching(cited, Article.Coverage.DisagreementKinds)
      val searches = af.extraQueries.map { q =>
        val (n, in) = coverageReads.members(Filter(q = Some(q)), PlaceScope.empty, cited)
        CoverageSearch(q, n, cited.filter(in))
      }
      Article.Coverage
        .compute(a.slug, af, total, hits, inFilter, cited, a.reasons, edges, searches)
        .copy(
          eventLeads = articleLeads(a.body, (cited ++ hits.map(_.citekey)).distinct, f.period),
          critiques = critiquesOf(a.body, links, edges, store.critiqueReasons(a.slug))
        )
    }

  /** The stored citekeys a body cites: its `[[links]]` outside a hand-written Not used section, resolved as the page
    * resolves them (an alias or a unique short form names its source).
    */
  def citedSources(body: com.histmersion.domain.research.articles.ArticlesControllers.CitedSourcesBody): List[com.histmersion.domain.research.articles.ArticlesControllers.CitedSourcesResult] = citedOf(body, bodyLinks(body))

  /** How the links of a body (outside a hand-written Not used section) resolve. */
  private def bodyLinks(body: com.histmersion.domain.research.articles.ArticlesControllers.BodyLinksBody): com.histmersion.domain.research.articles.ArticlesControllers.BodyLinksResultArg1 => Option[LinkTarget] =
    pageLinks(Article.Links.targets(Article.Coverage.withoutNotUsed(body)))

  private def citedOf(body: com.histmersion.domain.research.articles.ArticlesControllers.CitedOfBody, links: com.histmersion.domain.research.articles.ArticlesControllers.CitedOfLinksArg1 => Option[LinkTarget]): List[com.histmersion.domain.research.articles.ArticlesControllers.CitedOfResult] =
    Article.Links
      .targets(Article.Coverage.withoutNotUsed(body))
      .flatMap(t => links(t).collect { case r: Source.Ref => r.citekey })
      .distinct

  /** THE CRITIQUE CHECKLIST of an article: every challenges/refutes/updates/superseded_by edge with an end cited by
    * `body`, addressed (both cited in one paragraph), reasoned (a stored reason) or open — whether or not the article
    * records a filter. `body` is the article's own unless a caller reviews a revision held in memory; `extra` reasons
    * count as stored (a dry run's, which were never written).
    */
  def critiques(slug: com.histmersion.domain.research.articles.ArticlesControllers.CritiquesSlug, body: com.histmersion.domain.research.articles.ArticlesControllers.CritiquesBody, extra: List[CritiqueReason] = Nil): List[CritiquePair] =
    val links = bodyLinks(body)
    val cited = citedOf(body, links)
    val edges = coverageReads.edgesTouching(cited, Article.Coverage.DisagreementKinds)
    val stored = store.critiqueReasons(store.canonical(norm(slug)))
    critiquesOf(body, links, edges, extra ++ stored.filterNot(s => extra.exists(_.ref.same(s.ref))))

  /** THE PAIRING RULE of a body (lint `article-critique-pairing`): each paragraph that cites one side of a disagreement
    * edge and names the other side's author without citing it, and how its links resolve. Read only.
    */
  def pairing(body: com.histmersion.domain.research.articles.ArticlesControllers.PairingBody): (List[CritiquePairing.Hit], com.histmersion.domain.research.articles.ArticlesControllers.PairingResultPart2Arg1 => Option[com.histmersion.domain.research.articles.ArticlesControllers.PairingResultPart2Result]) =
    val links = bodyLinks(body)
    val cited = citedOf(body, links)
    val edges = coverageReads.edgesTouching(cited, Article.Coverage.DisagreementKinds)
    val works = sourceStore
      .refs(edges.flatMap(e => List(e.from, e.to)).distinct)
      .map(r => r.citekey -> CritiquePairing.Work(r.citekey, CritiquePairing.names(r.citekey, r.authors), r.year))
      .toMap
    val resolve: com.histmersion.domain.research.articles.ArticlesControllers.PairingResolveArg1 => Option[com.histmersion.domain.research.articles.ArticlesControllers.PairingResolveResult] = t => links(t).collect { case r: Source.Ref => r.citekey }
    (CritiquePairing.hits(body, resolve, edges, works.get), resolve)

  /** [[pairing]]'s mechanical repair: the body with each named partner's citekey appended, and the hits applied. */
  def repairPairing(body: com.histmersion.domain.research.articles.ArticlesControllers.RepairPairingBody): (com.histmersion.domain.research.articles.ArticlesControllers.RepairPairingResultPart1, List[CritiquePairing.Hit]) =
    CritiquePairing.repair(body, pairing(body)._1)

  /** What filter `af` returns from the live corpus: its source count, which of `citekeys` it returns, and up to `cap`
    * of its sources (by citekey) — what the managers' code checks measure (infer-filter, propose-articles).
    */
  def filterReach(af: Article.Filter, citekeys: List[com.histmersion.domain.research.articles.ArticlesControllers.FilterReachCitekeys], cap: com.histmersion.domain.research.articles.ArticlesControllers.FilterReachCap): (com.histmersion.domain.research.articles.ArticlesControllers.FilterReachResultPart1, Set[com.histmersion.domain.research.articles.ArticlesControllers.FilterReachResultPart2], List[com.histmersion.domain.research.articles.ArticlesControllers.FilterReachResultPart3]) =
    val f = af.toFilter
    val scope = scopeOf(f)
    val (total, rows) = coverageReads.sourceSet(f, scope, math.max(0, cap))
    val in = if citekeys.isEmpty then Set.empty[String] else coverageReads.members(f, scope, citekeys)._2
    (total, in, rows.map(_.citekey))

  /** The coverage filter an article records, as stored (no alias followed) — the review manager kind's. */
  def recordedFilter(slug: com.histmersion.domain.research.articles.ArticlesControllers.RecordedFilterSlug): Option[Article.Filter] = store.load(slug).flatMap(_.filter)

  /** How many sources the filter of the article `slug` names returns now (none: no article, or no filter). */
  def filterTotal(slug: com.histmersion.domain.research.articles.ArticlesControllers.FilterTotalSlug): Option[com.histmersion.domain.research.articles.ArticlesControllers.FilterTotalResult] =
    store.load(store.canonical(norm(slug))).flatMap(_.filter).map(af => filterReach(af, Nil, 0)._1)

  /** How many sources an article's filter returns (of the first `limits.coverage-sources`) that its body neither cites
    * nor gives a reason for — what an extension would add (the autopilot's article-extend threshold). None: no such
    * article, or it records no filter.
    */
  def newFilterSources(slug: com.histmersion.domain.research.articles.ArticlesControllers.NewFilterSourcesSlug): Option[com.histmersion.domain.research.articles.ArticlesControllers.NewFilterSourcesResult] =
    store
      .load(store.canonical(norm(slug)))
      .flatMap(a =>
        a.filter.map { af =>
          val f = af.toFilter
          val (_, hits) = coverageReads.sourceSet(f, scopeOf(f), cfg().limits.coverageSources)
          val cited = citedOf(a.body, bodyLinks(a.body)).toSet
          hits.map(_.citekey).count(ck => !cited(ck) && !a.reasons.contains(ck))
        }
      )

  /** How many pairs of the article's critique checklist are open now and bear on a work it cites (None: no such
    * article). An open pair on a work the article does not cite is background: no run owes it anything.
    */
  def openCritiques(slug: com.histmersion.domain.research.articles.ArticlesControllers.OpenCritiquesSlug): Option[com.histmersion.domain.research.articles.ArticlesControllers.OpenCritiquesResult] =
    store
      .load(store.canonical(norm(slug)))
      .map(a => Critiques.toRule(critiques(a.slug, a.body)).size)

  private def critiquesOf(
      body: com.histmersion.domain.research.articles.ArticlesControllers.CritiquesOfBody,
      links: com.histmersion.domain.research.articles.ArticlesControllers.CritiquesOfLinksArg1 => Option[LinkTarget],
      edges: List[Coverage.Edge],
      reasons: List[CritiqueReason]
  ): List[CritiquePair] =
    if edges.isEmpty then Nil
    else
      val refs = sourceStore
        .refs(edges.flatMap(e => List(e.from, e.to)).distinct)
        .map(r => r.citekey -> (r.title, r.year))
        .toMap
      Critiques.build(body, t => links(t).collect { case r: Source.Ref => r.citekey }, edges, reasons, refs.get)

  /** Reasons are stored under the citekey a source has NOW (an old one resolves). */
  private def canonicalReasons(rs: List[Coverage.Reason]): List[Coverage.Reason] =
    rs.map(r =>
      r.modify(_.citekey)
        .setTo(
          sources
            .canonical(Option(r.citekey).map(_.trim.stripPrefix("[[").stripSuffix("]]").trim.toLowerCase).getOrElse(""))
        )
    )

  // ── links ──────────────────────────────────────────────────────────────────────────────────

  /** How a page's `[[wikilinks]]` resolve: the citekey aliases its targets expand to (exact first, else the unique
    * `<target>_%` prefix — the wiki writes `[[knapp2016]]` for `knapp2016_seven`), the stored sources behind them, and
    * which of the rest are compiled articles — three queries for the whole page, whatever its number of links. A source
    * wins over an article, as in the pure [[ArticleLinks.hrefFor]]; anything unresolvable is `None` and renders as text
    * rather than a link to a 404.
    */
  def pageLinks(targets: List[com.histmersion.domain.research.articles.ArticlesControllers.PageLinksTargets]): com.histmersion.domain.research.articles.ArticlesControllers.PageLinksResultArg1 => Option[LinkTarget] =
    val aliases = sourceStore.resolveLinks(targets)
    val sourceRefs = sourceStore.refs(aliases.values.toList).map(r => r.citekey -> r).toMap
    val articles = store.refs(targets).map(a => a.slug -> a).toMap
    t => aliases.get(t).flatMap(sourceRefs.get).orElse(articles.get(t))

  /** `GET /api/sources/{ck}/articles` — the compiled articles that cite this source. 404 when the citekey itself is
    * unknown, so "no articles yet" and "no such source" are different answers.
    */
  def sourceArticles(citekey: com.histmersion.domain.library.Citekey): Either[Api.Error, List[Backlink]] =
    sources.get(citekey).map(s => store.backlinksForSource(s.source.citekey))

  /** "Mentioned in articles", the part a source's default and full views carry. */
  def mentionedIn(citekey: com.histmersion.domain.library.Citekey): com.histmersion.domain.research.articles.ArticlesControllers.MentionedInResult =
    BacklinksMarkdown.sourceArticles(store.backlinksForSource(citekey).toVector)

  /** The article an identifier names, as `GET /api/resolve/{id}` shows it. */
  def resolved(id: com.histmersion.domain.research.articles.ArticlesControllers.ResolvedId): Option[ResolvedArticle] =
    get(id, brief = true).toOption.map(a => ResolvedArticle(a.article.slug, a.article.title))

  // ── writes ─────────────────────────────────────────────────────────────────────────────────

  /** Create or REPLACE one article. The response carries its formatting problems; `strict` refuses the write when one
    * of them is an error.
    */
  def put(slug: com.histmersion.domain.research.articles.ArticlesControllers.PutSlug, in: Article.Put, strict: com.histmersion.domain.research.articles.ArticlesControllers.PutStrict = false): Either[Api.Error, Article.View] =
    maintenance.guard("put_article"):
      // an OLD slug follows the alias: the put replaces the renamed article
      Slug
        .parse(slug)
        .flatMap(s =>
          Articles
            .applyPut(
              current(AliasKinds.ArticleRecords, s.value),
              in.modify(_.reasons).setTo(canonicalReasons(in.reasons))
            )
        )
        .left
        .map(m => Api.Error(m, Some(Write.CodeInvalid)))
        .flatMap(a => strictGate(strict, formatting(a.body, a.summary)).map(_ => a))
        .flatMap { a =>
          val _ = store.put(a, Caller.Local); get(a.slug)
        }

  /** Merge only the parts present in the patch (read + merge + write in one transaction). With `strict`, the merged
    * result is checked first — an append is judged in the article it lands in.
    */
  def patch(slug: com.histmersion.domain.research.articles.ArticlesControllers.PatchSlug, p: Article.Patch, strict: com.histmersion.domain.research.articles.ArticlesControllers.PatchStrict = false): Either[Api.Error, Article.View] =
    maintenance.guard("patch_article"):
      Slug
        .parse(slug)
        .map(_.value)
        .left
        .map(m => Api.Error(m, Some(Write.CodeInvalid)))
        .map(current(AliasKinds.ArticleRecords, _))
        .flatMap { s =>
          val gate =
            if !strict then Right(())
            else
              store
                .load(s)
                .fold(Right(()))(cur =>
                  Articles
                    .applyPatch(cur, p)
                    .fold(_ => Right(()), next => strictGate(true, formatting(next.body, next.summary)))
                )
          gate.flatMap(_ => store.patch(s, p.modify(_.reasons).setTo(canonicalReasons(p.reasons)), Caller.Local))
        }
        .flatMap(_ => get(slug))

  def delete(slug: com.histmersion.domain.research.articles.ArticlesControllers.DeleteSlug): Either[Api.Error, Article.Deleted] =
    maintenance.guard("delete_article"):
      val now = current(AliasKinds.ArticleRecords, norm(slug))
      if store.delete(now, Caller.Local) then Right(Article.Deleted(now, true))
      else Left(Articles.notFound(slug))

  /** `POST /api/articles/{slug}/rename` — a new slug; the old one keeps resolving and every `[[old]]` in other articles
    * is rewritten (each a new version).
    */
  def rename(slug: com.histmersion.domain.research.articles.ArticlesControllers.RenameSlug, req: LabelRename): Either[Api.Error, LabelRenamed] =
    maintenance.guard("rename_article"):
      val from = current(AliasKinds.ArticleRecords, norm(slug))
      for
        to <- LabelRenames.validate(com.histmersion.api.exchange.Bundle.Kind.Article, from, req.to)
        rewritten <- store.rename(from, to, Caller.Local)
      yield LabelRenamed(com.histmersion.api.exchange.Bundle.Kind.Article, from, to, rewritten)

  // ── the mirror, the versions, the slugs, a note imported ────────────────────────────────────

  /** The article as it would live on disk — `wiki/<kind>/<slug>.md`, with its generated coverage sections after the
    * body (between markers an import drops) and its filter and reasons in the frontmatter.
    */
  def exportNote(slug: com.histmersion.domain.research.articles.ArticlesControllers.ExportNoteSlug): Either[Api.Error, com.histmersion.domain.research.articles.ArticlesControllers.ExportNoteResultPart2] = note(slug)

  /** The note of the article an old slug or its own names, as the mirror writes it. */
  def articleNote(slug: com.histmersion.domain.research.articles.ArticlesControllers.ArticleNoteSlug): Either[Api.Error, com.histmersion.domain.research.articles.ArticlesControllers.ArticleNoteResultPart2] =
    store
      .load(current(AliasKinds.ArticleRecords, norm(slug)))
      .map(a => ArticleNote.render(a, coverageOf(a).map(Article.Coverage.exported)))
      .toRight(Articles.notFound(slug))

  /** The edit history of one article. 404 when the slug itself is unknown (so "no versions yet" and "no such article"
    * are different answers).
    */
  def versions(slug: com.histmersion.domain.research.articles.ArticlesControllers.VersionsSlug): Either[Api.Error, List[Article.Version]] =
    get(norm(slug)).map(v => store.versions(v.article.slug))

  /** One historical version's full body. 404 when the slug or the version id is unknown. */
  def version(slug: com.histmersion.domain.research.articles.ArticlesControllers.VersionSlug, versionId: com.histmersion.domain.research.articles.ArticlesControllers.VersionVersionId): Either[Api.Error, Article.VersionFull] =
    get(norm(slug)).flatMap(v =>
      store
        .version(v.article.slug, versionId)
        .toRight(Api.Error(s"No version $versionId for article '$slug'", Some(Write.CodeNotFound)))
    )

  def slugs: List[com.histmersion.domain.research.articles.ArticlesControllers.SlugsResult] = store.slugs

  /** Import ONE article note from raw text (the host-side path of `histmersion seed`). Applies the same "stored row is
    * newer than the file" guard as the source seed whenever the client sends the file's mtime, so a reseed cannot wipe
    * an agent's compiled prose.
    */
  def importNote(req: Article.Import): Either[Api.Error, SeedResult] =
    maintenance.guard("import_article")(importNow(req))

  private def importNow(req: Article.Import): Either[Api.Error, SeedResult] =
    val fallbackSlug = req.slug.map(_.trim).filter(_.nonEmpty).getOrElse("")
    val fallbackKind = req.kind.map(_.trim).filter(_.nonEmpty).getOrElse(Articles.Kinds.head)
    ArticleNote.parse(req.text, fallbackSlug, fallbackKind) match
      case None         => Left(Api.Error("no YAML frontmatter in the article note"))
      case Some(parsed) =>
        Slug
          .parse(parsed.slug)
          .map(_.value)
          .left
          .map(m => Api.Error(m, Some(Write.CodeInvalid)))
          .flatMap { s =>
            // as for a source note: a note filed under an OLD slug is a stale mirror file — it would
            // otherwise create a second article that shadows the alias
            Option(store.canonical(s))
              .filter(_ != s)
              .fold(Right(s))(now =>
                Left(
                  Api.Error(
                    s"'$s' was renamed to '$now' — import the note under '$now' instead",
                    Some(Write.CodeInvalid)
                  )
                )
              )
          }
          .flatMap { s =>
            if !req.force && req.mtime.exists(m => SeedImport.isStale(store.updatedAtMillis, s, m)) then
              Right(SeedResult(0, Nil, 0, List(s)))
            else
              Articles
                .kindOf(parsed.kind)
                .left
                .map(m => Api.Error(m, Some(Write.CodeInvalid)))
                .flatMap(k => strictGate(req.strict, formatting(parsed.body, parsed.summary)).map(_ => k))
                .map { k =>
                  val _ = store.put(parsed.modify(_.slug).setTo(s).modify(_.kind).setTo(k), Caller.Local)
                  SeedResult(1, Nil, 0, Nil)
                }
          }

object ArticlesControllers:
  type PairingResolveArg1 = PairingResolveArg1.T
  object PairingResolveArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PairingResolveResult = PairingResolveResult.T
  object PairingResolveResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type LedgersArg1 = LedgersArg1.T
  object LedgersArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LedgersResultPart1 = LedgersResultPart1.T
  object LedgersResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLeadsArg1 = ArticleLeadsArg1.T
  object ArticleLeadsArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleLeadsArg2 = ArticleLeadsArg2.T
  object ArticleLeadsArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteArg1 = NoteArg1.T
  object NoteArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NoteResultPart2 = NoteResultPart2.T
  object NoteResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentArg2 = CurrentArg2.T
  object CurrentArg2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CurrentResult = CurrentResult.T
  object CurrentResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  type NormResult = NormResult.T
  object NormResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NormSlug = NormSlug.T
  object NormSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormattingBody = FormattingBody.T
  object FormattingBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FormattingSummary = FormattingSummary.T
  object FormattingSummary extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StrictGateStrict = StrictGateStrict.T
  object StrictGateStrict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type LintSlug = LintSlug.T
  object LintSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FixSlug = FixSlug.T
  object FixSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteRepairedBodyResult = WriteRepairedBodyResult.T
  object WriteRepairedBodyResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteRepairedBodySlug = WriteRepairedBodySlug.T
  object WriteRepairedBodySlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteRepairedBodyBody = WriteRepairedBodyBody.T
  object WriteRepairedBodyBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type WriteRepairedBodyToken = WriteRepairedBodyToken.T
  object WriteRepairedBodyToken extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyCorpusTexts = BodyCorpusTexts.T
  object BodyCorpusTexts extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyCorpusGenerated = BodyCorpusGenerated.T
  object BodyCorpusGenerated extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CheckSlug = CheckSlug.T
  object CheckSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReviewSlug = ReviewSlug.T
  object ReviewSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReviewOfSlug = ReviewOfSlug.T
  object ReviewOfSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetSlug = GetSlug.T
  object GetSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type GetBrief = GetBrief.T
  object GetBrief extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CoverageSlug = CoverageSlug.T
  object CoverageSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedSourcesResult = CitedSourcesResult.T
  object CitedSourcesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedSourcesBody = CitedSourcesBody.T
  object CitedSourcesBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyLinksResultArg1 = BodyLinksResultArg1.T
  object BodyLinksResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type BodyLinksBody = BodyLinksBody.T
  object BodyLinksBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedOfResult = CitedOfResult.T
  object CitedOfResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedOfBody = CitedOfBody.T
  object CitedOfBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CitedOfLinksArg1 = CitedOfLinksArg1.T
  object CitedOfLinksArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CritiquesSlug = CritiquesSlug.T
  object CritiquesSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CritiquesBody = CritiquesBody.T
  object CritiquesBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PairingResultPart2Arg1 = PairingResultPart2Arg1.T
  object PairingResultPart2Arg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PairingResultPart2Result = PairingResultPart2Result.T
  object PairingResultPart2Result extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PairingBody = PairingBody.T
  object PairingBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RepairPairingResultPart1 = RepairPairingResultPart1.T
  object RepairPairingResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RepairPairingBody = RepairPairingBody.T
  object RepairPairingBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterReachResultPart1 = FilterReachResultPart1.T
  object FilterReachResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterReachResultPart2 = FilterReachResultPart2.T
  object FilterReachResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterReachResultPart3 = FilterReachResultPart3.T
  object FilterReachResultPart3 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterReachCitekeys = FilterReachCitekeys.T
  object FilterReachCitekeys extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterReachCap = FilterReachCap.T
  object FilterReachCap extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RecordedFilterSlug = RecordedFilterSlug.T
  object RecordedFilterSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterTotalResult = FilterTotalResult.T
  object FilterTotalResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type FilterTotalSlug = FilterTotalSlug.T
  object FilterTotalSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewFilterSourcesResult = NewFilterSourcesResult.T
  object NewFilterSourcesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type NewFilterSourcesSlug = NewFilterSourcesSlug.T
  object NewFilterSourcesSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenCritiquesResult = OpenCritiquesResult.T
  object OpenCritiquesResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Int, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type OpenCritiquesSlug = OpenCritiquesSlug.T
  object OpenCritiquesSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CritiquesOfBody = CritiquesOfBody.T
  object CritiquesOfBody extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type CritiquesOfLinksArg1 = CritiquesOfLinksArg1.T
  object CritiquesOfLinksArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageLinksResultArg1 = PageLinksResultArg1.T
  object PageLinksResultArg1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PageLinksTargets = PageLinksTargets.T
  object PageLinksTargets extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type MentionedInResult = MentionedInResult.T
  object MentionedInResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ResolvedId = ResolvedId.T
  object ResolvedId extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutSlug = PutSlug.T
  object PutSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PutStrict = PutStrict.T
  object PutStrict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchSlug = PatchSlug.T
  object PatchSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type PatchStrict = PatchStrict.T
  object PatchStrict extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type DeleteSlug = DeleteSlug.T
  object DeleteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type RenameSlug = RenameSlug.T
  object RenameSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportNoteResultPart2 = ExportNoteResultPart2.T
  object ExportNoteResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ExportNoteSlug = ExportNoteSlug.T
  object ExportNoteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleNoteResultPart2 = ArticleNoteResultPart2.T
  object ArticleNoteResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleNoteSlug = ArticleNoteSlug.T
  object ArticleNoteSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionsSlug = VersionsSlug.T
  object VersionsSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionSlug = VersionSlug.T
  object VersionSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type VersionVersionId = VersionVersionId.T
  object VersionVersionId extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Long, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SlugsResult = SlugsResult.T
  object SlugsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
