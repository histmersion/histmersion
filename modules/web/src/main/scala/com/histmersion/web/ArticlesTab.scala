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
  type RenderArticleInPanel = RenderArticleInPanel.T
  object RenderArticleInPanel extends _root_.io.github.iltotore.iron.RefinedType[_root_.scala.Boolean, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type KindCountsResult = KindCountsResult.T
  object KindCountsResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ReviewClassResult = ReviewClassResult.T
  object ReviewClassResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripMdResult = StripMdResult.T
  object StripMdResult extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type StripMdS = StripMdS.T
  object StripMdS extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]
  type ShowTabName = ShowTabName.T
  object ShowTabName extends _root_.io.github.iltotore.iron.RefinedType[_root_.java.lang.String, _root_.io.github.iltotore.iron.constraint.any.Pure]

  private val tab = FilterTab[Api.ArticleQuery, Article.Summary](
    name = "articles",
    singular = "article",
    noun = "articles",
    query = () => UiState.articleQuery(),
    fetch = (q, offset) =>
      ApiClient
        .articles(q.modify(_.filter.offset).setTo(offset))
        .map(r =>
          TabPage(r.articles, r.total, kindCounts(r.kinds) :+ ResultsPaging.span(UiState.from.now(), UiState.to.now()))
        ),
    render = renderArticle(_, inPanel = true),
    empty = "No articles relate to the current filter.",
    page = Layout.tabPage,
    debounceMs = () => UiState.reloadDelay(Layout.tabReloadMs)
  )

  def pane: HtmlElement = tab.pane

  /** How many the list holds, once it has an answer (its tab's count). */
  val total: Signal[Option[com.histmersion.api.RowCount]] = tab.state.signal.map(s => Option.when(s.loaded)(com.histmersion.api.RowCount(math.max(s.total.value, s.rows.size))))

  /** An article's row: its link (in Explore's results a plain click shows its detail there; elsewhere it opens its
    * window) and "open in a new tab".
    */
  private[web] def renderArticle(art: Article.Summary, inPanel: com.histmersion.web.ArticlesTab.RenderArticleInPanel = false): HtmlElement =
    li(
      cls := "article-row",
      marks.RowActions(marks.ItemRef.article(art.slug, art.title), Route.Article(art.slug).path, Fmt.display(art.kind)),
      a(
        href := Route.Article(art.slug).path,
        cls := "article-link",
        onClick --> { ev =>
          if Router.plain(ev) then
            ev.preventDefault()
            if inPanel then ResultsPanel.show(Preview.Target.Article(art.slug)) else ArticleModal.openSlug(art.slug)
        },
        div(cls := "article-title", art.title),
        div(
          cls := "article-meta",
          span(cls := "article-kind", Fmt.display(art.kind)),
          art.review.map(reviewBadge),
          Fmt.periodStr(art.periodStart, art.periodEnd) match
            case ""  => emptyNode
            case per => span(cls := "article-period", per)
        ),
        art.summary
          .filter(_.nonEmpty)
          .map { s =>
            div(cls := "article-summary", Fmt.clip(stripMd(s), 120))
          }
          .getOrElse(emptyNode),
        if art.tags.nonEmpty then div(cls := "article-tags", art.tags.filter(_.nonEmpty).map(t => Chip.value(t)))
        else emptyNode
      )
    )

  /** The count line's per-kind counts: `200 concepts`, `3 indexes`, the largest first. */
  def kindCounts(kinds: List[Article.KindCount]): List[com.histmersion.web.ArticlesTab.KindCountsResult] =
    Article.List
      .byKind(kinds)
      .map(k => Plural.count(k.count.toLong, k.kind, if k.kind.endsWith("x") then k.kind + "es" else ""))

  /** An article's review state as a coloured badge, what it means on hover. */
  def reviewBadge(b: ReviewBadge): HtmlElement =
    span(cls := reviewClass(b.state), title := b.detail, Wording.capitalize(b.label))

  /** The badge's classes: the stylesheet colours each state (as the article page's badge). */
  def reviewClass(state: ReviewState): com.histmersion.web.ArticlesTab.ReviewClassResult = s"job-state review-state state-${state.wire}"

  private def stripMd(s: com.histmersion.web.ArticlesTab.StripMdS): com.histmersion.web.ArticlesTab.StripMdResult =
    s.replaceAll("\\[\\[([^\\]]+)\\]\\]", "$1")
      .replaceAll("\\*\\*([^*]+)\\*\\*", "$1")
      .replaceAll("\\*([^*]+)\\*", "$1")
      .replaceAll("#+ ", "")
      .replaceAll("\\s+", " ")
      .trim

  def show(tabName: com.histmersion.web.ArticlesTab.ShowTabName): Unit =
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
