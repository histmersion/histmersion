package com.histmersion.server

import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.json.circe.*
import sttp.shared.Identity
import sttp.model.{Header, MediaType}
import com.histmersion.api.*
import com.histmersion.config.*
import com.histmersion.domain.*
// EXPLICIT, and deliberately so: `com.histmersion.server.Article` (the page helpers in HttpApi.scala) and
// `com.histmersion.api.Article` (the article model) share a name, and a wildcard import outranks a package
// member declared in another file. This import pins `Article` to the page helpers; the model is
// never named here — the handlers hand back `ArticleView`.
import com.histmersion.server.Article


/** ARTICLES over HTTP (plan §5.2 stream 2.7): the REST routes of the compiled wiki plus the rendered
  * page at `/article/{slug}`.
  *
  * The page is the twin of `/source/{citekey}` — same stylesheet, same modal fragment protocol
  * (`?fragment=true`), same `?q=` term highlighting — but its wikilinks resolve BOTH ways:
  * `[[citekey]]` → `/source/citekey` for the sources the article cites, `[[slug]]` → `/article/slug`
  * for everything else, and neither when nothing in the corpus answers to the name.
  *
  * Resolution is [[Handlers.pageLinks]], NOT the article's own `sourcesCited` list: the wiki
  * writes citekey ALIASES (`[[allentoft2015]]` for `allentoft2015_population`), which never appear
  * in `article_links` verbatim and used to fall through to `/article/allentoft2015` — a 404 on every
  * citation in the corpus.
  */
final class ArticlesApi(h: Handlers):

  private val rest: List[ServerEndpoint[Any, Identity]] = List(
    Api.articles.serverLogicSuccess[Identity](q => h.listArticles(q)),
    Api.articleSlugs.serverLogicSuccess[Identity](_ => h.articleSlugs),
    Api.importArticle.serverLogic[Identity](req => h.importArticle(req)),
    // `…/export`, `…/versions` and `…/import` are prefixes of `…/{slug}`: they must be interpreted first.
    Api.exportArticle.serverLogic[Identity](slug => h.exportArticle(slug)),
    Api.articleVersions.serverLogic[Identity](slug => h.articleVersions(slug)),
    Api.articleVersion.serverLogic[Identity]((slug, id) => h.articleVersion(slug, id)),
    Api.lintArticle.serverLogic[Identity](slug => h.lintArticle(slug)),
    Api.fixArticleFormat.serverLogic[Identity](slug => h.fixArticle(slug)),
    Api.checkArticle.serverLogic[Identity]((slug, req) => h.checkArticle(slug, req)),
    Api.articleCoverage.serverLogic[Identity](slug => h.articleCoverage(slug)),
    Api.getArticle.serverLogic[Identity]((slug, brief) => h.getArticle(slug, brief)),
    Api.putArticle.serverLogic[Identity]((slug, in, strict) => h.putArticle(slug, in, strict)),
    Api.patchArticle.serverLogic[Identity]((slug, p, strict) => h.patchArticle(slug, p, strict)),
    Api.deleteArticle.serverLogic[Identity](slug => h.deleteArticle(slug))
  )

  private val pages = PageBuilder(h)

  /** `/article/{slug}` — 404 for an unknown slug, body unchanged (item 21). Twin of the source page. */
  private val page: ServerEndpoint[Any, Identity] =
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
            (sttp.model.StatusCode.Ok, if fragment then inner else Article.page(title, inner, PageKind.Article))
          case None =>
            val body = s"<p>No article <code>${Article.esc(slug)}</code>.</p>"
            (sttp.model.StatusCode.NotFound,
              if fragment then body else Article.page("Not found", body, PageKind.Article))
      }

  /** `/articles` — the address people guess for "the wiki"; it is the `index` article (the compiled
    * map of content), so redirect rather than keep a second listing page in sync. */
  private val listRedirect: ServerEndpoint[Any, Identity] =
    endpoint.get
      .in("articles")
      .out(statusCode(sttp.model.StatusCode.Found))
      .out(header[String]("Location"))
      .serverLogicSuccess[Identity](_ => "/article/index")

  val endpoints: List[ServerEndpoint[Any, Identity]] = rest :+ page :+ listRedirect
