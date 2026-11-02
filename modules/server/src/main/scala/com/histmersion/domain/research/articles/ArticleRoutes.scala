package com.histmersion.domain.research.articles

import com.histmersion.api.Api
import com.histmersion.server.{ArticleHtml, PageKind}
import sttp.model.{Header, MediaType}
import sttp.shared.Identity
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

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
object ArticleRoutes:
  def apply(articles: ArticlesControllers, pages: PageBuilder): List[ServerEndpoint[Any, Identity]] =
    val rest: List[ServerEndpoint[Any, Identity]] = List(
      Api.articles.serverLogicSuccess[Identity](q => articles.list(q)),
      Api.articleSlugs.serverLogicSuccess[Identity](_ => articles.slugs),
      Api.importArticle.serverLogic[Identity](req => articles.importNote(req)),
      Api.exportArticle.serverLogic[Identity](slug => articles.exportNote(slug)),
      Api.articleVersions.serverLogic[Identity](slug => articles.versions(slug)),
      Api.articleVersion.serverLogic[Identity]((slug, id) => articles.version(slug, id)),
      Api.lintArticle.serverLogic[Identity](slug => articles.lint(slug)),
      Api.fixArticleFormat.serverLogic[Identity](slug => articles.fix(slug)),
      Api.checkArticle.serverLogic[Identity]((slug, req) => articles.check(slug, req)),
      Api.articleCoverage.serverLogic[Identity](slug => articles.coverage(slug)),
      Api.articleReview.serverLogic[Identity](slug => articles.review(slug)),
      Api.getArticle.serverLogic[Identity]((slug, brief) => articles.get(slug, brief)),
      Api.putArticle.serverLogic[Identity]((slug, in, strict) => articles.put(slug, in, strict)),
      Api.patchArticle.serverLogic[Identity]((slug, p, strict) => articles.patch(slug, p, strict)),
      Api.deleteArticle.serverLogic[Identity](slug => articles.delete(slug))
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
          pages.article(slug, q) match
            case Some((title, inner)) =>
              (sttp.model.StatusCode.Ok, if fragment then inner else ArticleHtml.page(title, inner, PageKind.Article))
            case None =>
              val body = s"<p>No article <code>${ArticleHtml.esc(slug)}</code>.</p>"
              (
                sttp.model.StatusCode.NotFound,
                if fragment then body else ArticleHtml.page("Not found", body, PageKind.Article)
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
