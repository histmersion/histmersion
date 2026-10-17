package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ArticlesTab:

  /** The tab ([[FilterTab]]): the GLOBAL filter (title included), as the Sources tab sends it — the
    * server relates it to articles (see [[Api.ArticleQuery]]), so the two lists cannot disagree about
    * what is filtered. A page at a time; "show more" fetches past the first. */
  private val tab = FilterTab[Api.ArticleQuery, ArticleSummary](
    name = "articles", noun = "articles",
    query = () => UiState.articleQuery(),
    fetch = (q, offset) => ApiClient.articles(q.copy(filter = q.filter.copy(offset = offset))).map(r => (r.articles, r.total)),
    render = renderArticle,
    empty = "No articles relate to the current filter.",
    page = Layout.tabPage,
    debounceMs = () => UiState.reloadDelay(Layout.tabReloadMs))

  def pane: HtmlElement = tab.pane

  private def renderArticle(art: ArticleSummary): HtmlElement =
    li(
      cls := "article-row",
      a(
        href := "#",
        cls := "article-link",
        onClick.preventDefault --> { _ =>
          ArticleModal.openSlug(art.slug)
        },
        div(cls := "article-title", art.title),
        div(
          cls := "article-meta",
          span(cls := "article-kind", Fmt.display(art.kind)),
          Fmt.periodStr(art.periodStart, art.periodEnd) match
            case "" => emptyNode
            case per => span(cls := "article-period", per)
        ),
        art.summary.filter(_.nonEmpty).map { s =>
          div(cls := "article-summary", Fmt.clip(stripMd(s), 120))
        }.getOrElse(emptyNode),
        if art.tags.nonEmpty then
          div(cls := "article-tags",
            art.tags.filter(_.nonEmpty).map(t =>
              Chip.value(t)
            )
          )
        else emptyNode
      )
    )

  private def stripMd(s: String): String =
    s.replaceAll("\\[\\[([^\\]]+)\\]\\]", "$1")
      .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
      .replaceAll("\\*([^*]+)\\*", "$1")
      .replaceAll("#+ ", "")
      .replaceAll("\\s+", " ")
      .trim

  def show(tabName: String): Unit =
    if tabName == "articles" then
      UiState.set(UiState.tab := Some("articles"))()
      load()

  def restore(): Unit =
    UiState.tab.now() match
      case Some("articles") => load()
      case _                => ()

  /** Called on every UiState change: refetch only when the query this tab sends has changed. */
  def refresh(): Unit = tab.refresh()

  def load(): Unit = tab.load()
