package com.histmersion
package domain.research.articles

import com.histmersion.util.problem.Problem
import io.scalaland.chimney.dsl.*
import com.histmersion.api.{Api, Write}
import com.histmersion.api.research.{Article, Articles, ReviewGoal}
import com.histmersion.domain.research.Slug
import com.histmersion.api.Html.html
import com.histmersion.server.{ArticleHtml, PageKind}
import sttp.model.{Header, MediaType}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import com.histmersion.domain.library.search.Search
import com.histmersion.domain.Title
import com.histmersion.domain.Creator
import com.histmersion.domain.CreatorControllers

/** The articles over HTTP: the REST routes of the compiled wiki, the rendered page at `/article/{slug}` and the
  * `/articles` redirect, in the order route matching needs (`…/export`, `…/versions` and `…/import` before `…/{slug}`).
  *
  * The page is the twin of `/source/{citekey}` — same stylesheet, same modal fragment protocol (`?fragment=true`), same
  * `?q=` term highlighting — but its wikilinks resolve BOTH ways: `[[citekey]]` → `/source/citekey` for the sources the
  * article cites, `[[slug]]` → `/article/slug` for everything else, and neither when nothing in the corpus answers to
  * the name. Resolution is [[ArticlesControllers.pageLinks]], not the article's own `sourcesCited` list: the wiki
  * writes citekey ALIASES (`[[allentoft2015]]` for `allentoft2015_population`), which never appear in `article_links`
  * verbatim.
  */
@tolerated(LintRule.Direction, "pending G12 (a domain names only lower places)")
object ArticleRoutes:
  /** A slug that names an article to read: one that is no slug names no article. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def read(slug: ReviewGoal.Slug): Either[Api.Error, Slug] =
    Slug.parse(slug.value).left.map(_ => Articles.notFound(Article.Slug(slug.value)))

  /** A slug a write names: one that is no slug is refused. */
  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  @tolerated(LintRule.G44PlainDef, "pending G1 (the parameter takes the newtype, not its raw value)")
  private def write(slug: ReviewGoal.Slug): Either[Api.Error, Slug] =
    Slug.parse(slug.value).left.map(p => Api.Error(p, Some(Api.Error.Code(Write.CodeInvalid))))

  @tolerated(LintRule.G3Misplaced, "pending G3 (a function about a type lives on the type)")
  @tolerated(LintRule.G44Conversion, "pending G44 (a controller hands the wire value to the domain's parse, not .value)")
  def apply(
      articles: ArticlesControllers,
      pages: PageBuilder,
      shell: ArticleHtml.Shell
  ): List[ServerEndpoint[Any, Identity]] =
    given Creator.New = Creator.New.Unattributed(Creator.Surface.Rest)
    val rest: List[ServerEndpoint[Any, Identity]] = List(
      Api.articles.serverLogicSuccess[Identity](q => articles.list(q)),
      Api.articleSlugs.serverLogicSuccess[Identity](_ => articles.slugs.map(s => ReviewGoal.Slug(s.value))),
      Api.importArticle.serverLogic[Identity](req => CreatorControllers.rest(req)(articles.importNote(req))),
      Api.exportArticle.serverLogic[Identity](slug =>
        read(slug).flatMap(articles.exportNote).map(n => Article.Export(n.value))
      ),
      Api.articleVersions.serverLogic[Identity](slug => read(slug).flatMap(articles.versions)),
      Api.articleVersion.serverLogic[Identity]((slug, id) =>
        read(slug).flatMap(s => articles.version(s, ArticleRepo.VersionId(id.value)))
      ),
      Api.lintArticle.serverLogic[Identity](slug => read(slug).flatMap(articles.lint)),
      Api.fixArticleFormat.serverLogic[Identity](slug => read(slug).flatMap(articles.fix)),
      Api.checkArticle.serverLogic[Identity]((slug, req) => write(slug).flatMap(articles.check(_, req))),
      Api.articleCoverage.serverLogic[Identity](slug => read(slug).flatMap(articles.coverage)),
      Api.articleReview.serverLogic[Identity](slug => read(slug).flatMap(articles.review)),
      Api.getArticle.serverLogic[Identity]((slug, brief) =>
        read(slug).flatMap(articles.get(_, ArticlesControllers.GetBrief(brief.value)))
      ),
      Api.putArticle.serverLogic[Identity]((slug, in, strict) =>
        CreatorControllers.rest(in) {
          write(slug).flatMap(articles.put(_, in, domain.research.Article.StrictMode(strict.value)))
        }
      ),
      Api.patchArticle.serverLogic[Identity]((slug, p, strict) =>
        CreatorControllers.rest(p) {
          write(slug).flatMap(articles.patch(_, p, domain.research.Article.StrictMode(strict.value)))
        }
      ),
      Api.deleteArticle.serverLogic[Identity](slug => read(slug).flatMap(articles.delete))
    )

    /** `/article/{slug}` — 404 for an unknown slug. */
    val page: ServerEndpoint[Any, Identity] =
      endpoint.get
        .in("article" / path[String]("slug"))
        .in(query[Boolean]("fragment").default(false)) // fragment = inner HTML only (for the modal)
        .in(query[Option[String]]("q"))
        .out(statusCode)
        .out(stringBody)
        .out(header(Header.contentType(MediaType.TextHtml)))
        .serverLogicSuccess[Identity] { case (slug, fragment, q) =>
          Slug.parse(slug).toOption.flatMap(s => pages.article(s, q.map(Search.Query(_)))) match
            case Some((title, inner)) =>
              (
                sttp.model.StatusCode.Ok,
                (if fragment then inner else shell(title, inner, PageKind.Article)).markup
              )
            case None =>
              val body = html"<p>No article <code>$slug</code>.</p>"
              (
                sttp.model.StatusCode.NotFound,
                (if fragment then body else shell(Title("Not found"), body, PageKind.Article)).markup
              )
        }

    /** `/articles` — the address people guess for "the wiki"; it is the `index` article (the compiled map of content),
      * so redirect rather than keep a second listing page in sync.
      */
    val listRedirect: ServerEndpoint[Any, Identity] =
      endpoint.get
        .in("articles")
        .out(statusCode(sttp.model.StatusCode.Found))
        .out(header[String]("Location"))
        .serverLogicSuccess[Identity](_ => "/article/index")

    rest :+ page :+ listRedirect
