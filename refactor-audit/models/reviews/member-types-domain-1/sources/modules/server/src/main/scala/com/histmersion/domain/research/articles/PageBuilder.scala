package com.histmersion.domain.research.articles

import com.histmersion.domain.Highlight
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import com.histmersion.server.{ArticleHtml, ArticlePage, Citation, FullText, Pages, SourcePage}
import com.histmersion.api.library.{Label, NeighborRow, NeighborTarget}
import com.histmersion.api.library.Source
import com.histmersion.api.research.Article

/** Assembles the typed page models from the library's and research's controllers — the one impure step between the
  * database and [[Pages]]. Each page resolves its links with ONE [[ArticlesControllers.pageLinks]] call.
  */
final class PageBuilder(
    sources: SourcesControllers,
    sourceStore: SourceService,
    citations: CitationsControllers,
    articles: ArticlesControllers
):

  /** `(title, inner HTML)` of `/source/{citekey}`, `?q=` terms highlighted. */
  def source(ck: com.histmersion.domain.research.articles.PageBuilder.SourceCk, full: com.histmersion.domain.research.articles.PageBuilder.SourceFull, q: Option[com.histmersion.domain.research.articles.PageBuilder.SourceQ]): Option[(com.histmersion.domain.research.articles.PageBuilder.SourceResultPart1, com.histmersion.domain.research.articles.PageBuilder.SourceResultPart2)] =
    sources.get(ck).toOption.map { sw =>
      val s = sw.source
      val ref = Source.Ref(s.citekey, s.doi, s.authors, s.year, s.title, s.status)
      val rows = citations.neighbors(ck)
      val refs = sourceStore
        .refs(rows.filter(_.target == NeighborTarget.Source).map(_.citekey))
        .map(r => r.citekey -> r)
        .toMap
      val linked = articles.store
        .refs(rows.filter(_.target == NeighborTarget.Article).map(_.citekey))
        .map(a => a.slug -> a)
        .toMap
      def entry(r: NeighborRow): Option[Citation] = r.target match
        case NeighborTarget.Source     => refs.get(r.citekey).map(Citation.ToSource(_, r.kind))
        case NeighborTarget.Article    => linked.get(r.citekey).map(a => Citation.ToArticle(a, Some(r.kind)))
        case NeighborTarget.Unresolved => Some(Citation.Unresolved(r.citekey, r.kind))
      val citingArticles = articles
        .sourceArticles(ck)
        .getOrElse(Nil)
        .map(b => Citation.ToArticle(Article.Ref(b.id, b.title.getOrElse(Label.display(b.id))), None))
      val term = q.map(_.trim).filter(_.nonEmpty)
      val page = SourcePage(
        ref = ref,
        venue = s.venue,
        periodStart = s.periodStart,
        periodEnd = s.periodEnd,
        topics = s.topics,
        regions = s.regions,
        tags = s.tags,
        summary = s.summary,
        notes = s.notes,
        findings = sw.findings,
        cites = rows.filter(_.direction == "out").flatMap(entry),
        citedBy = rows.filter(_.direction == "in").flatMap(entry) ++ citingArticles,
        fullText =
          if !full then FullText.Hidden
          else s.ocrMarkdown.filter(_.trim.nonEmpty).fold(FullText.Missing)(FullText.Shown(_)),
        query = term,
        formatting = sources.formatIssues(s.summary, s.notes)
      )
      val md = List(s.summary, s.notes, if full then s.ocrMarkdown else None).flatten.mkString("\n\n")
      val links = articles.pageLinks(Article.Links.targets(md))
      (s.title, highlighted(Pages.source(page, links), term))
    }

  /** `(title, inner HTML)` of `/article/{slug}`, `?q=` terms highlighted. */
  def article(slug: com.histmersion.domain.research.articles.PageBuilder.ArticleSlug, q: Option[com.histmersion.domain.research.articles.PageBuilder.ArticleQ]): Option[(com.histmersion.domain.research.articles.PageBuilder.ArticleResultPart1, com.histmersion.domain.research.articles.PageBuilder.ArticleResultPart2)] =
    articles.get(slug).toOption.map { v =>
      val generated = v.coverage.fold(List.empty[String])(c => Article.Links.targets(Article.Coverage.sections(c)))
      val links =
        articles.pageLinks(Article.Links.targets(v.article.body) ++ v.sourcesCited.map(_.citekey) ++ generated)
      val html = Pages.article(
        ArticlePage(
          v.article,
          v.backlinks,
          v.sourcesCited,
          v.formatting,
          v.coverage,
          articles.reviewOf(v.article.slug).toOption
        ),
        links
      )
      (v.article.title, highlighted(html, q.map(_.trim).filter(_.nonEmpty)))
    }

  private def highlighted(html: com.histmersion.domain.research.articles.PageBuilder.HighlightedHtml, term: Option[com.histmersion.domain.research.articles.PageBuilder.HighlightedTerm]): com.histmersion.domain.research.articles.PageBuilder.HighlightedResult = term match
    case Some(t) =>
      val (out, n) = Highlight(html, t)
      ArticleHtml.matchNav(n) + out
    case None => html

object PageBuilder:
  type SourceResultPart1 = SourceResultPart1.T
  object SourceResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceResultPart2 = SourceResultPart2.T
  object SourceResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceCk = SourceCk.T
  object SourceCk extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceFull = SourceFull.T
  object SourceFull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type SourceQ = SourceQ.T
  object SourceQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleResultPart1 = ArticleResultPart1.T
  object ArticleResultPart1 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleResultPart2 = ArticleResultPart2.T
  object ArticleResultPart2 extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleSlug = ArticleSlug.T
  object ArticleSlug extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ArticleQ = ArticleQ.T
  object ArticleQ extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HighlightedResult = HighlightedResult.T
  object HighlightedResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HighlightedHtml = HighlightedHtml.T
  object HighlightedHtml extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type HighlightedTerm = HighlightedTerm.T
  object HighlightedTerm extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
