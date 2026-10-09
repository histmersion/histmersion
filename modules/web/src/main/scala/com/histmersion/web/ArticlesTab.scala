package com.histmersion.web

import com.raquo.laminar.api.L.*
import com.histmersion.core.*
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ArticlesTab:

  private val articlesVar = Var[List[ArticleSummary]](Nil)
  private val totalVar = Var(0)
  private val shownVar = Var(0)
  private val loading = Var(false)

  def pane: HtmlElement =
    div(
      cls := "articles-pane",
      child <-- loading.signal.map {
        case true => div(cls := "articles-loading", "Loading…")
        case false => emptyNode
      },
      ul(
        cls := "articles-list",
        children <-- articlesVar.signal.combineWith(shownVar.signal).map { (arts, n) =>
          arts.take(n).map(renderArticle)
        }
      ),
      child <-- articlesVar.signal.combineWith(shownVar.signal, totalVar.signal).map { (arts, n, t) =>
        val remaining = math.min(arts.length, t) - n
        if remaining > 0 then
          button(
            cls := "btn btn--bare btn--block show-more",
            s"show $remaining more",
            onClick --> { _ => shownVar.update(_ + 25) }
          )
        else emptyNode
      }
    )

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

  private var reqId: Int = 0
  private var lastQuery: Option[Api.ArticleQuery] = None
  private var reloadTimer: Int = 0

  /** The query this tab sends: the GLOBAL filter (title included), as the Sources tab sends it — the
    * server relates it to articles (see [[Api.ArticleQuery]]). A pure function of the UiState, so the
    * two lists cannot disagree with the Sources tab about what is filtered. */
  private def query(): Api.ArticleQuery = UiState.articleQuery()

  /** Called on every UiState change: refetch only when the query this tab sends has changed. */
  def refresh(): Unit =
    val q = query()
    if !lastQuery.contains(q) then
      lastQuery = Some(q)
      dom.window.clearTimeout(reloadTimer)
      reloadTimer = dom.window.setTimeout(() => load(), UiState.reloadDelay(Layout.tabReloadMs))

  def load(): Unit =
    loading.set(true)
    reqId += 1
    val mine = reqId
    val q = query()
    lastQuery = Some(q)
    ApiClient.articles(q).foreach { result =>
      if mine == reqId then
        articlesVar.set(result.articles)
        totalVar.set(result.total)
        shownVar.set(math.min(25, result.articles.length))
        loading.set(false)
    }


