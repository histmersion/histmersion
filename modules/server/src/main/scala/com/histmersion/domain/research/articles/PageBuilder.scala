package com.histmersion.domain.research.articles

import com.histmersion.domain.Highlight
import com.histmersion.domain.library.citations.CitationsControllers
import com.histmersion.domain.library.sources.{SourcesControllers, SourceService}
import com.histmersion.server.{ArticleHtml, ArticlePage, Citation, FullText, Pages, SourcePage}
import com.histmersion.api.library.{Label, NeighborRow, NeighborTarget, SourceRef}
import com.histmersion.api.research.{ArticleCoverage, ArticleLinks, ArticleRef}

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
  def source(ck: String, full: Boolean, q: Option[String]): Option[(String, String)] =
    sources.get(ck).toOption.map { sw =>
      val s = sw.source
      val ref = SourceRef(s.citekey, s.doi, s.authors, s.year, s.title, s.status)
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
        .map(b => Citation.ToArticle(ArticleRef(b.id, b.title.getOrElse(Label.display(b.id))), None))
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
      val links = articles.pageLinks(ArticleLinks.targets(md))
      (s.title, highlighted(Pages.source(page, links), term))
    }

  /** `(title, inner HTML)` of `/article/{slug}`, `?q=` terms highlighted. */
  def article(slug: String, q: Option[String]): Option[(String, String)] =
    articles.get(slug).toOption.map { v =>
      val generated = v.coverage.fold(List.empty[String])(c => ArticleLinks.targets(ArticleCoverage.sections(c)))
      val links = articles.pageLinks(ArticleLinks.targets(v.article.body) ++ v.sourcesCited.map(_.citekey) ++ generated)
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

  private def highlighted(html: String, term: Option[String]): String = term match
    case Some(t) =>
      val (out, n) = Highlight(html, t)
      ArticleHtml.matchNav(n) + out
    case None => html
