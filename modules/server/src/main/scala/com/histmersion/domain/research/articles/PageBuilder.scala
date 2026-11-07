package com.histmersion.domain.research.articles

import com.histmersion.domain.Highlight
import com.histmersion.domain.research.Slug
import com.histmersion.domain.library.Citekey
import com.histmersion.api.text.WikiLinks
import com.histmersion.server.HtmlRender
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
  def source(ck: com.histmersion.domain.library.Citekey, full: com.histmersion.domain.research.articles.PageBuilder.SourceFull, q: Option[com.histmersion.domain.library.search.Search.Query]): Option[(com.histmersion.domain.Title, HtmlRender.Html)] =
    sources.get(ck).toOption.map { sw =>
      val s = sw.source
      val ref = Source.Ref(s.citekey, s.doi, s.authors, s.year, s.title, s.status)
      val rows = citations.neighbors(ck)
      val refs = sourceStore
        .refs(rows.filter(_.target == NeighborTarget.Source).flatMap(r => Citekey.option(r.citekey.value)))
        .map(r => r.citekey.value -> r)
        .toMap
      val linked = articles.store
        .refs(rows.filter(_.target == NeighborTarget.Article).flatMap(r => Slug.option(r.citekey.value)))
        .map(a => a.slug.value -> a)
        .toMap
      def entry(r: NeighborRow): Option[Citation] = r.target match
        case NeighborTarget.Source     =>
          refs.get(r.citekey.value).map(Citation.ToSource(_, com.histmersion.domain.library.Edge.Kind.Wire(r.kind.value)))
        case NeighborTarget.Article    => linked.get(r.citekey.value).map(a => Citation.ToArticle(a, Some(r.kind)))
        case NeighborTarget.Unresolved =>
          Some(Citation.Unresolved(Citation.Unresolved.Id(r.citekey.value), com.histmersion.domain.library.Edge.Kind.Wire(r.kind.value)))
      val citingArticles = articles
        .sourceArticles(ck)
        .getOrElse(Nil)
        .map(b =>
          Citation.ToArticle(
            Article.Ref(
              Article.Slug(b.id.value),
              Article.Title(b.title.fold(Label.display(Label.Text(b.id.value)).value)(_.value))
            ),
            None
          )
        )
      val term = q.map(_.value.trim).filter(_.nonEmpty)
      val page = SourcePage(
        ref = ref,
        venue = s.venue.map(v => SourcePage.Venue(v.value)),
        periodStart = s.periodStart.map(y => SourcePage.PeriodStart(y.value)),
        periodEnd = s.periodEnd.map(y => SourcePage.PeriodEnd(y.value)),
        topics = s.topics.map(t => SourcePage.Topics(t.value)),
        regions = s.regions.map(t => SourcePage.Regions(t.value)),
        tags = s.tags.map(t => SourcePage.Tags(t.value)),
        summary = s.summary.map(t => SourcePage.Summary(t.value)),
        notes = s.notes.map(t => SourcePage.Notes(t.value)),
        findings = sw.findings,
        cites = rows.filter(_.direction.value == "out").flatMap(entry),
        citedBy = rows.filter(_.direction.value == "in").flatMap(entry) ++ citingArticles,
        fullText =
          if !full.value then FullText.Hidden
          else
            s.ocrMarkdown
              .filter(_.value.trim.nonEmpty)
              .fold(FullText.Missing)(m => FullText.Shown(FullText.Shown.Markdown(m.value))),
        query = term.map(SourcePage.Query(_)),
        formatting = sources.formatIssues(s.summary, s.notes)
      )
      val md =
        List(s.summary.map(_.value), s.notes.map(_.value), if full.value then s.ocrMarkdown.map(_.value) else None).flatten
          .mkString("\n\n")
      val links = articles.pageLinks(Article.Links.targets(WikiLinks.Body(md)))
      (com.histmersion.domain.Title(s.title.value), highlighted(Pages.source(page, links), term))
    }

  /** `(title, inner HTML)` of `/article/{slug}`, `?q=` terms highlighted. */
  def article(slug: com.histmersion.domain.research.Slug, q: Option[com.histmersion.domain.library.search.Search.Query]): Option[(com.histmersion.domain.Title, HtmlRender.Html)] =
    articles.get(slug).toOption.map { v =>
      val generated = v.coverage.fold(List.empty[WikiLinks.Occurrence.Target])(c =>
        Article.Links.targets(WikiLinks.Body(Article.Coverage.sections(c).value))
      )
      val links =
        articles.pageLinks(
          Article.Links.targets(WikiLinks.Body(v.article.body.value)) ++
            v.sourcesCited.map(c => WikiLinks.Occurrence.Target(c.citekey.value)) ++ generated
        )
      val html = Pages.article(
        ArticlePage(
          v.article,
          v.backlinks,
          v.sourcesCited,
          v.formatting,
          v.coverage,
          articles.reviewOf(Slug.assume(v.article.slug.value)).toOption
        ),
        links
      )
      (com.histmersion.domain.Title(v.article.title.value), highlighted(html, q.map(_.value.trim).filter(_.nonEmpty)))
    }

  private def highlighted(html: HtmlRender.Html, term: Option[String]): HtmlRender.Html = term match
    case Some(t) =>
      val (out, n) = Highlight(Highlight.Html(html.value), Highlight.Term(t))
      HtmlRender.Html(ArticleHtml.matchNav(ArticleHtml.MatchNavMatches(n.value)).value + out.value)
    case None => html

object PageBuilder:
  type SourceFull = SourceFull.T
  object SourceFull extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
