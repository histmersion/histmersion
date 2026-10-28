package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.api.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ArticlesTab:

  /** The tab ([[FilterTab]]): the GLOBAL filter (title included), as the Sources tab sends it — the
    * server relates it to articles (see [[Api.ArticleQuery]]), so the two lists cannot disagree about
    * what is filtered. A page at a time; "show more" fetches past the first. */
  private val tab = FilterTab[Api.ArticleQuery, ArticleSummary](
    name = "articles", singular = "article", noun = "articles",
    query = () => UiState.articleQuery(),
    fetch = (q, offset) => ApiClient.articles(q.copy(filter = q.filter.copy(offset = offset)))
      .map(r => TabPage(r.articles, r.total, kindCounts(r.kinds) :+ ResultsPaging.span(UiState.from.now(), UiState.to.now()))),
    render = renderArticle,
    empty = "No articles relate to the current filter.",
    page = Layout.tabPage,
    debounceMs = () => UiState.reloadDelay(Layout.tabReloadMs))

  def pane: HtmlElement = tab.pane

  private[web] def renderArticle(art: ArticleSummary): HtmlElement =
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
          art.review.map(reviewBadge),
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

  /** The count line's per-kind counts: `200 concepts`, `3 indexes`, the largest first. */
  def kindCounts(kinds: List[ArticleKindCount]): List[String] =
    ArticleList.byKind(kinds).map(k => Plural.count(k.count.toLong, k.kind, if k.kind.endsWith("x") then k.kind + "es" else ""))

  /** An article's review state as a coloured badge, what it means on hover. */
  def reviewBadge(b: ReviewBadge): HtmlElement =
    span(cls := reviewClass(b.state), title := b.detail, Wording.capitalize(b.label))

  /** The badge's classes: the stylesheet colours each state (as the article page's badge). */
  def reviewClass(state: ReviewState): String = s"job-state review-state state-${state.wire}"

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
