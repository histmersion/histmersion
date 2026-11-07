package com.histmersion.web

import hearth.kindlings.optics.syntax.*
import com.raquo.laminar.api.L.*
import com.histmersion.api.{Api, Plural, Wording}
import com.histmersion.web.ui.Layout
import org.scalajs.dom

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import com.histmersion.api.research.{ReviewBadge, ReviewState}
import com.histmersion.api.research.Article

object ArticlesTab:

  /** The tab ([[FilterTab]]): the GLOBAL filter (title included), as the Sources tab sends it — the server relates it
    * to articles (see [[Api.ArticleQuery]]), so the two lists cannot disagree about what is filtered. A page at a time;
    * "show more" fetches past the first.
    */

  private val tab = FilterTab[Api.ArticleQuery, Article.Summary](
    name = com.histmersion.web.FilterTab.Name("articles"),
    singular = Plural.Noun.Singular("article"),
    noun = Plural.Noun.Plural("articles"),
    query = () => UiState.articleQuery(),
    fetch = (q, offset) =>
      ApiClient
        .articles(q.modify(_.filter.offset).setTo(offset))
        .map(r =>
          TabPage(r.articles, r.total, kindCounts(r.kinds).map(TabPage.Extras(_)) :+ TabPage.Extras(ResultsPaging.span(UiState.from.now().map(com.histmersion.api.catalogue.Year(_)), UiState.to.now().map(com.histmersion.api.catalogue.Year(_))).value))
        ),
    render = renderArticle(_, inPanel = true),
    empty = com.histmersion.web.FilterTab.Empty("No articles relate to the current filter."),
    page = ResultsPaging.PageNumber(Layout.tabPage.value),
    debounceMs = () => UiState.reloadDelay(UiState.ReloadDelayNormalMs(Layout.tabReloadMs.value))
  )

  def pane: HtmlElement = tab.pane

  /** How many the list holds, once it has an answer (its tab's count). */
  val total: Signal[Option[com.histmersion.api.RowCount]] = tab.state.signal.map(s => Option.when(s.loaded.value)(com.histmersion.api.RowCount(math.max(s.total.value, s.rows.size))))

  /** An article's row: its link (in Explore's results a plain click shows its detail there; elsewhere it opens its
    * window) and "open in a new tab".
    */
  private[web] def renderArticle(art: Article.Summary, inPanel: Boolean = false): HtmlElement =
    li(
      cls := "article-row",
      marks.RowActions(marks.ItemRef.article(marks.ItemRef.ArticleSlug(art.slug.value), marks.ItemRef.ArticleTitle(art.title.value)), marks.ItemRef.Href(Route.Article(Route.Article.Slug(art.slug.value)).path), com.histmersion.web.library.TableRow.Byline(Fmt.display(com.histmersion.api.UiLabels.Text(art.kind.value)).value)),
      a(
        href := Route.Article(Route.Article.Slug(art.slug.value)).path,
        cls := "article-link",
        onClick --> { ev =>
          if Router.plain(ev) then
            ev.preventDefault()
            if inPanel then ResultsPanel.show(Preview.Target.Article(Preview.Target.Article.Slug(art.slug.value))) else ArticleModal.openSlug(art.slug)
        },
        div(cls := "article-title", art.title.value),
        div(
          cls := "article-meta",
          span(cls := "article-kind", Fmt.display(com.histmersion.api.UiLabels.Text(art.kind.value)).value),
          art.review.map(reviewBadge),
          Fmt.periodStr(art.periodStart, art.periodEnd).value match
            case ""  => emptyNode
            case per => span(cls := "article-period", per)
        ),
        art.summary
          .filter(_.value.nonEmpty)
          .map { s =>
            div(cls := "article-summary", Fmt.clip(stripMd(s.value), com.histmersion.api.Markdown.CharLimit(120)))
          }
          .getOrElse(emptyNode),
        if art.tags.nonEmpty then div(cls := "article-tags", art.tags.filter(_.value.nonEmpty).map(t => Chip.value(Chip.Choice(t.value))))
        else emptyNode
      )
    )

  /** The count line's per-kind counts: `200 concepts`, `3 indexes`, the largest first. */
  def kindCounts(kinds: List[Article.KindCount]): List[String] =
    Article.List
      .byKind(kinds)
      .map(k =>
        Plural.count(
          Plural.CountN(k.count.value.toLong),
          Plural.CountSingular(k.kind.value),
          Plural.CountPlural(if k.kind.value.endsWith("x") then k.kind.value + "es" else "")
        ).value
      )

  /** An article's review state as a coloured badge, what it means on hover. */
  def reviewBadge(b: ReviewBadge): HtmlElement =
    span(cls := reviewClass(b.state), title := b.detail.value, Wording.capitalize(b.label.value))

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
